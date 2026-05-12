#!/usr/bin/env bash
# Builds a tar.zst template of an EL datadir from a "frozen" node so freshly
# provisioned children can restore from it instead of syncing from scratch.
#
# Triggered by produce-el-template@<network>-<el>.timer.
# Required env (typically set via /etc/platform/templates/<network>-<el>.env):
#   NETWORK              hoodi | sepolia
#   EL_CLIENT            besu | geth
#   FROZEN_NODE_DIR      eth-docker workdir of the frozen node (contains .env + compose files)
#   EL_DATA_DIR          host path of the frozen node's bind-mounted EL datadir
# Optional:
#   TEMPLATES_DIR        target directory for the tarball (default /var/lib/platform/templates)
#   COMPRESS_LEVEL       zstd compression level (default 3)

set -euo pipefail

require_env() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "ERROR: required env var $name is not set" >&2
        exit 2
    fi
}

require_env NETWORK
require_env EL_CLIENT
require_env FROZEN_NODE_DIR
require_env EL_DATA_DIR
TEMPLATES_DIR="${TEMPLATES_DIR:-/var/lib/platform/templates}"
COMPRESS_LEVEL="${COMPRESS_LEVEL:-3}"

if [[ ! -d "$FROZEN_NODE_DIR" ]]; then
    echo "ERROR: FROZEN_NODE_DIR $FROZEN_NODE_DIR is not a directory" >&2
    exit 2
fi
if [[ ! -d "$EL_DATA_DIR" ]]; then
    echo "ERROR: EL_DATA_DIR $EL_DATA_DIR is not a directory" >&2
    exit 2
fi
if [[ -z "$(ls -A "$EL_DATA_DIR" 2>/dev/null)" ]]; then
    echo "ERROR: EL_DATA_DIR $EL_DATA_DIR is empty — frozen node has nothing synced yet" >&2
    exit 2
fi

mkdir -p "$TEMPLATES_DIR"

target="$TEMPLATES_DIR/${NETWORK}-${EL_CLIENT}.tar.zst"
tmp="${target}.tmp.$$"

trap 'rm -f "$tmp"' EXIT

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*"; }

cleanup_restart() {
    log "restarting execution container in $FROZEN_NODE_DIR"
    (cd "$FROZEN_NODE_DIR" && docker compose start execution) || {
        log "WARN: failed to restart execution container — manual intervention needed"
    }
}

# Stop only the EL container; the CL keeps running. This is the minimum
# downtime that lets us snapshot a quiescent chaindata directory.
log "stopping execution container in $FROZEN_NODE_DIR"
(cd "$FROZEN_NODE_DIR" && docker compose stop execution)

# From here on, we want to ALWAYS restart the EL — even on failure — so the
# frozen node resumes syncing. The atomic rename below only fires when the
# tar succeeds, so a partial template never replaces the previous one.
trap 'rm -f "$tmp"; cleanup_restart' EXIT

log "building $target via tar+zstd (level $COMPRESS_LEVEL)"
ZSTD_CLEVEL="$COMPRESS_LEVEL" \
    tar --use-compress-program=zstd -cf "$tmp" -C "$EL_DATA_DIR" .

# Atomic on same filesystem: a concurrent provisioning either reads the old
# template or the new one, never a truncated file.
mv -f "$tmp" "$target"
log "template ready: $target ($(du -h "$target" | cut -f1))"

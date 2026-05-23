#!/usr/bin/env bash
# Builds a tar.zst template of an EL datadir from a "frozen" node so freshly
# provisioned children can restore from it instead of syncing from scratch.
#
# Triggered by produce-el-template@<network>-<el>.timer.
# Required env (typically set via /etc/platform/templates/<network>-<el>.env):
#   NETWORK              hoodi | sepolia
#   EL_CLIENT            geth
#   FROZEN_NODE_DIR      eth-docker workdir of the frozen node (contains .env + compose files)
#   EL_DATA_DIR          host path of the frozen node's bind-mounted EL datadir
# Optional:
#   TEMPLATES_DIR        target directory for the tarball (default /var/lib/platform/templates)
#   COMPRESS_LEVEL       zstd compression level (default 3)
#   PLATFORM_API         base URL of the platform REST API (e.g. http://localhost:8080/api/v1).
#                        When set together with FROZEN_NODE_ID and FROZEN_NODE_OWNER_ID, the
#                        script asks the platform to restart the frozen node aggregate after
#                        the EL container is back up — otherwise the reconciler leaves the
#                        node stuck in STOPPED (no auto-transition out of that state).
#   FROZEN_NODE_ID       UUID of the frozen node aggregate (sibling of FROZEN_NODE_DIR).
#   FROZEN_NODE_OWNER_ID UUID sent as X-Owner-Id when calling the restart endpoint.

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
PLATFORM_API="${PLATFORM_API:-}"
FROZEN_NODE_ID="${FROZEN_NODE_ID:-}"
FROZEN_NODE_OWNER_ID="${FROZEN_NODE_OWNER_ID:-}"

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

# The reconciler observes the EL outage and transitions the frozen node aggregate
# to STOPPED; nothing in the state machine transitions it back automatically. We
# ask the platform to restart the aggregate explicitly so a fresh tarball doesn't
# leave the frozen node in STOPPED. Opt-in: skipped if any of the three vars is
# unset, preserving existing setups.
release_node_from_stopped() {
    if [[ -z "$PLATFORM_API" || -z "$FROZEN_NODE_ID" || -z "$FROZEN_NODE_OWNER_ID" ]]; then
        log "skipping platform restart call (PLATFORM_API / FROZEN_NODE_ID / FROZEN_NODE_OWNER_ID not all set)"
        return 0
    fi
    local url="${PLATFORM_API%/}/nodes/${FROZEN_NODE_ID}/restart"
    log "asking platform to restart frozen node aggregate ($url)"
    local body_file http_code
    body_file=$(mktemp)
    http_code=$(curl -sS -o "$body_file" -w '%{http_code}' \
        --max-time 10 \
        -X POST \
        -H "X-Owner-Id: ${FROZEN_NODE_OWNER_ID}" \
        "$url") || {
        log "WARN: curl failed contacting platform — frozen node may stay in STOPPED"
        rm -f "$body_file"
        return 0
    }
    case "$http_code" in
        202) log "platform accepted restart" ;;
        # 409 = node not in STOPPED yet (reconciler hasn't ticked) or already restarting; benign.
        409) log "platform returned 409 (node not in STOPPED) — likely benign, reconciler will catch up" ;;
        *)
            log "WARN: platform returned HTTP $http_code — body: $(tr -d '\n' <"$body_file")"
            log "WARN: frozen node may stay in STOPPED"
            ;;
    esac
    rm -f "$body_file"
}

# Stop only the EL container; the CL keeps running. This is the minimum
# downtime that lets us snapshot a quiescent chaindata directory.
log "stopping execution container in $FROZEN_NODE_DIR"
(cd "$FROZEN_NODE_DIR" && docker compose stop execution)

# From here on, we want to ALWAYS restart the EL — even on failure — so the
# frozen node resumes syncing. The atomic rename below only fires when the
# tar succeeds, so a partial template never replaces the previous one.
# release_node_from_stopped is called *after* cleanup_restart so the EL
# container is up before the platform's restart use case acts on the aggregate.
trap 'rm -f "$tmp"; cleanup_restart; release_node_from_stopped' EXIT

log "building $target via tar+zstd (level $COMPRESS_LEVEL)"
ZSTD_CLEVEL="$COMPRESS_LEVEL" \
    tar --use-compress-program=zstd -cf "$tmp" -C "$EL_DATA_DIR" .

# Atomic on same filesystem: a concurrent provisioning either reads the old
# template or the new one, never a truncated file.
mv -f "$tmp" "$target"
log "template ready: $target ($(du -h "$target" | cut -f1))"

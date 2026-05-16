package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class EthDockerEnvFile {

    public static final String HOST_PORTS_OVERRIDE_FILE = "host-ports.yml";
    public static final String MEV_BOOST_COMPOSE_FILE = "mev-boost.yml";
    public static final String SHARED_NETWORK_OVERRIDE_FILE = "shared-network.yml";
    public static final String SHARED_NETWORK_NAME = "node-provider-shared";
    public static final String EL_DATADIR_BIND_OVERRIDE_FILE = "el-datadir-bind.yml";

    // eth-docker yml files don't publish RPC/WS/REST to the host by default (the project
    // expects traefik in front). For our single-host demo we ship a minimal compose override
    // that binds host-side ports to 127.0.0.1. Container-side ports stay at eth-docker
    // defaults (8545/8546/5052) so intra-network calls like validator -> consensus:5052
    // keep working.
    private static final String HOST_PORTS_OVERRIDE_YAML =
            """
            services:
              execution:
                ports:
                  - "${HOST_IP:-127.0.0.1}:${EL_RPC_HOST_PORT}:8545/tcp"
                  - "${HOST_IP:-127.0.0.1}:${EL_WS_HOST_PORT}:8546/tcp"
              consensus:
                ports:
                  - "${HOST_IP:-127.0.0.1}:${CL_REST_HOST_PORT}:5052/tcp"
            """;

    // Connects the consensus service to a long-lived external docker network so a freshly-spawned
    // node can use a previously-synced sibling's beacon REST as CHECKPOINT_SYNC_URL. The
    // service stays on its project default network too — without that line, validator/execution
    // would lose intra-project connectivity to consensus.
    private static final String SHARED_NETWORK_OVERRIDE_YAML =
            """
            networks:
              shared:
                external: true
                name: node-provider-shared

            services:
              consensus:
                networks:
                  default: {}
                  shared:
                    aliases:
                      - node-${NODE_SHORT_ID}-consensus
            """;

    private EthDockerEnvFile() {}

    public static String hostPortsOverrideYaml() {
        return HOST_PORTS_OVERRIDE_YAML;
    }

    public static String sharedNetworkOverrideYaml() {
        return SHARED_NETWORK_OVERRIDE_YAML;
    }

    // Overrides the top-level named volume (e.g. `besu-el-data`, `geth-el-data`) so docker mounts
    // it from a host bind path instead of allocating a docker-managed volume. The host path is
    // injected via the EL_DATA_HOST_PATH env var set in .env, so the same YAML works for any
    // nodeId. Returns Optional.empty() for EL clients out of PR1 scope (nethermind/erigon), which
    // fall back to eth-docker's default named volumes.
    public static Optional<String> elDatadirBindOverrideYaml(ElClient el) {
        return elDataVolumeName(el)
                .map(
                        name ->
                                """
                                volumes:
                                  %s:
                                    driver: local
                                    driver_opts:
                                      type: none
                                      o: bind
                                      device: ${EL_DATA_HOST_PATH}
                                """
                                        .formatted(name));
    }

    private static Optional<String> elDataVolumeName(ElClient el) {
        return switch (el) {
            case BESU -> Optional.of("besu-el-data");
            case GETH -> Optional.of("geth-el-data");
            case NETHERMIND, ERIGON -> Optional.empty();
        };
    }

    public static Map<String, String> render(
            NodeSpec spec,
            AllocatedPorts ports,
            String composeProjectName,
            Map<String, String> defaults) {
        return render(
                spec, ports, composeProjectName, defaults, Optional.empty(), Optional.empty());
    }

    public static Map<String, String> render(
            NodeSpec spec,
            AllocatedPorts ports,
            String composeProjectName,
            Map<String, String> defaults,
            Optional<URI> checkpointSyncOverride) {
        return render(
                spec,
                ports,
                composeProjectName,
                defaults,
                checkpointSyncOverride,
                Optional.empty());
    }

    public static Map<String, String> render(
            NodeSpec spec,
            AllocatedPorts ports,
            String composeProjectName,
            Map<String, String> defaults,
            Optional<URI> checkpointSyncOverride,
            Optional<Path> elDataHostPath) {
        // The EL datadir bind-mount override is added only when (a) the caller passes a host data
        // path AND (b) the EL client is one we ship a bind override for. nethermind/erigon stay on
        // their default named volume.
        boolean elDatadirBindActive =
                elDataHostPath.isPresent()
                        && elDataVolumeName(spec.clientPair().executionLayer()).isPresent();

        // Start from default.env so values like LOG_LEVEL, EL_NODE, ... flow through; eth-docker
        // compose files reference many of these and lighthouse/geth crash if some are blank.
        Map<String, String> env = new LinkedHashMap<>(defaults);
        env.put("NETWORK", networkName(spec.network()));
        env.put(
                "COMPOSE_FILE",
                composeFile(
                        spec.clientPair().executionLayer(),
                        spec.clientPair().consensusLayer(),
                        spec.options(),
                        elDatadirBindActive));
        env.put("COMPOSE_PROJECT_NAME", composeProjectName);
        env.put("NODE_SHORT_ID", shortId(spec.nodeId()));
        env.put("EL_HOST", "0.0.0.0");
        // Bind published host ports on every interface so the JSON-RPC URL we announce
        // (publicHost from EthDockerProperties) is actually reachable from outside the VM.
        // Without this the compose override falls back to 127.0.0.1 and the URL is unreachable.
        env.put("HOST_IP", "0.0.0.0");
        // EL_RPC_HOST_PORT / EL_WS_HOST_PORT / CL_REST_HOST_PORT are *our* override variables,
        // consumed only by host-ports.yml. Do NOT set EL_RPC_PORT / EL_WS_PORT / CL_REST_PORT
        // here: those would be passed as --http.port / --http-port flags to geth/lighthouse and
        // would break intra-network calls (validator -> consensus:5052, ...).
        env.put("EL_RPC_HOST_PORT", Integer.toString(ports.elRpcPort()));
        env.put("EL_WS_HOST_PORT", Integer.toString(ports.elWsPort()));
        env.put("CL_REST_HOST_PORT", Integer.toString(ports.clRestPort()));
        // P2P ports must match host-side and container-side: peers discover us by the port we
        // announce, which is exactly what these variables configure inside the client.
        env.put("EL_P2P_PORT", Integer.toString(ports.elP2pPort()));
        env.put("ERIGON_TORRENT_PORT", Integer.toString(ports.erigonTorrentPort()));
        env.put("CL_P2P_PORT", Integer.toString(ports.clP2pPort()));
        env.put("CL_QUIC_PORT", Integer.toString(ports.clQuicPort()));
        // Prysm uses its own port keys but binds the same number on TCP and UDP, so they alias
        // CL_P2P_PORT.
        env.put("PRYSM_PORT", Integer.toString(ports.clP2pPort()));
        env.put("PRYSM_UDP_PORT", Integer.toString(ports.clP2pPort()));
        env.put("FEE_RECIPIENT", spec.options().feeRecipient());
        spec.options().graffiti().ifPresent(g -> env.put("GRAFFITI", g));
        if (spec.options().mevBoost()) {
            env.put(
                    "MEV_MIN_BID",
                    spec.options().mevMinBid().orElse(NodeOptions.DEFAULT_MEV_MIN_BID));
            env.put(
                    "MEV_BUILD_FACTOR",
                    Integer.toString(
                            spec.options().mevBuildFactor().isPresent()
                                    ? spec.options().mevBuildFactor().getAsInt()
                                    : NodeOptions.DEFAULT_MEV_BUILD_FACTOR));
        }
        // When a sibling beacon is already past initial sync on the same network, point this CL
        // at it so the new node skips the long internet-side checkpoint and bootstraps in seconds
        // off the LAN. If absent, eth-docker's default.env wins (e.g.
        // https://hoodi.checkpoint.sigp.io).
        checkpointSyncOverride.ifPresent(uri -> env.put("CHECKPOINT_SYNC_URL", uri.toString()));
        if (elDatadirBindActive) {
            env.put("EL_DATA_HOST_PATH", elDataHostPath.get().toString());
        }
        return Map.copyOf(env);
    }

    static String shortId(NodeId id) {
        return id.value().toString().substring(0, 8);
    }

    public static String serialize(Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        env.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    private static String networkName(Network network) {
        return network.name().toLowerCase(Locale.ROOT);
    }

    private static String composeFile(
            ElClient el, ClClient cl, NodeOptions options, boolean elDatadirBindActive) {
        StringBuilder sb = new StringBuilder();
        sb.append(elComposeFile(el)).append(':').append(clComposeFile(cl, options.validator()));
        if (options.mevBoost()) {
            sb.append(':').append(MEV_BOOST_COMPOSE_FILE);
        }
        sb.append(':').append(HOST_PORTS_OVERRIDE_FILE);
        sb.append(':').append(SHARED_NETWORK_OVERRIDE_FILE);
        if (elDatadirBindActive) {
            sb.append(':').append(EL_DATADIR_BIND_OVERRIDE_FILE);
        }
        return sb.toString();
    }

    private static String elComposeFile(ElClient el) {
        return switch (el) {
            case BESU -> "besu.yml";
            case GETH -> "geth.yml";
            case NETHERMIND -> "nethermind.yml";
            case ERIGON -> "erigon.yml";
        };
    }

    private static String clComposeFile(ClClient cl, boolean withValidator) {
        // -cl-only.yml runs the beacon node alone; the plain *.yml also brings up a validator
        // service. We pick based on the per-node opt-in flag.
        if (withValidator) {
            return switch (cl) {
                case TEKU -> "teku.yml";
                case LIGHTHOUSE -> "lighthouse.yml";
                case PRYSM -> "prysm.yml";
                case NIMBUS -> "nimbus.yml";
                case LODESTAR -> "lodestar.yml";
            };
        }
        return switch (cl) {
            case TEKU -> "teku-cl-only.yml";
            case LIGHTHOUSE -> "lighthouse-cl-only.yml";
            case PRYSM -> "prysm-cl-only.yml";
            case NIMBUS -> "nimbus-cl-only.yml";
            case LODESTAR -> "lodestar-cl-only.yml";
        };
    }
}

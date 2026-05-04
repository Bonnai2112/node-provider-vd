package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class EthDockerEnvFile {

    private static final String FEE_RECIPIENT_PLACEHOLDER =
            "0x0000000000000000000000000000000000000000";

    private EthDockerEnvFile() {}

    public static Map<String, String> render(
            NodeSpec spec, AllocatedPorts ports, String composeProjectName) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("NETWORK", networkName(spec.network()));
        env.put(
                "COMPOSE_FILE",
                composeFile(
                        spec.clientPair().executionLayer(), spec.clientPair().consensusLayer()));
        env.put("COMPOSE_PROJECT_NAME", composeProjectName);
        env.put("EL_HOST", "0.0.0.0");
        env.put("EL_RPC_PORT", Integer.toString(ports.elRpcPort()));
        env.put("EL_WS_PORT", Integer.toString(ports.elWsPort()));
        env.put("CL_REST_PORT", Integer.toString(ports.clRestPort()));
        env.put("FEE_RECIPIENT", FEE_RECIPIENT_PLACEHOLDER);
        return Map.copyOf(env);
    }

    public static String serialize(Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        env.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    private static String networkName(Network network) {
        return network.name().toLowerCase(Locale.ROOT);
    }

    private static String composeFile(ElClient el, ClClient cl) {
        return elComposeFile(el) + ":" + clComposeFile(cl);
    }

    private static String elComposeFile(ElClient el) {
        return switch (el) {
            case BESU -> "besu.yml";
            case GETH -> "geth.yml";
            case NETHERMIND -> "nethermind.yml";
            case ERIGON -> "erigon.yml";
        };
    }

    private static String clComposeFile(ClClient cl) {
        return switch (cl) {
            case TEKU -> "teku.yml";
            case LIGHTHOUSE -> "lighthouse.yml";
            case PRYSM -> "prysm.yml";
            case NIMBUS -> "nimbus.yml";
            case LODESTAR -> "lodestar.yml";
        };
    }
}

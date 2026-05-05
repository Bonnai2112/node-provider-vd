package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EthDockerEnvFileTest {

    private static final AllocatedPorts PORTS =
            new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
    private static final String PROJECT_NAME = "node-abc12345";
    private static final Map<String, String> DEFAULTS =
            Map.of("ENV_VERSION", "55", "LOG_LEVEL", "info");

    private static NodeSpec spec(Network network, ElClient el, ClClient cl) {
        return spec(network, el, cl, NodeOptions.defaults());
    }

    private static NodeSpec spec(Network network, ElClient el, ClClient cl, NodeOptions options) {
        return new NodeSpec(
                new NodeId(UUID.randomUUID()),
                new OwnerId(UUID.randomUUID()),
                network,
                new ClientPair(el, cl),
                options);
    }

    @Test
    void render_should_setNetworkLowercase_when_hoodi() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env).containsEntry("NETWORK", "hoodi");
    }

    @Test
    void render_should_setNetworkLowercase_when_sepolia() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.SEPOLIA, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env).containsEntry("NETWORK", "sepolia");
    }

    @Test
    void render_should_buildComposeFile_from_elAndCl_when_besuTeku() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env).containsEntry("COMPOSE_FILE", "besu.yml:teku-cl-only.yml:host-ports.yml");
    }

    @Test
    void render_should_buildComposeFile_from_elAndCl_when_gethLighthouse() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.GETH, ClClient.LIGHTHOUSE),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env)
                .containsEntry("COMPOSE_FILE", "geth.yml:lighthouse-cl-only.yml:host-ports.yml");
    }

    @Test
    void render_should_appendHostPortsOverride_to_composeFile() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env.get("COMPOSE_FILE")).endsWith(":host-ports.yml");
    }

    @Test
    void hostPortsOverrideYaml_should_publishElRpcWsAndClRest() {
        String yaml = EthDockerEnvFile.hostPortsOverrideYaml();

        assertThat(yaml)
                .contains("execution:")
                .contains("consensus:")
                .contains("${EL_RPC_PORT}:${EL_RPC_PORT}")
                .contains("${EL_WS_PORT}:${EL_WS_PORT}")
                .contains("${CL_REST_PORT}:${CL_REST_PORT}")
                .contains("${HOST_IP:-127.0.0.1}");
    }

    @Test
    void render_should_useClOnlyVariant_for_everyConsensusClient() {
        for (ClClient cl : ClClient.values()) {
            Map<String, String> env =
                    EthDockerEnvFile.render(
                            spec(Network.HOODI, ElClient.BESU, cl), PORTS, PROJECT_NAME, DEFAULTS);

            String composeFile = env.get("COMPOSE_FILE");
            assertThat(composeFile.split(":"))
                    .as("COMPOSE_FILE for CL=%s must include a -cl-only.yml fragment", cl)
                    .anyMatch(fragment -> fragment.endsWith("-cl-only.yml"));
        }
    }

    @Test
    void render_should_neverIncludeValidatorYml_in_composeFile() {
        for (ElClient el : ElClient.values()) {
            for (ClClient cl : ClClient.values()) {
                Map<String, String> env =
                        EthDockerEnvFile.render(
                                spec(Network.HOODI, el, cl), PORTS, PROJECT_NAME, DEFAULTS);

                String composeFile = env.get("COMPOSE_FILE");
                assertThat(composeFile)
                        .as("COMPOSE_FILE for %s/%s must NOT include any validator yml", el, cl)
                        .doesNotContain("validator")
                        .doesNotContain("vc-")
                        .doesNotContain("vc.yml");
            }
        }
    }

    @Test
    void render_should_neverIncludeMevBoostYml_in_composeFile() {
        for (ElClient el : ElClient.values()) {
            for (ClClient cl : ClClient.values()) {
                Map<String, String> env =
                        EthDockerEnvFile.render(
                                spec(Network.HOODI, el, cl), PORTS, PROJECT_NAME, DEFAULTS);

                String composeFile = env.get("COMPOSE_FILE");
                assertThat(composeFile)
                        .as("COMPOSE_FILE for %s/%s must NOT include MEV-Boost yml", el, cl)
                        .doesNotContainIgnoringCase("mev-boost")
                        .doesNotContainIgnoringCase("mevboost");
            }
        }
    }

    @Test
    void render_should_setElPorts_when_provided() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env)
                .containsEntry("EL_RPC_PORT", "30100")
                .containsEntry("EL_WS_PORT", "30101")
                .containsEntry("EL_P2P_PORT", "30102")
                .containsEntry("ERIGON_TORRENT_PORT", "30103");
    }

    @Test
    void render_should_setClPorts_when_provided() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env)
                .containsEntry("CL_REST_PORT", "30104")
                .containsEntry("CL_P2P_PORT", "30105")
                .containsEntry("CL_QUIC_PORT", "30106");
    }

    @Test
    void render_should_aliasPrysmPorts_to_clP2pPort() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.PRYSM),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env)
                .containsEntry("PRYSM_PORT", "30105")
                .containsEntry("PRYSM_UDP_PORT", "30105");
    }

    @Test
    void render_should_setComposeProjectName_when_provided() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env).containsEntry("COMPOSE_PROJECT_NAME", PROJECT_NAME);
    }

    @Test
    void render_should_setFeeRecipient_to_zeroPlaceholder() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env)
                .containsEntry("FEE_RECIPIENT", "0x0000000000000000000000000000000000000000");
    }

    @Test
    void render_should_setElHost_to_allInterfaces() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        assertThat(env).containsEntry("EL_HOST", "0.0.0.0");
    }

    @Test
    void serialize_should_produceKeyEqualsValueLines() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        DEFAULTS);

        String text = EthDockerEnvFile.serialize(env);

        assertThat(text)
                .contains("NETWORK=hoodi")
                .contains("COMPOSE_FILE=besu.yml:teku-cl-only.yml:host-ports.yml")
                .contains("FEE_RECIPIENT=0x0000000000000000000000000000000000000000");
    }

    @Test
    void render_should_propagateDefaults_when_keysNotOverridden() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        Map.of("ENV_VERSION", "55", "LOG_LEVEL", "info", "EL_NODE", ""));

        assertThat(env)
                .containsEntry("LOG_LEVEL", "info")
                .containsEntry("EL_NODE", "")
                .containsEntry("ENV_VERSION", "55");
    }

    @Test
    void render_should_overrideDefaults_when_keyConflicts() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        Map.of(
                                "ENV_VERSION", "55",
                                "NETWORK", "mainnet",
                                "EL_RPC_PORT", "8545",
                                "COMPOSE_FILE", "should-be-replaced.yml"));

        assertThat(env)
                .containsEntry("NETWORK", "hoodi")
                .containsEntry("EL_RPC_PORT", "30100")
                .containsEntry("COMPOSE_FILE", "besu.yml:teku-cl-only.yml:host-ports.yml");
    }

    @Test
    void render_should_setEnvVersion_when_provided() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        Map.of("ENV_VERSION", "77"));

        assertThat(env).containsEntry("ENV_VERSION", "77");
    }

    @Test
    void serialize_should_includeEnvVersionLine() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU),
                        PORTS,
                        PROJECT_NAME,
                        Map.of("ENV_VERSION", "55"));

        String text = EthDockerEnvFile.serialize(env);

        assertThat(text).contains("ENV_VERSION=55");
    }
}

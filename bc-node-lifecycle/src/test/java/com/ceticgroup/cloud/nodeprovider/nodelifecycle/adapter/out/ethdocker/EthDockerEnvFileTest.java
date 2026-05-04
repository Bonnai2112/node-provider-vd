package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EthDockerEnvFileTest {

    private static final AllocatedPorts PORTS = new AllocatedPorts(30100, 30101, 30102);
    private static final String PROJECT_NAME = "node-abc12345";

    private static NodeSpec spec(Network network, ElClient el, ClClient cl) {
        return new NodeSpec(
                new NodeId(UUID.randomUUID()),
                new OwnerId(UUID.randomUUID()),
                network,
                new ClientPair(el, cl));
    }

    @Test
    void render_should_setNetworkLowercase_when_hoodi() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env).containsEntry("NETWORK", "hoodi");
    }

    @Test
    void render_should_setNetworkLowercase_when_sepolia() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.SEPOLIA, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env).containsEntry("NETWORK", "sepolia");
    }

    @Test
    void render_should_buildComposeFile_from_elAndCl_when_besuTeku() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env).containsEntry("COMPOSE_FILE", "besu.yml:teku.yml");
    }

    @Test
    void render_should_buildComposeFile_from_elAndCl_when_gethLighthouse() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.GETH, ClClient.LIGHTHOUSE),
                        PORTS,
                        PROJECT_NAME);

        assertThat(env).containsEntry("COMPOSE_FILE", "geth.yml:lighthouse.yml");
    }

    @Test
    void render_should_neverIncludeValidatorYml_in_composeFile() {
        for (ElClient el : ElClient.values()) {
            for (ClClient cl : ClClient.values()) {
                Map<String, String> env =
                        EthDockerEnvFile.render(spec(Network.HOODI, el, cl), PORTS, PROJECT_NAME);

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
                        EthDockerEnvFile.render(spec(Network.HOODI, el, cl), PORTS, PROJECT_NAME);

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
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env).containsEntry("EL_RPC_PORT", "30100").containsEntry("EL_WS_PORT", "30101");
    }

    @Test
    void render_should_setClRestPort_when_provided() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env).containsEntry("CL_REST_PORT", "30102");
    }

    @Test
    void render_should_setComposeProjectName_when_provided() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env).containsEntry("COMPOSE_PROJECT_NAME", PROJECT_NAME);
    }

    @Test
    void render_should_setFeeRecipient_to_zeroPlaceholder() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env)
                .containsEntry("FEE_RECIPIENT", "0x0000000000000000000000000000000000000000");
    }

    @Test
    void render_should_setElHost_to_allInterfaces() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        assertThat(env).containsEntry("EL_HOST", "0.0.0.0");
    }

    @Test
    void serialize_should_produceKeyEqualsValueLines() {
        Map<String, String> env =
                EthDockerEnvFile.render(
                        spec(Network.HOODI, ElClient.BESU, ClClient.TEKU), PORTS, PROJECT_NAME);

        String text = EthDockerEnvFile.serialize(env);

        assertThat(text)
                .contains("NETWORK=hoodi")
                .contains("COMPOSE_FILE=besu.yml:teku.yml")
                .contains("FEE_RECIPIENT=0x0000000000000000000000000000000000000000");
    }
}

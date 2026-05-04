package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EthDockerOrchestrationAdapterTest {

    private static final EthDockerProperties PROPS =
            new EthDockerProperties(
                    "https://example.invalid/eth-docker.git",
                    "v26.4.1",
                    "/tmp/platform/nodes",
                    "/tmp/platform/cache",
                    "/tmp/platform/cache/sha");

    @Mock private PortAllocator portAllocator;
    @Mock private EthDockerRefResolver refResolver;
    @Mock private EthdShellRunner shell;
    @Mock private ContainerInspector inspector;

    private final ObjectMapper mapper = new ObjectMapper();
    private EthDockerOrchestrationAdapter adapter;
    private NodeSpec spec;

    @BeforeEach
    void setUp() {
        adapter =
                new EthDockerOrchestrationAdapter(
                        PROPS, portAllocator, refResolver, shell, inspector, mapper);
        spec =
                new NodeSpec(
                        new NodeId(UUID.randomUUID()),
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku());
    }

    @Test
    void deploy_should_returnDeploymentRefWithSerializedPayload() throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102);
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);

        DeploymentRef result = adapter.deploy(spec);

        verify(shell).ensureCache(any(), anyString());
        verify(shell).cloneIntoWorkdir(any(), any(), any());
        verify(shell).writeEnv(any(), anyString());
        verify(shell).runEthdUp(any());

        DeploymentPayload payload = mapper.readValue(result.payload(), DeploymentPayload.class);
        assertThat(payload.composeProjectName()).startsWith("node-");
        assertThat(payload.ports()).isEqualTo(ports);
        assertThat(payload.ref()).isEqualTo(ref);
        assertThat(payload.workdir()).contains(spec.nodeId().value().toString());
    }

    @Test
    void tearDown_should_runDownAndTerminate_when_payloadValid() throws Exception {
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/platform/nodes/x/eth-docker",
                        "node-deadbeef",
                        new AllocatedPorts(30100, 30101, 30102),
                        new EthDockerRef("v26.4.1", "abc123"));
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        adapter.tearDown(ref);

        verify(shell).runEthdDown(Path.of("/tmp/platform/nodes/x/eth-docker"));
        verify(shell).runEthdTerminate(Path.of("/tmp/platform/nodes/x/eth-docker"));
        verify(shell).removeWorkdir(Path.of("/tmp/platform/nodes/x/eth-docker"));
    }

    @Test
    void getDeploymentStatus_should_delegateToInspectorWithProjectName() throws Exception {
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/x",
                        "node-12345678",
                        new AllocatedPorts(30100, 30101, 30102),
                        new EthDockerRef("v26.4.1", "abc"));
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));
        when(inspector.inspectByProject("node-12345678")).thenReturn(new RuntimeStatus.Running());

        RuntimeStatus status = adapter.getDeploymentStatus(ref);

        assertThat(status).isInstanceOf(RuntimeStatus.Running.class);
    }

    @Test
    void endpointFor_should_returnEndpointBuiltFromPayloadPort() throws Exception {
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/x",
                        "node-12345678",
                        new AllocatedPorts(30123, 30101, 30102),
                        new EthDockerRef("v26.4.1", "abc"));
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        Optional<JsonRpcEndpoint> endpoint = adapter.endpointFor(ref);

        assertThat(endpoint).isPresent();
        assertThat(endpoint.get().uri().toString()).isEqualTo("http://localhost:30123");
    }
}

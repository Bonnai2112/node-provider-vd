package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LayerState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.CheckpointSyncSourceLocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
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
    @Mock private CheckpointSyncSourceLocator checkpointLocator;
    @Mock private DockerNetworkManager networkManager;

    private final ObjectMapper mapper = new ObjectMapper();
    private EthDockerOrchestrationAdapter adapter;
    private NodeSpec spec;

    @BeforeEach
    void setUp() {
        adapter =
                new EthDockerOrchestrationAdapter(
                        PROPS,
                        portAllocator,
                        refResolver,
                        shell,
                        inspector,
                        checkpointLocator,
                        networkManager,
                        mapper);
        spec =
                new NodeSpec(
                        new NodeId(UUID.randomUUID()),
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults());
    }

    @Test
    void deploy_should_returnDeploymentRefWithSerializedPayload() throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(spec.network())).thenReturn(Optional.empty());

        DeploymentRef result = adapter.deploy(spec);

        verify(shell).ensureCache(any(), anyString());
        verify(shell).cloneIntoWorkdir(any(), any(), any());
        verify(shell).readDefaultEnv(any());
        verify(shell).writeEnv(any(), anyString());
        verify(shell).writeFile(any(), eq("host-ports.yml"), anyString());
        verify(shell).writeFile(any(), eq("shared-network.yml"), anyString());
        verify(networkManager).ensureSharedNetworkExists("node-provider-shared");
        verify(shell).runEthdUp(any());

        DeploymentPayload payload = mapper.readValue(result.payload(), DeploymentPayload.class);
        assertThat(payload.composeProjectName()).startsWith("node-");
        assertThat(payload.ports()).isEqualTo(ports);
        assertThat(payload.ref()).isEqualTo(ref);
        assertThat(payload.workdir()).contains(spec.nodeId().value().toString());
    }

    @Test
    void deploy_should_consumeCheckpointSyncOverride_when_locatorReturnsUrl() throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        URI leaderUrl = URI.create("http://node-deadbeef-consensus:5052");
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(spec.network())).thenReturn(Optional.of(leaderUrl));

        adapter.deploy(spec);

        org.mockito.ArgumentCaptor<String> envCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(shell).writeEnv(any(), envCaptor.capture());
        assertThat(envCaptor.getValue())
                .contains("CHECKPOINT_SYNC_URL=http://node-deadbeef-consensus:5052");
    }

    @Test
    void tearDown_should_runDownAndTerminate_when_payloadValid() throws Exception {
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/platform/nodes/x/eth-docker",
                        "node-deadbeef",
                        new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106),
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
                        new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc"));
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));
        RuntimeStatus.Healthy expected =
                RuntimeStatus.Healthy.of(new LayerState.Running(), new LayerState.Running());
        when(inspector.inspectByProject("node-12345678")).thenReturn(expected);

        RuntimeStatus status = adapter.getDeploymentStatus(ref);

        assertThat(status).isEqualTo(expected);
    }

    @Test
    void endpointFor_should_returnEndpointBuiltFromPayloadPort() throws Exception {
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/x",
                        "node-12345678",
                        new AllocatedPorts(30123, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc"));
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        Optional<JsonRpcEndpoint> endpoint = adapter.endpointFor(ref);

        assertThat(endpoint).isPresent();
        assertThat(endpoint.get().uri().toString()).isEqualTo("http://localhost:30123");
    }
}

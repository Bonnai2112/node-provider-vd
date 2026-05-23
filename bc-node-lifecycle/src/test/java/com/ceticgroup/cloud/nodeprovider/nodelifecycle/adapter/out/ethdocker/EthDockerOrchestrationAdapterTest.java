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
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ElDatadirTemplateLocator;
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
                    "/tmp/platform/cache/sha",
                    "/tmp/platform/templates",
                    "localhost",
                    null);

    @Mock private PortAllocator portAllocator;
    @Mock private EthDockerRefResolver refResolver;
    @Mock private EthdShellRunner shell;
    @Mock private ContainerInspector inspector;
    @Mock private CheckpointSyncSourceLocator checkpointLocator;
    @Mock private ElDatadirTemplateLocator templateLocator;
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
                        templateLocator,
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
    void deploy_should_writeElDatadirBindOverride_and_ensureDataDir_when_besu() throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(spec.network())).thenReturn(Optional.empty());

        DeploymentRef result = adapter.deploy(spec);

        Path expectedDataDir =
                Path.of("/tmp/platform/nodes", spec.nodeId().value().toString(), "data");
        verify(shell).ensureDataDir(expectedDataDir, 10000);
        verify(shell).writeFile(any(), eq("el-datadir-bind.yml"), anyString());

        DeploymentPayload payload = mapper.readValue(result.payload(), DeploymentPayload.class);
        assertThat(payload.elDataHostPath()).isEqualTo(expectedDataDir.toString());
    }

    @Test
    void deploy_should_includeElDataHostPath_in_envFile_when_besu() throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(spec.network())).thenReturn(Optional.empty());

        adapter.deploy(spec);

        org.mockito.ArgumentCaptor<String> envCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(shell).writeEnv(any(), envCaptor.capture());
        assertThat(envCaptor.getValue())
                .contains("EL_DATA_HOST_PATH=/tmp/platform/nodes/")
                .contains("/data\n");
    }

    @Test
    void deploy_should_extractTemplate_before_ensureDataDir_when_locatorReturnsTarball()
            throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        Path tarball = Path.of("/tmp/platform/templates/hoodi-besu.tar.zst");
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(spec.network())).thenReturn(Optional.empty());
        when(templateLocator.findTemplate(spec.network(), spec.clientPair().executionLayer()))
                .thenReturn(Optional.of(tarball));

        adapter.deploy(spec);

        Path expectedDataDir =
                Path.of("/tmp/platform/nodes", spec.nodeId().value().toString(), "data");
        // Extraction MUST run before chown so the recursive chown covers the restored files.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(shell);
        inOrder.verify(shell).extractTarballZstd(tarball, expectedDataDir);
        inOrder.verify(shell).ensureDataDir(expectedDataDir, 10000);
    }

    @Test
    void deploy_should_skipExtraction_and_callEnsureDataDir_when_templateMissing()
            throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(spec.network())).thenReturn(Optional.empty());
        when(templateLocator.findTemplate(spec.network(), spec.clientPair().executionLayer()))
                .thenReturn(Optional.empty());

        adapter.deploy(spec);

        verify(shell, org.mockito.Mockito.never()).extractTarballZstd(any(), any());
        Path expectedDataDir =
                Path.of("/tmp/platform/nodes", spec.nodeId().value().toString(), "data");
        verify(shell).ensureDataDir(expectedDataDir, 10000);
    }

    @Test
    void deploy_should_notCallTemplateLocator_when_nethermindOrErigon() throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        NodeSpec erigonSpec =
                new NodeSpec(
                        new NodeId(UUID.randomUUID()),
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        new com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair(
                                com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient
                                        .ERIGON,
                                com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient
                                        .TEKU),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults());
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(erigonSpec.network())).thenReturn(Optional.empty());

        adapter.deploy(erigonSpec);

        verify(templateLocator, org.mockito.Mockito.never()).findTemplate(any(), any());
        verify(shell, org.mockito.Mockito.never()).extractTarballZstd(any(), any());
    }

    @Test
    void deploy_should_skipElDatadirBindOverride_when_nethermind() throws Exception {
        EthDockerRef ref = new EthDockerRef("v26.4.1", "abc123");
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);
        NodeSpec nethermindSpec =
                new NodeSpec(
                        new NodeId(UUID.randomUUID()),
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        new com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair(
                                com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient
                                        .NETHERMIND,
                                com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient
                                        .TEKU),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults());
        when(refResolver.resolve(anyString(), anyString())).thenReturn(ref);
        when(portAllocator.allocate()).thenReturn(ports);
        when(shell.readDefaultEnv(any())).thenReturn(Map.of("ENV_VERSION", "55"));
        when(checkpointLocator.findFor(nethermindSpec.network())).thenReturn(Optional.empty());

        DeploymentRef result = adapter.deploy(nethermindSpec);

        verify(shell, org.mockito.Mockito.never())
                .ensureDataDir(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(shell, org.mockito.Mockito.never())
                .writeFile(any(), eq("el-datadir-bind.yml"), anyString());

        DeploymentPayload payload = mapper.readValue(result.payload(), DeploymentPayload.class);
        assertThat(payload.elDataHostPath()).isNull();
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
    void tearDown_should_runDownAndTerminate_and_removeDataDir_when_elDataHostPathPresent()
            throws Exception {
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/platform/nodes/x/eth-docker",
                        "node-deadbeef",
                        new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc123"),
                        "/tmp/platform/nodes/x/data");
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        adapter.tearDown(ref);

        verify(shell).runEthdDown(Path.of("/tmp/platform/nodes/x/eth-docker"));
        verify(shell).runEthdTerminate(Path.of("/tmp/platform/nodes/x/eth-docker"));
        verify(shell).removeWorkdir(Path.of("/tmp/platform/nodes/x/eth-docker"));
        verify(shell).removeDataDir(Path.of("/tmp/platform/nodes/x/data"));
        verify(shell).removeNodeRoot(Path.of("/tmp/platform/nodes/x"));
    }

    @Test
    void tearDown_should_notCallRemoveDataDir_when_elDataHostPathNull() throws Exception {
        // Pre-PR1 payload or nethermind/erigon: no bind-mount, no data dir to remove.
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/platform/nodes/x/eth-docker",
                        "node-deadbeef",
                        new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc123"),
                        null);
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        adapter.tearDown(ref);

        verify(shell, org.mockito.Mockito.never()).removeDataDir(any());
        verify(shell).removeNodeRoot(Path.of("/tmp/platform/nodes/x"));
    }

    @Test
    void tearDown_should_skipRemoveNodeRoot_when_workdirNotUnderRootDir() throws Exception {
        // Defensive guard: if the persisted workdir lives outside the configured rootDir
        // (legacy payload, manual fixture), we MUST NOT recursively sudo rm its parent.
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/some/other/path/eth-docker",
                        "node-deadbeef",
                        new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc123"),
                        null);
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        adapter.tearDown(ref);

        verify(shell, org.mockito.Mockito.never()).removeNodeRoot(any());
    }

    @Test
    void getDeploymentStatus_should_delegateToInspectorWithProjectName() throws Exception {
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/x",
                        "node-12345678",
                        new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc"),
                        null);
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
                        new EthDockerRef("v26.4.1", "abc"),
                        null);
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        Optional<JsonRpcEndpoint> endpoint = adapter.endpointFor(ref);

        assertThat(endpoint).isPresent();
        assertThat(endpoint.get().uri().toString()).isEqualTo("http://localhost:30123");
    }

    @Test
    void endpointFor_should_useConfiguredPublicHost_when_overridden() throws Exception {
        EthDockerProperties propsWithIp =
                new EthDockerProperties(
                        "https://example.invalid/eth-docker.git",
                        "v26.4.1",
                        "/tmp/platform/nodes",
                        "/tmp/platform/cache",
                        "/tmp/platform/cache/sha",
                        "/tmp/platform/templates",
                        "203.0.113.42",
                        null);
        EthDockerOrchestrationAdapter adapterWithIp =
                new EthDockerOrchestrationAdapter(
                        propsWithIp,
                        portAllocator,
                        refResolver,
                        shell,
                        inspector,
                        checkpointLocator,
                        templateLocator,
                        networkManager,
                        mapper);
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/x",
                        "node-12345678",
                        new AllocatedPorts(30123, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc"),
                        null);
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        Optional<JsonRpcEndpoint> endpoint = adapterWithIp.endpointFor(ref);

        assertThat(endpoint).isPresent();
        assertThat(endpoint.get().uri().toString()).isEqualTo("http://203.0.113.42:30123");
    }

    @Test
    void internalEndpointFor_should_use127001_evenWhenPublicHostIsOverridden() throws Exception {
        // Internal probes must hit loopback: many clouds don't support hairpin NAT, so a request
        // from this VM to its own public IP times out. The endpoint persisted on the aggregate
        // (endpointFor) keeps the public host since it's what API consumers see.
        EthDockerProperties propsWithIp =
                new EthDockerProperties(
                        "https://example.invalid/eth-docker.git",
                        "v26.4.1",
                        "/tmp/platform/nodes",
                        "/tmp/platform/cache",
                        "/tmp/platform/cache/sha",
                        "/tmp/platform/templates",
                        "203.0.113.42",
                        null);
        EthDockerOrchestrationAdapter adapterWithIp =
                new EthDockerOrchestrationAdapter(
                        propsWithIp,
                        portAllocator,
                        refResolver,
                        shell,
                        inspector,
                        checkpointLocator,
                        templateLocator,
                        networkManager,
                        mapper);
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/x",
                        "node-12345678",
                        new AllocatedPorts(30123, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc"),
                        null);
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        Optional<JsonRpcEndpoint> endpoint = adapterWithIp.internalEndpointFor(ref);

        assertThat(endpoint).isPresent();
        assertThat(endpoint.get().uri().toString()).isEqualTo("http://127.0.0.1:30123");
    }

    @Test
    void internalClRestEndpointFor_should_use127001_evenWhenPublicHostIsOverridden()
            throws Exception {
        EthDockerProperties propsWithIp =
                new EthDockerProperties(
                        "https://example.invalid/eth-docker.git",
                        "v26.4.1",
                        "/tmp/platform/nodes",
                        "/tmp/platform/cache",
                        "/tmp/platform/cache/sha",
                        "/tmp/platform/templates",
                        "203.0.113.42",
                        null);
        EthDockerOrchestrationAdapter adapterWithIp =
                new EthDockerOrchestrationAdapter(
                        propsWithIp,
                        portAllocator,
                        refResolver,
                        shell,
                        inspector,
                        checkpointLocator,
                        templateLocator,
                        networkManager,
                        mapper);
        DeploymentPayload payload =
                new DeploymentPayload(
                        "/tmp/x",
                        "node-12345678",
                        new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106),
                        new EthDockerRef("v26.4.1", "abc"),
                        null);
        DeploymentRef ref = new DeploymentRef(mapper.writeValueAsString(payload));

        Optional<URI> endpoint = adapterWithIp.internalClRestEndpointFor(ref);

        assertThat(endpoint).isPresent();
        assertThat(endpoint.get().toString()).isEqualTo("http://127.0.0.1:30104");
    }
}

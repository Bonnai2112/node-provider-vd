package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.CheckpointSyncSourceLocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public class EthDockerOrchestrationAdapter implements NodeOrchestrationPort {

    private final EthDockerProperties properties;
    private final PortAllocator portAllocator;
    private final EthDockerRefResolver refResolver;
    private final EthdShellRunner shell;
    private final ContainerInspector containerInspector;
    private final CheckpointSyncSourceLocator checkpointLocator;
    private final DockerNetworkManager networkManager;
    private final ObjectMapper mapper;

    public EthDockerOrchestrationAdapter(
            EthDockerProperties properties,
            PortAllocator portAllocator,
            EthDockerRefResolver refResolver,
            EthdShellRunner shell,
            ContainerInspector containerInspector,
            CheckpointSyncSourceLocator checkpointLocator,
            DockerNetworkManager networkManager,
            ObjectMapper mapper) {
        this.properties = properties;
        this.portAllocator = portAllocator;
        this.refResolver = refResolver;
        this.shell = shell;
        this.containerInspector = containerInspector;
        this.checkpointLocator = checkpointLocator;
        this.networkManager = networkManager;
        this.mapper = mapper;
    }

    @Override
    public DeploymentRef deploy(NodeSpec spec) {
        try {
            EthDockerRef ref = refResolver.resolve(properties.repoUrl(), properties.ref());
            AllocatedPorts ports = portAllocator.allocate();
            String projectName = "node-" + spec.nodeId().value().toString().substring(0, 8);
            Path workdir =
                    Paths.get(properties.rootDir(), spec.nodeId().value().toString(), "eth-docker");

            shell.ensureCache(Paths.get(properties.cacheDir()), properties.repoUrl());
            shell.cloneIntoWorkdir(Paths.get(properties.cacheDir()), ref.tag(), workdir);

            Map<String, String> defaults = shell.readDefaultEnv(workdir);
            Optional<URI> checkpointOverride = checkpointLocator.findFor(spec.network());
            Map<String, String> env =
                    EthDockerEnvFile.render(spec, ports, projectName, defaults, checkpointOverride);
            shell.writeEnv(workdir, EthDockerEnvFile.serialize(env));
            shell.writeFile(
                    workdir,
                    EthDockerEnvFile.HOST_PORTS_OVERRIDE_FILE,
                    EthDockerEnvFile.hostPortsOverrideYaml());
            shell.writeFile(
                    workdir,
                    EthDockerEnvFile.SHARED_NETWORK_OVERRIDE_FILE,
                    EthDockerEnvFile.sharedNetworkOverrideYaml());

            networkManager.ensureSharedNetworkExists(EthDockerEnvFile.SHARED_NETWORK_NAME);

            shell.runEthdUp(workdir);

            DeploymentPayload payload =
                    new DeploymentPayload(workdir.toString(), projectName, ports, ref);
            return new DeploymentRef(serialize(payload));
        } catch (IOException e) {
            throw new IllegalStateException("eth-docker deploy failed", e);
        }
    }

    @Override
    public void tearDown(DeploymentRef ref) {
        DeploymentPayload payload = deserialize(ref);
        Path workdir = Paths.get(payload.workdir());
        try {
            shell.runEthdDown(workdir);
            shell.runEthdTerminate(workdir);
            shell.removeWorkdir(workdir);
        } catch (IOException e) {
            throw new IllegalStateException("eth-docker tearDown failed", e);
        }
    }

    @Override
    public RuntimeStatus getDeploymentStatus(DeploymentRef ref) {
        return containerInspector.inspectByProject(deserialize(ref).composeProjectName());
    }

    @Override
    public Optional<JsonRpcEndpoint> endpointFor(DeploymentRef ref) {
        DeploymentPayload payload = deserialize(ref);
        return Optional.of(
                new JsonRpcEndpoint(URI.create("http://localhost:" + payload.ports().elRpcPort())));
    }

    @Override
    public Optional<URI> clRestEndpointFor(DeploymentRef ref) {
        DeploymentPayload payload = deserialize(ref);
        return Optional.of(URI.create("http://localhost:" + payload.ports().clRestPort()));
    }

    private String serialize(DeploymentPayload payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialize DeploymentPayload", e);
        }
    }

    private DeploymentPayload deserialize(DeploymentRef ref) {
        try {
            return mapper.readValue(ref.payload(), DeploymentPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not deserialize DeploymentPayload", e);
        }
    }
}

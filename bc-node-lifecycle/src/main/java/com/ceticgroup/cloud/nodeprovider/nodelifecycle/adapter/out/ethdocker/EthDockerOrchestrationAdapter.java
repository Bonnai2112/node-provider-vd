package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.CheckpointSyncSourceLocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ElDatadirTemplateLocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
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
    private final ElDatadirTemplateLocator templateLocator;
    private final DockerNetworkManager networkManager;
    private final ObjectMapper mapper;

    public EthDockerOrchestrationAdapter(
            EthDockerProperties properties,
            PortAllocator portAllocator,
            EthDockerRefResolver refResolver,
            EthdShellRunner shell,
            ContainerInspector containerInspector,
            CheckpointSyncSourceLocator checkpointLocator,
            ElDatadirTemplateLocator templateLocator,
            DockerNetworkManager networkManager,
            ObjectMapper mapper) {
        this.properties = properties;
        this.portAllocator = portAllocator;
        this.refResolver = refResolver;
        this.shell = shell;
        this.containerInspector = containerInspector;
        this.checkpointLocator = checkpointLocator;
        this.templateLocator = templateLocator;
        this.networkManager = networkManager;
        this.mapper = mapper;
    }

    // Standard eth-docker container UID (services run as the `ethdocker` user). We chown the
    // host-side bind-mounted datadir to this UID so the EL process can write to it.
    private static final int ETH_DOCKER_UID = 10000;

    private static final String EE_SECRET_VOLUME = "jwtsecret";

    @Override
    public DeploymentRef deploy(NodeSpec spec) {
        try {
            EthDockerRef ref = refResolver.resolve(properties.repoUrl(), properties.ref());
            AllocatedPorts ports = portAllocator.allocate();
            String projectName = "node-" + spec.nodeId().value().toString().substring(0, 8);
            Path workdir =
                    Paths.get(properties.rootDir(), spec.nodeId().value().toString(), "eth-docker");
            Path elDataHostPath =
                    Paths.get(properties.rootDir(), spec.nodeId().value().toString(), "data");

            shell.ensureCache(Paths.get(properties.cacheDir()), properties.repoUrl());
            shell.cloneIntoWorkdir(Paths.get(properties.cacheDir()), ref.tag(), workdir);

            Optional<String> elBindYaml =
                    EthDockerEnvFile.elDatadirBindOverrideYaml(spec.clientPair().executionLayer());
            Optional<Path> elDataHostPathForRender =
                    elBindYaml.isPresent() ? Optional.of(elDataHostPath) : Optional.empty();

            Map<String, String> defaults = shell.readDefaultEnv(workdir);
            Optional<URI> checkpointOverride = checkpointLocator.findFor(spec.network());
            Map<String, String> env =
                    EthDockerEnvFile.render(
                            spec,
                            ports,
                            projectName,
                            defaults,
                            checkpointOverride,
                            elDataHostPathForRender);
            shell.writeEnv(workdir, EthDockerEnvFile.serialize(env));
            shell.writeFile(
                    workdir,
                    EthDockerEnvFile.HOST_PORTS_OVERRIDE_FILE,
                    EthDockerEnvFile.hostPortsOverrideYaml());
            shell.writeFile(
                    workdir,
                    EthDockerEnvFile.SHARED_NETWORK_OVERRIDE_FILE,
                    EthDockerEnvFile.sharedNetworkOverrideYaml());

            if (elBindYaml.isPresent()) {
                // Restore from the (network, el-client) template tarball if one is available.
                // Without a template the node falls back to a from-scratch sync — the EL container
                // boots against an empty datadir as before.
                Optional<Path> template =
                        templateLocator.findTemplate(
                                spec.network(), spec.clientPair().executionLayer());
                if (template.isPresent()) {
                    shell.extractTarballZstd(template.get(), elDataHostPath);
                }
                // Single chown -R after the optional extraction so the EL container UID can write
                // to the bind mount whether or not a template was applied.
                shell.ensureDataDir(elDataHostPath, ETH_DOCKER_UID);
                shell.writeFile(
                        workdir, EthDockerEnvFile.EL_DATADIR_BIND_OVERRIDE_FILE, elBindYaml.get());
            }

            networkManager.ensureSharedNetworkExists(EthDockerEnvFile.SHARED_NETWORK_NAME);

            shell.ensureVolumeOwnership(projectName, EE_SECRET_VOLUME, ETH_DOCKER_UID);
            shell.runEthdUp(workdir);

            DeploymentPayload payload =
                    new DeploymentPayload(
                            workdir.toString(),
                            projectName,
                            ports,
                            ref,
                            elBindYaml.isPresent() ? elDataHostPath.toString() : null);
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
            if (payload.elDataHostPath() != null) {
                shell.removeDataDir(Paths.get(payload.elDataHostPath()));
            }
            Path nodeRoot = workdir.getParent();
            if (nodeRoot != null
                    && nodeRoot.getParent() != null
                    && nodeRoot.getParent().equals(Paths.get(properties.rootDir()))) {
                shell.removeNodeRoot(nodeRoot);
            }
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
                new JsonRpcEndpoint(
                        URI.create(
                                "http://"
                                        + properties.publicHost()
                                        + ":"
                                        + payload.ports().elRpcPort())));
    }

    @Override
    public Optional<JsonRpcEndpoint> internalEndpointFor(DeploymentRef ref) {
        DeploymentPayload payload = deserialize(ref);
        return Optional.of(
                new JsonRpcEndpoint(URI.create("http://127.0.0.1:" + payload.ports().elRpcPort())));
    }

    @Override
    public boolean canRestart(DeploymentRef ref) {
        DeploymentPayload payload = deserialize(ref);
        Path workdir = Paths.get(payload.workdir());
        // The .env we wrote at deploy time is the marker of an alive workdir: present means
        // `docker compose up` can pick it up, missing means the workdir was nuked and a
        // fresh deploy is required.
        return Files.isDirectory(workdir) && Files.isRegularFile(workdir.resolve(".env"));
    }

    @Override
    public void restart(DeploymentRef ref) {
        DeploymentPayload payload = deserialize(ref);
        Path workdir = Paths.get(payload.workdir());
        try {
            shell.runEthdUp(workdir);
        } catch (IOException e) {
            throw new IllegalStateException("eth-docker restart failed", e);
        }
    }

    @Override
    public void applyOptionsChange(DeploymentRef ref, NodeSpec newSpec) {
        DeploymentPayload payload = deserialize(ref);
        Path workdir = Paths.get(payload.workdir());
        try {
            Optional<Path> elDataHostPathForRender =
                    payload.elDataHostPath() == null
                            ? Optional.empty()
                            : Optional.of(Paths.get(payload.elDataHostPath()));
            Map<String, String> defaults = shell.readDefaultEnv(workdir);
            Optional<URI> checkpointOverride = checkpointLocator.findFor(newSpec.network());
            Map<String, String> env =
                    EthDockerEnvFile.render(
                            newSpec,
                            payload.ports(),
                            payload.composeProjectName(),
                            defaults,
                            checkpointOverride,
                            elDataHostPathForRender);
            shell.writeEnv(workdir, EthDockerEnvFile.serialize(env));
            // --remove-orphans is the whole point of this method: when validator or mev-boost is
            // toggled off, COMPOSE_FILE drops the corresponding yml and we need docker compose to
            // tear down the now-orphaned services in place. The existing EL/CL containers are
            // left untouched because their config didn't change.
            shell.runEthdUpRemoveOrphans(workdir);
        } catch (IOException e) {
            throw new IllegalStateException("eth-docker applyOptionsChange failed", e);
        }
    }

    @Override
    public Optional<URI> internalClRestEndpointFor(DeploymentRef ref) {
        DeploymentPayload payload = deserialize(ref);
        return Optional.of(URI.create("http://127.0.0.1:" + payload.ports().clRestPort()));
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

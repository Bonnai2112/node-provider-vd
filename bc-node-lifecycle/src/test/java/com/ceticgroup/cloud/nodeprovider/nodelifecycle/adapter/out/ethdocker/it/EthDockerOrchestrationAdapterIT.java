package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ContainerInspector;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.DockerJavaContainerInspector;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.DockerJavaNetworkManager;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerOrchestrationAdapter;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerProperties;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerRefResolver;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.FsElDatadirTemplateLocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.PortAllocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ProcessEthdShellRunner;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ProcessGitLsRemoteClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.CheckpointSyncSourceLocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("eth-docker")
class EthDockerOrchestrationAdapterIT {

    @Test
    void deploy_should_bringUpHoodiStackThenTearDown(@TempDir Path tmp) {
        assumeTrue(dockerAvailable(), "Docker engine not reachable; skipping eth-docker IT");

        EthDockerProperties props =
                new EthDockerProperties(
                        "https://github.com/ethstaker/eth-docker.git",
                        "v26.4.1",
                        tmp.resolve("nodes").toString(),
                        tmp.resolve("cache").toString(),
                        tmp.resolve("cache/sha").toString(),
                        tmp.resolve("templates").toString());

        DockerClient docker = newDockerClient();
        ContainerInspector inspector = new DockerJavaContainerInspector(docker);

        CheckpointSyncSourceLocator noLocalLeader = network -> Optional.empty();
        // No template directory populated → locator returns empty → from-scratch sync.
        FsElDatadirTemplateLocator templateLocator =
                new FsElDatadirTemplateLocator(Path.of(props.templatesDir()));
        EthDockerOrchestrationAdapter adapter =
                new EthDockerOrchestrationAdapter(
                        props,
                        new PortAllocator(),
                        new EthDockerRefResolver(
                                new ProcessGitLsRemoteClient(), Path.of(props.shaCacheFile())),
                        new ProcessEthdShellRunner(),
                        inspector,
                        noLocalLeader,
                        templateLocator,
                        new DockerJavaNetworkManager(docker),
                        new ObjectMapper());

        NodeSpec spec =
                new NodeSpec(
                        new NodeId(UUID.randomUUID()),
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults());

        DeploymentRef ref = adapter.deploy(spec);
        try {
            RuntimeStatus status = adapter.getDeploymentStatus(ref);
            assertThat(status).isInstanceOf(RuntimeStatus.Healthy.class);
        } finally {
            adapter.tearDown(ref);
        }
    }

    private static boolean dockerAvailable() {
        try (Socket s = new Socket()) {
            DefaultDockerClientConfig cfg =
                    DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            String host = cfg.getDockerHost().toString();
            return host != null && !host.isBlank();
        } catch (IOException e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static DockerClient newDockerClient() {
        DefaultDockerClientConfig cfg =
                DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        return DockerClientImpl.getInstance(
                cfg,
                new ZerodepDockerHttpClient.Builder()
                        .dockerHost(cfg.getDockerHost())
                        .sslConfig(cfg.getSSLConfig())
                        .build());
    }
}

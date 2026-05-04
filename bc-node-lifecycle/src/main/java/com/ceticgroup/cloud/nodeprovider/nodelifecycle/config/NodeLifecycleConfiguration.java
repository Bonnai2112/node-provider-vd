package com.ceticgroup.cloud.nodeprovider.nodelifecycle.config;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ContainerInspector;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.DockerJavaContainerInspector;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerOrchestrationAdapter;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerProperties;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerRefResolver;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthdShellRunner;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.GitLsRemoteClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.PortAllocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ProcessEthdShellRunner;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ProcessGitLsRemoteClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.jsonrpc.HttpBlockchainProbeAdapter;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GetNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ReconcileNodeStatusUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.GetNodeService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ProvisionNodeService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ReconcileNodeStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.net.http.HttpClient;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EthDockerProperties.class)
public class NodeLifecycleConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    Executor nodeProvisionExecutor() {
        return Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "node-provision");
                    t.setDaemon(true);
                    return t;
                });
    }

    @Bean
    ProvisionNodeUseCase provisionNodeUseCase(
            NodeRepository repository,
            DomainEventPublisher publisher,
            NodeOrchestrationPort orchestration,
            @Qualifier("nodeProvisionExecutor") Executor executor) {
        return new ProvisionNodeService(repository, publisher, orchestration, executor);
    }

    @Bean
    GetNodeUseCase getNodeUseCase(NodeRepository repository) {
        return new GetNodeService(repository);
    }

    @Bean
    ReconcileNodeStatusUseCase reconcileNodeStatusUseCase(
            NodeRepository repository,
            NodeOrchestrationPort orchestration,
            BlockchainProbePort probe,
            DomainEventPublisher publisher) {
        return new ReconcileNodeStatusService(repository, orchestration, probe, publisher);
    }

    @Bean
    PortAllocator portAllocator() {
        return new PortAllocator();
    }

    @Bean
    GitLsRemoteClient gitLsRemoteClient() {
        return new ProcessGitLsRemoteClient();
    }

    @Bean
    EthDockerRefResolver ethDockerRefResolver(
            GitLsRemoteClient client, EthDockerProperties properties) {
        return new EthDockerRefResolver(client, Paths.get(properties.shaCacheFile()));
    }

    @Bean
    EthdShellRunner ethdShellRunner() {
        return new ProcessEthdShellRunner();
    }

    @Bean
    DockerClient dockerClient() {
        DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient http =
                new ZerodepDockerHttpClient.Builder()
                        .dockerHost(config.getDockerHost())
                        .sslConfig(config.getSSLConfig())
                        .build();
        return DockerClientImpl.getInstance(config, http);
    }

    @Bean
    ContainerInspector containerInspector(DockerClient dockerClient) {
        return new DockerJavaContainerInspector(dockerClient);
    }

    @Bean
    HttpClient probeHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Bean
    BlockchainProbePort blockchainProbePort(HttpClient probeHttpClient, ObjectMapper mapper) {
        return new HttpBlockchainProbeAdapter(probeHttpClient, mapper);
    }

    @Bean
    NodeOrchestrationPort nodeOrchestrationPort(
            EthDockerProperties properties,
            PortAllocator portAllocator,
            EthDockerRefResolver refResolver,
            EthdShellRunner shell,
            ContainerInspector containerInspector,
            ObjectMapper mapper) {
        return new EthDockerOrchestrationAdapter(
                properties, portAllocator, refResolver, shell, containerInspector, mapper);
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.config;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ContainerInspector;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.DepositCliKeyGenerator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.DockerJavaContainerInspector;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.DockerJavaNetworkManager;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.DockerNetworkManager;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerOrchestrationAdapter;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerProperties;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthDockerRefResolver;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthdShellRunner;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.EthdValidatorKeyImporter;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.GitLsRemoteClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.LocalKeystoreArchiver;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.PortAllocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ProcessEthdShellRunner;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.ProcessGitLsRemoteClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker.RepositoryCheckpointSyncSourceLocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.jsonrpc.HttpBlockchainProbeAdapter;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.DownloadValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GetNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ImportValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ListNodesByOwnerUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ListValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ReconcileNodeStatusUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.TerminateNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.CheckpointSyncSourceLocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyArchiverPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyGeneratorPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyImporterPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.DownloadValidatorKeysService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.GenerateValidatorKeysService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.GetNodeService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ImportValidatorKeysService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ListNodesByOwnerService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ListValidatorKeysService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ProvisionNodeService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ReconcileNodeStatusService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.TerminateNodeService;
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
    ListNodesByOwnerUseCase listNodesByOwnerUseCase(NodeRepository repository) {
        return new ListNodesByOwnerService(repository);
    }

    @Bean
    TerminateNodeUseCase terminateNodeUseCase(
            NodeRepository repository,
            NodeOrchestrationPort orchestration,
            DomainEventPublisher publisher,
            @Qualifier("nodeProvisionExecutor") Executor executor) {
        return new TerminateNodeService(repository, orchestration, publisher, executor);
    }

    @Bean
    ValidatorKeyImporterPort validatorKeyImporterPort(EthdShellRunner shell, ObjectMapper mapper) {
        return new EthdValidatorKeyImporter(shell, mapper);
    }

    @Bean
    ListValidatorKeysUseCase listValidatorKeysUseCase(
            NodeRepository nodes, ValidatorKeyRepository keys) {
        return new ListValidatorKeysService(nodes, keys);
    }

    @Bean
    ImportValidatorKeysUseCase importValidatorKeysUseCase(
            NodeRepository nodes, ValidatorKeyRepository keys, ValidatorKeyImporterPort importer) {
        return new ImportValidatorKeysService(nodes, keys, importer);
    }

    @Bean
    ValidatorKeyGeneratorPort validatorKeyGeneratorPort(ObjectMapper mapper) {
        return new DepositCliKeyGenerator(mapper);
    }

    @Bean
    GenerateValidatorKeysUseCase generateValidatorKeysUseCase(
            NodeRepository nodes,
            ValidatorKeyRepository keys,
            ValidatorKeyGeneratorPort generator,
            ValidatorKeyImporterPort importer) {
        return new GenerateValidatorKeysService(nodes, keys, generator, importer);
    }

    @Bean
    ValidatorKeyArchiverPort validatorKeyArchiverPort(ObjectMapper mapper) {
        return new LocalKeystoreArchiver(mapper);
    }

    @Bean
    DownloadValidatorKeysUseCase downloadValidatorKeysUseCase(
            NodeRepository nodes, ValidatorKeyArchiverPort archiver) {
        return new DownloadValidatorKeysService(nodes, archiver);
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
    DockerNetworkManager dockerNetworkManager(DockerClient dockerClient) {
        return new DockerJavaNetworkManager(dockerClient);
    }

    @Bean
    CheckpointSyncSourceLocator checkpointSyncSourceLocator(NodeRepository nodes) {
        return new RepositoryCheckpointSyncSourceLocator(nodes);
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
            CheckpointSyncSourceLocator checkpointLocator,
            DockerNetworkManager networkManager,
            ObjectMapper mapper) {
        return new EthDockerOrchestrationAdapter(
                properties,
                portAllocator,
                refResolver,
                shell,
                containerInspector,
                checkpointLocator,
                networkManager,
                mapper);
    }
}

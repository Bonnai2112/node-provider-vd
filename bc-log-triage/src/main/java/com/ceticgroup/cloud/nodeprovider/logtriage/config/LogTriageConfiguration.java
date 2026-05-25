package com.ceticgroup.cloud.nodeprovider.logtriage.config;

import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.in.web.IncidentQueue;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.in.web.IncidentQueueWorker;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.claude.ClaudeFixSuggestionAdapter;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.container.ContainerTestAdapter;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.git.GitGraphContextAdapter;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.gitlab.GitLabMergeRequestAdapter;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.loki.LokiLogRetrievalAdapter;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.safety.ConfigFeatureToggle;
import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.safety.InMemoryDailyMergeRequestQuota;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.in.HandleIncidentUseCase;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.CodeContextPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FeatureTogglePort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixSuggestionPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixVerificationPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.LogRetrievalPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestQuotaPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.service.TriageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.gitlab4j.api.GitLabApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    SafetyProperties.class,
    LokiProperties.class,
    GitWorkspaceProperties.class,
    GitLabProperties.class,
    ContainerTestProperties.class,
    LogTriageWebhookProperties.class
})
public class LogTriageConfiguration {

    @Bean
    Clock logTriageClock() {
        return Clock.systemUTC();
    }

    @Bean
    ObjectMapper logTriageObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    HttpClient logTriageHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Bean
    LogRetrievalPort logRetrievalPort(
            HttpClient logTriageHttpClient,
            ObjectMapper logTriageObjectMapper,
            LokiProperties properties) {
        return new LokiLogRetrievalAdapter(logTriageHttpClient, logTriageObjectMapper, properties);
    }

    @Bean
    CodeContextPort codeContextPort(GitWorkspaceProperties properties) {
        return new GitGraphContextAdapter(properties);
    }

    @Bean
    ChatClient logTriageChatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    FixSuggestionPort fixSuggestionPort(ChatClient logTriageChatClient) {
        return new ClaudeFixSuggestionAdapter(logTriageChatClient);
    }

    @Bean
    DockerClient logTriageDockerClient() {
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
    FixVerificationPort fixVerificationPort(
            DockerClient logTriageDockerClient, ContainerTestProperties properties) {
        return new ContainerTestAdapter(logTriageDockerClient, properties);
    }

    @Bean
    GitLabApi gitLabApi(GitLabProperties properties) {
        return new GitLabApi(properties.baseUrl(), properties.token());
    }

    @Bean
    MergeRequestPort mergeRequestPort(GitLabApi gitLabApi, GitLabProperties properties) {
        return new GitLabMergeRequestAdapter(gitLabApi, properties);
    }

    @Bean
    FeatureTogglePort featureTogglePort(SafetyProperties properties) {
        return new ConfigFeatureToggle(properties);
    }

    @Bean
    MergeRequestQuotaPort mergeRequestQuotaPort(SafetyProperties properties, Clock logTriageClock) {
        return new InMemoryDailyMergeRequestQuota(properties, logTriageClock);
    }

    @Bean
    HandleIncidentUseCase handleIncidentUseCase(
            LogRetrievalPort logRetrieval,
            CodeContextPort codeContext,
            FixSuggestionPort fixSuggestion,
            FixVerificationPort fixVerification,
            MergeRequestPort mergeRequest,
            FeatureTogglePort featureToggle,
            MergeRequestQuotaPort quota,
            SafetyProperties safety) {
        return new TriageService(
                logRetrieval,
                codeContext,
                fixSuggestion,
                fixVerification,
                mergeRequest,
                featureToggle,
                quota,
                safety.toPathAllowlist(),
                safety.toConfidenceThreshold());
    }

    @Bean
    IncidentQueue incidentQueue(LogTriageWebhookProperties properties) {
        return new IncidentQueue(properties.queueCapacity());
    }

    @Bean
    IncidentQueueWorker incidentQueueWorker(
            IncidentQueue queue, HandleIncidentUseCase useCase, MeterRegistry meterRegistry) {
        return new IncidentQueueWorker(queue, useCase, meterRegistry);
    }
}

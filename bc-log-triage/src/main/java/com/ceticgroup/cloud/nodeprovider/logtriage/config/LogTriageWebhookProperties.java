package com.ceticgroup.cloud.nodeprovider.logtriage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.log-triage.webhook")
public record LogTriageWebhookProperties(String token, int queueCapacity) {

    public LogTriageWebhookProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
    }
}

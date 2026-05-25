package com.ceticgroup.cloud.nodeprovider.logtriage.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.log-triage.loki")
public record LokiProperties(
        URI baseUrl, Duration windowBefore, Duration windowAfter, int maxLines) {

    public LokiProperties {
        if (baseUrl == null) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (windowBefore == null || windowBefore.isNegative()) {
            throw new IllegalArgumentException("windowBefore must be a non-negative duration");
        }
        if (windowAfter == null || windowAfter.isNegative()) {
            throw new IllegalArgumentException("windowAfter must be a non-negative duration");
        }
        if (maxLines <= 0) {
            throw new IllegalArgumentException("maxLines must be > 0");
        }
    }
}

package com.ceticgroup.cloud.nodeprovider.logtriage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.log-triage.verification")
public record ContainerTestProperties(
        String repoUrl, String baseBranch, String mavenImage, int timeoutSeconds) {

    public ContainerTestProperties {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl is required");
        }
        if (baseBranch == null || baseBranch.isBlank()) {
            throw new IllegalArgumentException("baseBranch is required");
        }
        if (mavenImage == null || mavenImage.isBlank()) {
            throw new IllegalArgumentException("mavenImage is required");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be > 0");
        }
    }
}

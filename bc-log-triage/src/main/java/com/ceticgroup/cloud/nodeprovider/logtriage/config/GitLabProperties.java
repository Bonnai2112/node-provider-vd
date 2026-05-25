package com.ceticgroup.cloud.nodeprovider.logtriage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.log-triage.gitlab")
public record GitLabProperties(
        String baseUrl, String token, String projectPath, String baseBranch) {

    public GitLabProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("projectPath is required");
        }
        if (baseBranch == null || baseBranch.isBlank()) {
            throw new IllegalArgumentException("baseBranch is required");
        }
    }
}

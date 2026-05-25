package com.ceticgroup.cloud.nodeprovider.logtriage.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.log-triage.git")
public record GitWorkspaceProperties(String repoRootPath, int maxSnippets) {

    public GitWorkspaceProperties {
        if (repoRootPath == null || repoRootPath.isBlank()) {
            throw new IllegalArgumentException("repoRootPath is required");
        }
        if (maxSnippets <= 0) {
            throw new IllegalArgumentException("maxSnippets must be > 0");
        }
    }

    public Path repoRoot() {
        return Paths.get(repoRootPath);
    }
}

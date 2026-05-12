package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.platform.eth-docker")
public record EthDockerProperties(
        String repoUrl,
        String ref,
        String rootDir,
        String cacheDir,
        String shaCacheFile,
        String templatesDir) {

    public EthDockerProperties {
        if (repoUrl == null || repoUrl.isBlank()) {
            repoUrl = "https://github.com/ethstaker/eth-docker.git";
        }
        if (ref == null || ref.isBlank()) {
            ref = "v26.4.1";
        }
        if (rootDir == null || rootDir.isBlank()) {
            rootDir = "/var/lib/platform/nodes";
        }
        if (cacheDir == null || cacheDir.isBlank()) {
            cacheDir = "/var/lib/platform/cache";
        }
        if (shaCacheFile == null || shaCacheFile.isBlank()) {
            shaCacheFile = "/var/lib/platform/cache/eth-docker-sha";
        }
        if (templatesDir == null || templatesDir.isBlank()) {
            templatesDir = "/var/lib/platform/templates";
        }
    }
}

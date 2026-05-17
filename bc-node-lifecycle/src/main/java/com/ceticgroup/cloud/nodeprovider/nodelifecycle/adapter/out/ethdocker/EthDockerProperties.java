package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.platform.eth-docker")
public record EthDockerProperties(
        String repoUrl,
        String ref,
        String rootDir,
        String cacheDir,
        String shaCacheFile,
        String templatesDir,
        String publicHost,
        String depositCliImage) {

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
        // Host announced in the JSON-RPC URL handed to API consumers, and bound on the
        // host side via HOST_IP in the compose override. Default keeps local dev working;
        // operators set the VM public IP via the PLATFORM_PUBLIC_HOST env var.
        if (publicHost == null || publicHost.isBlank()) {
            publicHost = "localhost";
        }
        // Pinned to avoid silent upgrades and to let us pre-pull at startup. Bump after
        // validating a new release against the keystore/import flow.
        if (depositCliImage == null || depositCliImage.isBlank()) {
            depositCliImage = "ghcr.io/ethstaker/ethstaker-deposit-cli:v1.3.0";
        }
    }
}

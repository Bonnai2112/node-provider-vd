package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class EthDockerRefResolver {

    private final GitLsRemoteClient client;
    private final Path cacheFile;

    public EthDockerRefResolver(GitLsRemoteClient client, Path cacheFile) {
        this.client = client;
        this.cacheFile = cacheFile;
    }

    public EthDockerRef resolve(String repoUrl, String tag) {
        Optional<String> fresh = client.resolveSha(repoUrl, tag);
        if (fresh.isPresent()) {
            writeCache(tag, fresh.get());
            return new EthDockerRef(tag, fresh.get());
        }
        return readCache(tag)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "could not resolve "
                                                + tag
                                                + " from remote and no cache found at "
                                                + cacheFile));
    }

    private void writeCache(String tag, String sha) {
        try {
            if (cacheFile.getParent() != null) {
                Files.createDirectories(cacheFile.getParent());
            }
            Files.writeString(cacheFile, tag + " " + sha, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // cache write best-effort; log via stderr to keep adapter free of logger deps
            System.err.println("warn: could not write eth-docker SHA cache: " + e.getMessage());
        }
    }

    private Optional<EthDockerRef> readCache(String expectedTag) {
        if (!Files.exists(cacheFile)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(cacheFile, StandardCharsets.UTF_8).trim();
            int sp = content.indexOf(' ');
            if (sp <= 0 || sp >= content.length() - 1) {
                return Optional.empty();
            }
            String tag = content.substring(0, sp);
            String sha = content.substring(sp + 1);
            if (!expectedTag.equals(tag)) {
                return Optional.empty();
            }
            return Optional.of(new EthDockerRef(tag, sha));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}

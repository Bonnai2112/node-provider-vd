package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ProcessGitLsRemoteClient implements GitLsRemoteClient {

    @Override
    public Optional<String> resolveSha(String repoUrl, String ref) {
        try {
            ProcessBuilder pb =
                    new ProcessBuilder("git", "ls-remote", repoUrl, ref).redirectErrorStream(true);
            Process p = pb.start();
            String firstLine;
            try (BufferedReader r =
                    new BufferedReader(
                            new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                firstLine = r.readLine();
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return Optional.empty();
            }
            if (p.exitValue() != 0 || firstLine == null) {
                return Optional.empty();
            }
            int tab = firstLine.indexOf('\t');
            if (tab <= 0) {
                return Optional.empty();
            }
            return Optional.of(firstLine.substring(0, tab).trim());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }
}

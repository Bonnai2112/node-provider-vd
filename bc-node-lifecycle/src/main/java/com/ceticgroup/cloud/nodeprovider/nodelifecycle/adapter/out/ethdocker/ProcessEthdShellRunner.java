package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ProcessEthdShellRunner implements EthdShellRunner {

    private static final long PROCESS_TIMEOUT_SECONDS = 600;

    @Override
    public void ensureCache(Path cacheDir, String repoUrl) throws IOException {
        Files.createDirectories(cacheDir.getParent() == null ? cacheDir : cacheDir.getParent());
        Path bare = cacheDir.resolve("eth-docker.git");
        if (Files.exists(bare)) {
            run(cacheDir, "git", "-C", bare.toString(), "fetch", "--tags", "--prune");
            return;
        }
        Files.createDirectories(cacheDir);
        run(cacheDir, "git", "clone", "--bare", repoUrl, bare.toString());
    }

    @Override
    public void cloneIntoWorkdir(Path cacheDir, String tag, Path workdir) throws IOException {
        Path bare = cacheDir.resolve("eth-docker.git");
        Files.createDirectories(workdir.getParent());
        run(
                cacheDir,
                "git",
                "clone",
                "--shared",
                "--branch",
                tag,
                bare.toString(),
                workdir.toString());
    }

    @Override
    public void writeEnv(Path workdir, String envContent) throws IOException {
        Files.createDirectories(workdir);
        Files.writeString(workdir.resolve(".env"), envContent, StandardCharsets.UTF_8);
    }

    @Override
    public void runEthdUp(Path workdir) throws IOException {
        run(workdir, "./ethd", "up", "--non-interactive");
    }

    @Override
    public void runEthdDown(Path workdir) throws IOException {
        run(workdir, "./ethd", "down", "--non-interactive");
    }

    @Override
    public void runEthdTerminate(Path workdir) throws IOException {
        run(workdir, "./ethd", "terminate", "--non-interactive");
    }

    @Override
    public void removeWorkdir(Path workdir) throws IOException {
        if (!Files.exists(workdir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(workdir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
        }
    }

    private static void run(Path workdir, String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command).directory(workdir.toFile()).inheritIO();
        Process p = pb.start();
        try {
            if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("command timed out: " + String.join(" ", command));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("command interrupted", e);
        }
        if (p.exitValue() != 0) {
            throw new IOException(
                    "command failed with exit " + p.exitValue() + ": " + String.join(" ", command));
        }
    }
}

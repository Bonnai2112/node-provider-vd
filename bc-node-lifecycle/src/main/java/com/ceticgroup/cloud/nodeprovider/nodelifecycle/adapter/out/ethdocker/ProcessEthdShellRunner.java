package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ProcessEthdShellRunner implements EthdShellRunner {

    private static final long PROCESS_TIMEOUT_SECONDS = 600;

    @Override
    public void ensureCache(Path cacheDir, String repoUrl) throws IOException {
        Files.createDirectories(cacheDir.getParent() == null ? cacheDir : cacheDir.getParent());
        Path bare = cacheDir.resolve("eth-docker.git");
        if (Files.exists(bare)) {
            run(cacheDir, null, "git", "-C", bare.toString(), "fetch", "--tags", "--prune");
            return;
        }
        Files.createDirectories(cacheDir);
        run(cacheDir, null, "git", "clone", "--bare", repoUrl, bare.toString());
    }

    @Override
    public void cloneIntoWorkdir(Path cacheDir, String tag, Path workdir) throws IOException {
        Path bare = cacheDir.resolve("eth-docker.git");
        Files.createDirectories(workdir.getParent());
        run(
                cacheDir,
                null,
                "git",
                "clone",
                "--shared",
                "--branch",
                tag,
                bare.toString(),
                workdir.toString());
    }

    @Override
    public Map<String, String> readDefaultEnv(Path workdir) throws IOException {
        Path defaultEnv = workdir.resolve("default.env");
        Map<String, String> env = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(defaultEnv, StandardCharsets.UTF_8);
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i++;
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                i++;
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1);
            // eth-docker's default.env declares some values with multi-line quoted strings
            // (e.g. MEV_RELAYS="\n<url>,\n<url>\n"). Stitch the value back together so the
            // serialized .env we hand to docker-compose remains a valid quoted block.
            if (value.startsWith("\"") && !value.substring(1).contains("\"")) {
                StringBuilder sb = new StringBuilder(value);
                i++;
                while (i < lines.size()) {
                    String next = lines.get(i);
                    sb.append('\n').append(next);
                    if (next.contains("\"")) {
                        break;
                    }
                    i++;
                }
                value = sb.toString();
            }
            env.put(key, value);
            i++;
        }
        return env;
    }

    @Override
    public void writeEnv(Path workdir, String envContent) throws IOException {
        Files.createDirectories(workdir);
        Files.writeString(workdir.resolve(".env"), envContent, StandardCharsets.UTF_8);
    }

    @Override
    public void writeFile(Path workdir, String name, String content) throws IOException {
        Files.createDirectories(workdir);
        Files.writeString(workdir.resolve(name), content, StandardCharsets.UTF_8);
    }

    @Override
    public void runEthdUp(Path workdir) throws IOException {
        run(workdir, null, "./ethd", "up");
    }

    @Override
    public void runEthdUpRemoveOrphans(Path workdir) throws IOException {
        // ethd passes trailing args through to `docker compose up`, so --remove-orphans drops
        // containers whose service no longer appears in COMPOSE_FILE (e.g. validator / mev-boost
        // after a disable).
        run(workdir, null, "./ethd", "up", "--remove-orphans");
    }

    @Override
    public void runEthdDown(Path workdir) throws IOException {
        run(workdir, null, "./ethd", "down");
    }

    @Override
    public void runEthdTerminate(Path workdir) throws IOException {
        // ethd terminate has a hard `read -rp` confirmation prompt with no --non-interactive
        // support; feed "yes\n" on stdin to auto-confirm the destructive volume removal.
        run(workdir, "yes\n", "./ethd", "terminate");
    }

    @Override
    public void runEthdKeysImport(Path workdir, String keystorePassword) throws IOException {
        // eth-docker v26.4.1 has a bug in ethd's `keys import --non-interactive` parser:
        // it appends "--interactive" (instead of "--non-interactive") to the args passed
        // to the validator-keys container's keymanager.sh. As a result keymanager.sh runs
        // in interactive mode and prompts for the password on stdin instead of reading
        // KEYSTORE_PASSWORD from env. We patch the cloned ethd in place to fix the typo.
        patchKeysImportNonInteractiveBug(workdir);
        runCapturing(
                workdir,
                null,
                Map.of("KEYSTORE_PASSWORD", keystorePassword),
                "./ethd",
                "keys",
                "import",
                "--non-interactive");
    }

    private static void patchKeysImportNonInteractiveBug(Path workdir) throws IOException {
        Path ethd = workdir.resolve("ethd");
        if (!Files.isRegularFile(ethd)) {
            return;
        }
        String content = Files.readString(ethd, StandardCharsets.UTF_8);
        String buggy = "__keys_args+=\"${__keys_args:+ }--interactive\"";
        String fixed = "__keys_args+=\"${__keys_args:+ }--non-interactive\"";
        if (content.contains(buggy)) {
            Files.writeString(ethd, content.replace(buggy, fixed), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void removeWorkdir(Path workdir) throws IOException {
        deleteRecursively(workdir);
    }

    @Override
    public void ensureDataDir(Path dataDir, int ownerUid) throws IOException {
        Files.createDirectories(dataDir);
        // eth-docker images run the EL process as a non-root UID (10000 for Geth/Erigon/...,
        // 10002 for Nethermind, 12000, ...) so the container can't write to the bind-mounted
        // host directory unless ownership matches. chown(2) toward an UID outside the caller's
        // own requires CAP_CHOWN, so we delegate via sudo. The non-interactive flag (-n) fails
        // fast if the sudoers rule is missing or doesn't match (rather than blocking on a
        // password prompt). Required sudoers entry — see bc-node-lifecycle/README.md:
        //   <backend-user> ALL=(root) NOPASSWD: /usr/bin/chown -R *\:*
        // /var/lib/platform/nodes/*/data
        run(
                dataDir.getParent() == null ? dataDir : dataDir.getParent(),
                null,
                "sudo",
                "-n",
                "/usr/bin/chown",
                "-R",
                ownerUid + ":" + ownerUid,
                dataDir.toString());
    }

    @Override
    public void removeDataDir(Path dataDir) throws IOException {
        deleteRecursively(dataDir);
    }

    @Override
    public void removeNodeRoot(Path nodeRoot) throws IOException {
        if (!Files.exists(nodeRoot)) {
            return;
        }
        // The data dir is chowned to UID 10000 at deploy time (see ensureDataDir), so a
        // best-effort Files.walk delete from the backend user silently fails on those
        // entries. We delegate to root via sudo to make the cleanup actually effective.
        // Required sudoers entry:
        //   <backend-user> ALL=(root) NOPASSWD: /bin/rm -rf /var/lib/platform/nodes/*
        run(
                nodeRoot.getParent() == null ? nodeRoot : nodeRoot.getParent(),
                null,
                "sudo",
                "-n",
                "/bin/rm",
                "-rf",
                nodeRoot.toString());
    }

    @Override
    public void extractTarballZstd(Path tarball, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        // GNU tar's --use-compress-program lets us decompress with zstd without resorting to a
        // shell pipe (and the associated quoting hazards). tar reads the archive itself.
        run(
                targetDir,
                null,
                "tar",
                "--use-compress-program=zstd",
                "-xf",
                tarball.toString(),
                "-C",
                targetDir.toString());
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
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

    private static void runCapturing(
            Path workdir, String stdin, Map<String, String> env, String... command)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command).directory(workdir.toFile());
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }
        pb.redirectErrorStream(true);
        if (stdin == null) {
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }
        Process p = pb.start();
        if (stdin != null) {
            try (var os = p.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader r =
                new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                // Tee to JVM stdout so the operator sees ethd progress in real time
                // (image builds, docker pulls...) instead of a hung-looking request.
                System.out.println(line);
                out.append(line).append('\n');
            }
        }
        try {
            if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException(
                        "command timed out: " + String.join(" ", command) + "\n" + out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("command interrupted: " + out, e);
        }
        if (p.exitValue() != 0) {
            throw new IOException(
                    "command failed with exit "
                            + p.exitValue()
                            + ": "
                            + String.join(" ", command)
                            + "\noutput:\n"
                            + out);
        }
    }

    private static void run(Path workdir, String stdin, String... command) throws IOException {
        ProcessBuilder pb =
                new ProcessBuilder(command)
                        .directory(workdir.toFile())
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT);
        if (stdin == null) {
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }
        Process p = pb.start();
        if (stdin != null) {
            try (var os = p.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
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

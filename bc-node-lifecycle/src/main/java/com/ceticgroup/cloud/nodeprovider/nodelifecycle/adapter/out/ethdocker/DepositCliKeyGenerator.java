package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyGeneratorPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositCliKeyGenerator implements ValidatorKeyGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(DepositCliKeyGenerator.class);
    private static final String CONTAINER_DATA_DIR = "/app/data";
    private static final String ETH_REL = ".eth";
    private static final String VALIDATOR_KEYS_REL = ".eth/validator_keys";
    private static final long PROCESS_TIMEOUT_SECONDS = 300;

    private final ObjectMapper mapper;
    private final String image;
    // Resolved once via `id -u`/`id -g`; reused for every generation. Volatile so a
    // concurrent caller observes the fully-initialized value.
    private volatile String cachedUidGid;

    public DepositCliKeyGenerator(ObjectMapper mapper, String image) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.image = Objects.requireNonNull(image, "image");
    }

    @Override
    public GeneratedKeys generate(
            DeploymentRef ref, String chain, int count, String withdrawalAddress, String password) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(withdrawalAddress, "withdrawalAddress");
        Objects.requireNonNull(password, "password");
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        Path workdir = workdirOf(ref);
        Path ethDir = workdir.resolve(ETH_REL);
        Path validatorKeysDir = workdir.resolve(VALIDATOR_KEYS_REL);
        try {
            Files.createDirectories(ethDir);
            // Each generation uses a fresh keystore password. Move keystores from previous
            // generations to a backup folder so the subsequent `ethd keys import` only sees
            // the freshly-generated keystores — otherwise old keystores (encrypted with a
            // different password) fail to decrypt and the import errors out.
            archivePreExistingKeystores(validatorKeysDir);
            String stdout = runDepositCli(ethDir, chain, count, withdrawalAddress, password);
            String mnemonic = parseMnemonic(stdout);
            List<String> pubkeys = readNewlyGeneratedPubkeys(validatorKeysDir, List.of());
            return new GeneratedKeys(mnemonic, pubkeys);
        } catch (IOException e) {
            throw new IllegalStateException("deposit-cli generation failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running deposit-cli", e);
        }
    }

    private Path workdirOf(DeploymentRef ref) {
        try {
            JsonNode root = mapper.readTree(ref.payload());
            JsonNode wd = root.get("workdir");
            if (wd == null || !wd.isTextual()) {
                throw new IllegalStateException("deployment ref has no workdir field");
            }
            return Paths.get(wd.asText());
        } catch (IOException e) {
            throw new IllegalStateException("could not parse deployment ref", e);
        }
    }

    private String runDepositCli(
            Path mountSource, String chain, int count, String withdrawalAddress, String password)
            throws IOException, InterruptedException {
        // --language and --non_interactive are GLOBAL options (must precede the subcommand);
        // --mnemonic_language selects the BIP39 wordlist for the subcommand. -i is required
        // for docker to forward stdin. --user maps the container UID/GID to the host user
        // so generated keystores are readable by the calling JVM (otherwise they end up
        // owned by root).
        String uidGid = currentUidGid();
        ProcessBuilder pb =
                new ProcessBuilder(
                        "docker",
                        "run",
                        "--rm",
                        "-i",
                        "--user",
                        uidGid,
                        "-v",
                        mountSource.toAbsolutePath() + ":" + CONTAINER_DATA_DIR,
                        image,
                        "--language=english",
                        "--non_interactive",
                        "new-mnemonic",
                        "--num_validators=" + count,
                        "--chain=" + chain,
                        "--execution_address=" + withdrawalAddress,
                        "--keystore_password=" + password,
                        "--mnemonic_language=english",
                        "--compounding",
                        // Since v1.3.0 the --compounding flag triggers an interactive `amount`
                        // prompt that --non_interactive does not skip; we always deposit the
                        // activation default (32 ETH), so pass it explicitly to avoid blocking.
                        "--amount=32",
                        "--folder",
                        CONTAINER_DATA_DIR);
        pb.redirectErrorStream(true);
        log.info(
                "starting deposit-cli (image={}, count={}, chain={}); container boot + scrypt"
                        + " typically take 5-15s before the first keystore appears",
                image,
                count,
                chain);
        long startedAt = System.currentTimeMillis();
        Process p = pb.start();
        OutputStream stdin = p.getOutputStream();

        StringBuilder out = new StringBuilder();
        AtomicReference<String> capturedMnemonic = new AtomicReference<>();
        // 0x02 (compounding) credentials are selected via the --compounding flag, so the
        // Pectra yes/no prompt no longer fires. The mnemonic confirmation prompt is still
        // emitted (--non_interactive does not skip it), so we echo back the mnemonic
        // captured from stdout when that prompt appears.
        Thread reader =
                new Thread(
                        () -> {
                            try (BufferedReader r =
                                    new BufferedReader(
                                            new InputStreamReader(
                                                    p.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = r.readLine()) != null) {
                                    synchronized (out) {
                                        out.append(line).append('\n');
                                    }
                                    String trimmed = line.trim();
                                    if (capturedMnemonic.get() == null && isMnemonicLine(trimmed)) {
                                        capturedMnemonic.compareAndSet(null, trimmed);
                                    }
                                    if (capturedMnemonic.get() != null
                                            && line.contains("Please type your mnemonic")) {
                                        try {
                                            stdin.write(
                                                    (capturedMnemonic.get() + "\n")
                                                            .getBytes(StandardCharsets.UTF_8));
                                            stdin.flush();
                                        } catch (IOException ignored) {
                                            // process probably exited, give up writing
                                        }
                                    }
                                }
                            } catch (IOException ignored) {
                                // stream closed by process exit
                            }
                        },
                        "deposit-cli-stdout");
        reader.setDaemon(true);
        reader.start();

        if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("deposit-cli timed out after " + PROCESS_TIMEOUT_SECONDS + "s");
        }
        reader.join();
        try {
            stdin.close();
        } catch (IOException ignored) {
            // already closed
        }
        if (p.exitValue() != 0) {
            throw new IOException("deposit-cli exited " + p.exitValue() + ", output:\n" + out);
        }
        log.info(
                "deposit-cli finished in {} ms ({} keystore(s))",
                System.currentTimeMillis() - startedAt,
                count);
        return out.toString();
    }

    private String currentUidGid() {
        String cached = cachedUidGid;
        if (cached != null) {
            return cached;
        }
        // On Linux/macOS we can read the real UID/GID via the JNR-less approach: a quick
        // `id -u` / `id -g` shell call. Falling back to "0:0" (root) is acceptable on Docker
        // Desktop where rootless mounts handle ownership transparently. Result is stable
        // for the JVM's lifetime so we resolve it once and reuse it.
        String resolved;
        try {
            String uid = readSingleLine("id", "-u");
            String gid = readSingleLine("id", "-g");
            resolved = uid + ":" + gid;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            resolved = "0:0";
        }
        cachedUidGid = resolved;
        return resolved;
    }

    private static String readSingleLine(String... command)
            throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader r =
                new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            p.waitFor();
            return line == null ? "" : line.trim();
        }
    }

    private static boolean isMnemonicLine(String line) {
        if (line.isEmpty()) {
            return false;
        }
        String[] words = line.split("\\s+");
        return words.length == 24 && allLowercaseWords(words);
    }

    private static String parseMnemonic(String output) {
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            String[] words = trimmed.split("\\s+");
            if (words.length == 24 && allLowercaseWords(words)) {
                return trimmed;
            }
        }
        throw new IllegalStateException(
                "could not extract 24-word mnemonic from deposit-cli output");
    }

    private static boolean allLowercaseWords(String[] words) {
        for (String w : words) {
            if (w.isEmpty()) {
                return false;
            }
            for (int i = 0; i < w.length(); i++) {
                char c = w.charAt(i);
                if (c < 'a' || c > 'z') {
                    return false;
                }
            }
        }
        return true;
    }

    private static void archivePreExistingKeystores(Path validatorKeysDir) throws IOException {
        if (!Files.isDirectory(validatorKeysDir)) {
            return;
        }
        Path backup =
                validatorKeysDir
                        .resolve("keybackup")
                        .resolve(String.valueOf(Instant.now().toEpochMilli()));
        boolean moved = false;
        try (Stream<Path> walk = Files.list(validatorKeysDir)) {
            for (Path file : walk.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String name = file.getFileName().toString();
                if (name.startsWith("keystore-") && name.endsWith(".json")) {
                    if (!moved) {
                        Files.createDirectories(backup);
                        moved = true;
                    }
                    Files.move(file, backup.resolve(name));
                }
            }
        }
    }

    private List<String> readNewlyGeneratedPubkeys(Path dir, List<String> preExisting)
            throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IOException(
                    "deposit-cli did not create the validator_keys directory at " + dir);
        }
        List<Path> newFiles;
        try (Stream<Path> walk = Files.list(dir)) {
            newFiles =
                    walk.filter(Files::isRegularFile)
                            .filter(
                                    p -> {
                                        String n = p.getFileName().toString();
                                        return n.startsWith("keystore-")
                                                && n.endsWith(".json")
                                                && !preExisting.contains(n);
                                    })
                            .sorted(Comparator.comparing(Path::getFileName))
                            .toList();
        }
        if (newFiles.isEmpty()) {
            throw new IOException("deposit-cli did not produce any new keystore in " + dir);
        }
        List<String> pubkeys = new ArrayList<>(newFiles.size());
        for (Path file : newFiles) {
            JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            JsonNode pk = root.get("pubkey");
            if (pk == null || !pk.isTextual() || pk.asText().isBlank()) {
                throw new IOException(
                        "generated keystore " + file.getFileName() + " missing pubkey field");
            }
            String value = pk.asText();
            pubkeys.add(value.startsWith("0x") ? value : "0x" + value);
        }
        return pubkeys;
    }
}

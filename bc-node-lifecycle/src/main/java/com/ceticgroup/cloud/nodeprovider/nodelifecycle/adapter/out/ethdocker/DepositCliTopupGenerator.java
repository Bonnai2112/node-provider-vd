package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.TopupDepositGeneratorPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositCliTopupGenerator implements TopupDepositGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(DepositCliTopupGenerator.class);
    private static final String CONTAINER_DATA_DIR = "/app/data";
    private static final String ETH_REL = ".eth";
    private static final String VALIDATOR_KEYS_REL = "validator_keys";
    private static final String TOPUPS_REL = "topups";
    private static final long PROCESS_TIMEOUT_SECONDS = 120;

    private final ObjectMapper mapper;
    private final String image;
    private volatile String cachedUidGid;

    public DepositCliTopupGenerator(ObjectMapper mapper, String image) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.image = Objects.requireNonNull(image, "image");
    }

    @Override
    public byte[] generate(
            DeploymentRef ref,
            String chain,
            String pubkey,
            BigDecimal amountEth,
            String keystorePassword) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(pubkey, "pubkey");
        Objects.requireNonNull(amountEth, "amountEth");
        Objects.requireNonNull(keystorePassword, "keystorePassword");

        Path workdir = workdirOf(ref);
        Path ethDir = workdir.resolve(ETH_REL);
        Path validatorKeysDir = ethDir.resolve(VALIDATOR_KEYS_REL);
        if (!Files.isDirectory(validatorKeysDir)) {
            throw new IllegalStateException(
                    "validator_keys directory not found at " + validatorKeysDir);
        }

        try {
            Path keystoreFile = findKeystoreFor(validatorKeysDir, pubkey);
            String withdrawalAddress = findWithdrawalAddressFor(validatorKeysDir, pubkey);
            // Output goes to a fresh per-call topups/<timestamp> subfolder. This isolates the
            // partial-deposit output from the initial-deposit deposit_data files scanned by the
            // download endpoint — otherwise the launchpad would try to deposit again for an
            // already-active validator.
            Path topupDir =
                    ethDir.resolve(TOPUPS_REL)
                            .resolve(String.valueOf(Instant.now().toEpochMilli()));
            Files.createDirectories(topupDir);
            try {
                String stdout =
                        runPartialDeposit(
                                ethDir,
                                keystoreFile,
                                topupDir,
                                chain,
                                withdrawalAddress,
                                amountEth,
                                keystorePassword);
                return readProducedDepositData(topupDir, stdout);
            } finally {
                deleteRecursively(topupDir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("partial-deposit generation failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running partial-deposit", e);
        }
    }

    private String runPartialDeposit(
            Path ethDirMountSource,
            Path keystoreFile,
            Path hostOutputDir,
            String chain,
            String withdrawalAddress,
            BigDecimal amountEth,
            String keystorePassword)
            throws IOException, InterruptedException {
        String uidGid = currentUidGid();
        String containerKeystore =
                CONTAINER_DATA_DIR
                        + "/"
                        + ethDirMountSource.relativize(keystoreFile).toString().replace('\\', '/');
        String containerOutput =
                CONTAINER_DATA_DIR
                        + "/"
                        + ethDirMountSource.relativize(hostOutputDir).toString().replace('\\', '/');
        ProcessBuilder pb =
                new ProcessBuilder(
                        "docker",
                        "run",
                        "--rm",
                        "-i",
                        "--user",
                        uidGid,
                        "-v",
                        ethDirMountSource.toAbsolutePath() + ":" + CONTAINER_DATA_DIR,
                        image,
                        "--language=english",
                        "--non_interactive",
                        "partial-deposit",
                        "--chain=" + chain,
                        "--keystore=" + containerKeystore,
                        "--keystore_password=" + keystorePassword,
                        "--amount=" + amountEth.toPlainString(),
                        "--withdrawal_address=" + withdrawalAddress,
                        "--compounding",
                        "--output_folder=" + containerOutput);
        pb.redirectErrorStream(true);
        log.info("starting partial-deposit (chain={}, amount={} ETH)", chain, amountEth);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r =
                new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException(
                    "partial-deposit timed out after " + PROCESS_TIMEOUT_SECONDS + "s");
        }
        if (p.exitValue() != 0) {
            throw new IOException("partial-deposit exited " + p.exitValue() + ", output:\n" + out);
        }
        return out.toString();
    }

    private byte[] readProducedDepositData(Path topupDir, String cliStdout) throws IOException {
        // Walk recursively because some deposit-cli versions write into a `partial_deposits`
        // subfolder of --output_folder, others write directly into it. Picking the most recent
        // matching file covers both layouts.
        List<Path> produced;
        try (Stream<Path> walk = Files.walk(topupDir)) {
            produced =
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".json"))
                            .sorted(Comparator.comparing(Path::getFileName))
                            .toList();
        }
        if (produced.isEmpty()) {
            throw new IOException(
                    "partial-deposit did not produce any deposit file in "
                            + topupDir
                            + "; topup dir contents: "
                            + listAllPaths(topupDir)
                            + "; cli stdout:\n"
                            + cliStdout);
        }
        Path latest = produced.get(produced.size() - 1);
        return Files.readAllBytes(latest);
    }

    private static String listAllPaths(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.map(Path::toString).sorted().toList().toString();
        } catch (IOException e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }

    private Path findKeystoreFor(Path validatorKeysDir, String pubkey) throws IOException {
        String needle = normalizePubkey(pubkey);
        try (Stream<Path> walk = Files.list(validatorKeysDir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!name.startsWith("keystore-") || !name.endsWith(".json")) {
                    continue;
                }
                JsonNode parsed = mapper.readTree(Files.readAllBytes(file));
                JsonNode pk = parsed.get("pubkey");
                if (pk != null && pk.isTextual() && normalizePubkey(pk.asText()).equals(needle)) {
                    return file;
                }
            }
        }
        throw new IllegalStateException("no keystore matching pubkey " + pubkey);
    }

    private String findWithdrawalAddressFor(Path validatorKeysDir, String pubkey)
            throws IOException {
        String needle = normalizePubkey(pubkey);
        try (Stream<Path> walk = Files.list(validatorKeysDir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!name.startsWith("deposit_data-") || !name.endsWith(".json")) {
                    continue;
                }
                JsonNode parsed = mapper.readTree(Files.readAllBytes(file));
                if (!parsed.isArray()) {
                    continue;
                }
                for (JsonNode entry : parsed) {
                    JsonNode pk = entry.get("pubkey");
                    if (pk == null
                            || !pk.isTextual()
                            || !normalizePubkey(pk.asText()).equals(needle)) {
                        continue;
                    }
                    JsonNode creds = entry.get("withdrawal_credentials");
                    if (creds == null || !creds.isTextual()) {
                        throw new IllegalStateException(
                                "deposit_data entry for "
                                        + pubkey
                                        + " is missing withdrawal_credentials");
                    }
                    return executionAddressOf(creds.asText());
                }
            }
        }
        throw new IllegalStateException(
                "no deposit_data entry found for pubkey "
                        + pubkey
                        + "; cannot derive withdrawal address");
    }

    private static String executionAddressOf(String withdrawalCredentialsHex) {
        // 0x01 / 0x02 withdrawal credentials are 32 bytes (64 hex chars): first byte is the type
        // tag, next 11 bytes are zero padding, last 20 bytes (40 hex chars) are the execution
        // address. 0x00 (BLS-only) credentials cannot be topped up via partial-deposit.
        String s = withdrawalCredentialsHex.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() != 64) {
            throw new IllegalStateException(
                    "withdrawal_credentials must be 32 bytes; got " + s.length() / 2);
        }
        String tag = s.substring(0, 2).toLowerCase(Locale.ROOT);
        if (!tag.equals("01") && !tag.equals("02")) {
            throw new IllegalStateException(
                    "validator has 0x"
                            + tag
                            + " withdrawal credentials; top-up requires 0x01 or 0x02");
        }
        return toEip55Checksum(s.substring(24));
    }

    static String toEip55Checksum(String addressHexNoPrefix) {
        // deposit-cli refuses lowercase addresses to catch transcription typos. EIP-55 mixes case
        // by hashing the lowercase ASCII of the address with keccak-256 and uppercasing each hex
        // nibble whose corresponding hash nibble is >= 8.
        String lower = addressHexNoPrefix.toLowerCase(Locale.ROOT);
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] hash = keccak.digest(lower.getBytes(StandardCharsets.US_ASCII));
        StringBuilder out = new StringBuilder(2 + lower.length());
        out.append("0x");
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            int hashNibble = (hash[i / 2] >> (i % 2 == 0 ? 4 : 0)) & 0x0f;
            out.append(hashNibble >= 8 ? Character.toUpperCase(c) : c);
        }
        return out.toString();
    }

    private static String normalizePubkey(String pubkey) {
        String s = pubkey.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        return s.toLowerCase(Locale.ROOT);
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

    private String currentUidGid() {
        String cached = cachedUidGid;
        if (cached != null) {
            return cached;
        }
        try {
            String uid = readSingleLine("id", "-u");
            String gid = readSingleLine("id", "-g");
            cachedUidGid = uid + ":" + gid;
            return cachedUidGid;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "0:0";
        }
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

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                    // best-effort cleanup
                                }
                            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}

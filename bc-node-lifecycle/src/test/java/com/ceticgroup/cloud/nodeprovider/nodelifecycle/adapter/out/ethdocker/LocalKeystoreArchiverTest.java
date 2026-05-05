package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalKeystoreArchiverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalKeystoreArchiver archiver = new LocalKeystoreArchiver(mapper);

    @Test
    void archive_should_zipAllKeystoreJsonFiles(@TempDir Path tmp) throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(
                validatorKeys.resolve("keystore-m_1.json"),
                "{\"pubkey\":\"abc\"}",
                StandardCharsets.UTF_8);
        Files.writeString(
                validatorKeys.resolve("keystore-m_2.json"),
                "{\"pubkey\":\"def\"}",
                StandardCharsets.UTF_8);
        // a non-keystore file that must NOT end up in the zip
        Files.writeString(validatorKeys.resolve("password.txt"), "secret", StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        byte[] zip = archiver.archive(ref);

        Map<String, String> entries = readZip(zip);
        assertThat(entries).hasSize(2).containsKeys("keystore-m_1.json", "keystore-m_2.json");
        assertThat(entries.get("keystore-m_1.json")).contains("\"pubkey\":\"abc\"");
        assertThat(entries).doesNotContainKey("password.txt");
    }

    @Test
    void archive_should_throw_when_noValidatorKeysDir(@TempDir Path tmp) {
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.archive(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no validator_keys directory");
    }

    @Test
    void archive_should_throw_when_directoryIsEmpty(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve(".eth/validator_keys"));
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.archive(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no keystore-*.json");
    }

    @Test
    void depositData_should_concatenateAllDepositDataFilesIntoSingleArray(@TempDir Path tmp)
            throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(
                validatorKeys.resolve("deposit_data-1700000001.json"),
                "[{\"pubkey\":\"a\"},{\"pubkey\":\"b\"}]",
                StandardCharsets.UTF_8);
        Files.writeString(
                validatorKeys.resolve("deposit_data-1700000002.json"),
                "[{\"pubkey\":\"c\"}]",
                StandardCharsets.UTF_8);
        Files.writeString(
                validatorKeys.resolve("keystore-m_1.json"),
                "{\"pubkey\":\"a\"}",
                StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        byte[] json = archiver.depositData(ref);

        com.fasterxml.jackson.databind.JsonNode root =
                mapper.readTree(new String(json, StandardCharsets.UTF_8));
        assertThat(root.isArray()).isTrue();
        assertThat(root).hasSize(3);
        assertThat(root.get(0).get("pubkey").asText()).isEqualTo("a");
        assertThat(root.get(1).get("pubkey").asText()).isEqualTo("b");
        assertThat(root.get(2).get("pubkey").asText()).isEqualTo("c");
    }

    @Test
    void depositData_should_throw_when_noDepositDataFile(@TempDir Path tmp) throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(validatorKeys.resolve("keystore-m_1.json"), "{}", StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.depositData(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deposit_data");
    }

    @Test
    void depositData_should_throw_when_noValidatorKeysDir(@TempDir Path tmp) {
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.depositData(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no validator_keys directory");
    }

    @Test
    void keystoreFor_should_returnMatchingKeystoreJson_when_pubkeyMatches(@TempDir Path tmp)
            throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        String alphaJson = "{\"pubkey\":\"aaaa1111\",\"crypto\":{\"x\":1}}";
        String betaJson = "{\"pubkey\":\"bbbb2222\",\"crypto\":{\"x\":2}}";
        Files.writeString(
                validatorKeys.resolve("keystore-m_1.json"), alphaJson, StandardCharsets.UTF_8);
        Files.writeString(
                validatorKeys.resolve("keystore-m_2.json"), betaJson, StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        byte[] keystore = archiver.keystoreFor(ref, "0xbbbb2222");

        com.fasterxml.jackson.databind.JsonNode parsed =
                mapper.readTree(new String(keystore, StandardCharsets.UTF_8));
        assertThat(parsed.get("pubkey").asText()).isEqualTo("bbbb2222");
        assertThat(parsed.get("crypto").get("x").asInt()).isEqualTo(2);
    }

    @Test
    void keystoreFor_should_matchWithoutCaseAndPrefix(@TempDir Path tmp) throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(
                validatorKeys.resolve("keystore-m_1.json"),
                "{\"pubkey\":\"AABBccdd\"}",
                StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        byte[] keystore = archiver.keystoreFor(ref, "0xaabbccdd");

        assertThat(new String(keystore, StandardCharsets.UTF_8)).contains("AABBccdd");
    }

    @Test
    void keystoreFor_should_throw_when_noKeystoreMatchesPubkey(@TempDir Path tmp)
            throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(
                validatorKeys.resolve("keystore-m_1.json"),
                "{\"pubkey\":\"aaaa\"}",
                StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.keystoreFor(ref, "0xdeadbeef"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no keystore matching pubkey");
    }

    @Test
    void keystoreFor_should_throw_when_noValidatorKeysDir(@TempDir Path tmp) {
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.keystoreFor(ref, "abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no validator_keys directory");
    }

    @Test
    void depositDataFor_should_returnSingleEntryArrayJson_when_pubkeyMatches(@TempDir Path tmp)
            throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(
                validatorKeys.resolve("deposit_data-1700000001.json"),
                "[{\"pubkey\":\"aaaa\",\"amount\":32},{\"pubkey\":\"bbbb\",\"amount\":32}]",
                StandardCharsets.UTF_8);
        Files.writeString(
                validatorKeys.resolve("deposit_data-1700000002.json"),
                "[{\"pubkey\":\"cccc\",\"amount\":32}]",
                StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        byte[] json = archiver.depositDataFor(ref, "0xbbbb");

        com.fasterxml.jackson.databind.JsonNode root =
                mapper.readTree(new String(json, StandardCharsets.UTF_8));
        assertThat(root.isArray()).isTrue();
        assertThat(root).hasSize(1);
        assertThat(root.get(0).get("pubkey").asText()).isEqualTo("bbbb");
        assertThat(root.get(0).get("amount").asInt()).isEqualTo(32);
    }

    @Test
    void depositDataFor_should_throw_when_noEntryMatchesPubkey(@TempDir Path tmp)
            throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(
                validatorKeys.resolve("deposit_data-1700000001.json"),
                "[{\"pubkey\":\"aaaa\"}]",
                StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.depositDataFor(ref, "0xdeadbeef"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no deposit_data entry matching pubkey");
    }

    @Test
    void depositDataFor_should_throw_when_noDepositDataFile(@TempDir Path tmp) throws IOException {
        Path validatorKeys = tmp.resolve(".eth/validator_keys");
        Files.createDirectories(validatorKeys);
        Files.writeString(validatorKeys.resolve("keystore-m_1.json"), "{}", StandardCharsets.UTF_8);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"" + tmp.toAbsolutePath() + "\"}");

        assertThatThrownBy(() -> archiver.depositDataFor(ref, "abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deposit_data");
    }

    private static Map<String, String> readZip(byte[] zipBytes) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                result.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return result;
    }
}

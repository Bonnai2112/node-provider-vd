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

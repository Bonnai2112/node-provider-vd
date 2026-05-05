package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyArchiverPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LocalKeystoreArchiver implements ValidatorKeyArchiverPort {

    private static final String VALIDATOR_KEYS_REL = ".eth/validator_keys";

    private final ObjectMapper mapper;

    public LocalKeystoreArchiver(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public byte[] archive(DeploymentRef ref) {
        Objects.requireNonNull(ref, "ref");
        Path workdir = workdirOf(ref);
        Path keystoreDir = workdir.resolve(VALIDATOR_KEYS_REL);
        if (!Files.isDirectory(keystoreDir)) {
            throw new IllegalStateException(
                    "no validator_keys directory at " + keystoreDir + "; nothing to archive");
        }

        try {
            List<Path> keystores;
            try (Stream<Path> walk = Files.list(keystoreDir)) {
                keystores =
                        walk.filter(Files::isRegularFile)
                                .filter(LocalKeystoreArchiver::isKeystoreFile)
                                .sorted(Comparator.comparing(Path::getFileName))
                                .toList();
            }
            if (keystores.isEmpty()) {
                throw new IllegalStateException("no keystore-*.json files found in " + keystoreDir);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                for (Path file : keystores) {
                    zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("failed to build keystore archive", e);
        }
    }

    private static boolean isKeystoreFile(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith("keystore-") && name.endsWith(".json");
    }

    private static boolean isDepositDataFile(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith("deposit_data-") && name.endsWith(".json");
    }

    @Override
    public byte[] depositData(DeploymentRef ref) {
        Objects.requireNonNull(ref, "ref");
        Path workdir = workdirOf(ref);
        Path keystoreDir = workdir.resolve(VALIDATOR_KEYS_REL);
        if (!Files.isDirectory(keystoreDir)) {
            throw new IllegalStateException(
                    "no validator_keys directory at " + keystoreDir + "; nothing to archive");
        }

        try {
            List<Path> depositFiles;
            try (Stream<Path> walk = Files.list(keystoreDir)) {
                depositFiles =
                        walk.filter(Files::isRegularFile)
                                .filter(LocalKeystoreArchiver::isDepositDataFile)
                                .sorted(Comparator.comparing(Path::getFileName))
                                .toList();
            }
            if (depositFiles.isEmpty()) {
                throw new IllegalStateException(
                        "no deposit_data-*.json files found in " + keystoreDir);
            }
            com.fasterxml.jackson.databind.node.ArrayNode merged = mapper.createArrayNode();
            for (Path file : depositFiles) {
                JsonNode parsed = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
                if (!parsed.isArray()) {
                    throw new IllegalStateException(
                            "deposit_data file " + file.getFileName() + " is not a JSON array");
                }
                parsed.forEach(merged::add);
            }
            return mapper.writeValueAsBytes(merged);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read deposit_data files", e);
        }
    }

    @Override
    public byte[] keystoreFor(DeploymentRef ref, String pubkey) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(pubkey, "pubkey");
        String needle = normalizePubkey(pubkey);
        Path keystoreDir = validatorKeysDir(ref);

        try {
            List<Path> keystores;
            try (Stream<Path> walk = Files.list(keystoreDir)) {
                keystores =
                        walk.filter(Files::isRegularFile)
                                .filter(LocalKeystoreArchiver::isKeystoreFile)
                                .toList();
            }
            for (Path file : keystores) {
                byte[] bytes = Files.readAllBytes(file);
                JsonNode parsed = mapper.readTree(bytes);
                JsonNode pk = parsed.get("pubkey");
                if (pk != null && pk.isTextual() && normalizePubkey(pk.asText()).equals(needle)) {
                    return bytes;
                }
            }
            throw new IllegalStateException("no keystore matching pubkey " + pubkey);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read keystores", e);
        }
    }

    @Override
    public byte[] depositDataFor(DeploymentRef ref, String pubkey) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(pubkey, "pubkey");
        String needle = normalizePubkey(pubkey);
        Path keystoreDir = validatorKeysDir(ref);

        try {
            List<Path> depositFiles;
            try (Stream<Path> walk = Files.list(keystoreDir)) {
                depositFiles =
                        walk.filter(Files::isRegularFile)
                                .filter(LocalKeystoreArchiver::isDepositDataFile)
                                .sorted(Comparator.comparing(Path::getFileName))
                                .toList();
            }
            if (depositFiles.isEmpty()) {
                throw new IllegalStateException(
                        "no deposit_data-*.json files found in " + keystoreDir);
            }
            for (Path file : depositFiles) {
                JsonNode parsed = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
                if (!parsed.isArray()) {
                    continue;
                }
                for (JsonNode entry : parsed) {
                    JsonNode pk = entry.get("pubkey");
                    if (pk != null
                            && pk.isTextual()
                            && normalizePubkey(pk.asText()).equals(needle)) {
                        com.fasterxml.jackson.databind.node.ArrayNode singleton =
                                mapper.createArrayNode();
                        singleton.add(entry);
                        return mapper.writeValueAsBytes(singleton);
                    }
                }
            }
            throw new IllegalStateException("no deposit_data entry matching pubkey " + pubkey);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read deposit_data files", e);
        }
    }

    private Path validatorKeysDir(DeploymentRef ref) {
        Path workdir = workdirOf(ref);
        Path keystoreDir = workdir.resolve(VALIDATOR_KEYS_REL);
        if (!Files.isDirectory(keystoreDir)) {
            throw new IllegalStateException(
                    "no validator_keys directory at " + keystoreDir + "; nothing to read");
        }
        return keystoreDir;
    }

    private static String normalizePubkey(String pubkey) {
        String s = pubkey.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        return s.toLowerCase(java.util.Locale.ROOT);
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
}

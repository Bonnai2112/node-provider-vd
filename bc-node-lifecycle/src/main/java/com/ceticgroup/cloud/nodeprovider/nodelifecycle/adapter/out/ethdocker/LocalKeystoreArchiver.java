package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyArchiverPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

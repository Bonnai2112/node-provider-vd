package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyImporterPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EthdValidatorKeyImporter implements ValidatorKeyImporterPort {

    // eth-docker convention: keystores live under <workdir>/.eth/validator_keys/.
    private static final String KEYSTORE_DIR_REL = ".eth/validator_keys";
    private static final String PASSWORD_FILE = "password.txt";

    private final EthdShellRunner shell;
    private final ObjectMapper mapper;

    public EthdValidatorKeyImporter(EthdShellRunner shell, ObjectMapper mapper) {
        this.shell = Objects.requireNonNull(shell, "shell");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<String> importKeystores(
            DeploymentRef ref, List<KeystoreFile> keystores, String password) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(keystores, "keystores");
        Objects.requireNonNull(password, "password");
        if (keystores.isEmpty()) {
            return List.of();
        }
        Path workdir = workdirOf(ref);
        Path keystoreRoot = workdir.resolve(KEYSTORE_DIR_REL);

        List<String> pubkeys = new ArrayList<>(keystores.size());
        try {
            for (KeystoreFile k : keystores) {
                shell.writeFile(keystoreRoot, k.fileName(), k.content());
                pubkeys.add(extractPubkey(k));
            }
            // password.txt is kept for compatibility with eth-docker variants that read it,
            // but the canonical path is the KEYSTORE_PASSWORD env var passed below.
            shell.writeFile(keystoreRoot, PASSWORD_FILE, password);
            shell.runEthdKeysImport(workdir, password);
        } catch (IOException e) {
            throw new IllegalStateException("failed to import validator keystores", e);
        }
        return pubkeys;
    }

    @Override
    public void triggerImport(DeploymentRef ref, String password) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(password, "password");
        Path workdir = workdirOf(ref);
        Path keystoreRoot = workdir.resolve(KEYSTORE_DIR_REL);
        try {
            shell.writeFile(keystoreRoot, PASSWORD_FILE, password);
            shell.runEthdKeysImport(workdir, password);
        } catch (IOException e) {
            throw new IllegalStateException("failed to trigger validator key import", e);
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

    private String extractPubkey(KeystoreFile keystore) {
        try {
            JsonNode root = mapper.readTree(keystore.content());
            JsonNode pubkey = root.get("pubkey");
            if (pubkey == null || !pubkey.isTextual() || pubkey.asText().isBlank()) {
                throw new IllegalArgumentException(
                        "keystore " + keystore.fileName() + " has no pubkey field");
            }
            String value = pubkey.asText();
            return value.startsWith("0x") ? value : "0x" + value;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "keystore " + keystore.fileName() + " is not valid JSON", e);
        }
    }
}

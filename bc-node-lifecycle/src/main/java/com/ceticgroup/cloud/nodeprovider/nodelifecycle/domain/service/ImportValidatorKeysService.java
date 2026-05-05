package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ImportValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyImporterPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyImporterPort.KeystoreFile;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ImportValidatorKeysService implements ImportValidatorKeysUseCase {

    private final NodeRepository nodes;
    private final ValidatorKeyRepository keys;
    private final ValidatorKeyImporterPort importer;
    private final Clock clock;

    public ImportValidatorKeysService(
            NodeRepository nodes, ValidatorKeyRepository keys, ValidatorKeyImporterPort importer) {
        this(nodes, keys, importer, Clock.systemUTC());
    }

    public ImportValidatorKeysService(
            NodeRepository nodes,
            ValidatorKeyRepository keys,
            ValidatorKeyImporterPort importer,
            Clock clock) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.importer = Objects.requireNonNull(importer, "importer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<ValidatorKey> importKeys(ImportValidatorKeysCommand command) {
        Objects.requireNonNull(command, "command");
        Node node =
                nodes.findById(command.nodeId())
                        .orElseThrow(() -> new NodeNotFoundException(command.nodeId()));
        if (!node.owner().equals(command.requester())) {
            throw new NodeNotFoundException(command.nodeId());
        }
        if (!node.options().validator()) {
            throw new IllegalStateException(
                    "node is not configured for validator; recreate with validator=true");
        }
        DeploymentRef ref = node.deploymentRef();
        if (ref == null) {
            throw new IllegalStateException("node has no deployment yet, retry once provisioned");
        }

        List<KeystoreFile> files =
                command.keystores().stream()
                        .map(k -> new KeystoreFile(k.fileName(), k.content()))
                        .toList();
        List<String> importedPubkeys = importer.importKeystores(ref, files, command.password());

        var now = clock.instant();
        List<ValidatorKey> records =
                importedPubkeys.stream()
                        .map(pk -> new ValidatorKey(UUID.randomUUID(), command.nodeId(), pk, now))
                        .toList();
        keys.saveAll(records);
        return records;
    }
}

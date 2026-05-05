package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyGeneratorPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyImporterPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class GenerateValidatorKeysService implements GenerateValidatorKeysUseCase {

    private static final int PASSWORD_BYTES = 24;

    private final NodeRepository nodes;
    private final ValidatorKeyRepository keys;
    private final ValidatorKeyGeneratorPort generator;
    private final ValidatorKeyImporterPort importer;
    private final Clock clock;
    private final Supplier<String> passwordSupplier;

    public GenerateValidatorKeysService(
            NodeRepository nodes,
            ValidatorKeyRepository keys,
            ValidatorKeyGeneratorPort generator,
            ValidatorKeyImporterPort importer) {
        this(nodes, keys, generator, importer, Clock.systemUTC(), defaultPasswordSupplier());
    }

    public GenerateValidatorKeysService(
            NodeRepository nodes,
            ValidatorKeyRepository keys,
            ValidatorKeyGeneratorPort generator,
            ValidatorKeyImporterPort importer,
            Clock clock,
            Supplier<String> passwordSupplier) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.importer = Objects.requireNonNull(importer, "importer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.passwordSupplier = Objects.requireNonNull(passwordSupplier, "passwordSupplier");
    }

    @Override
    public GenerateValidatorKeysResult generate(GenerateValidatorKeysCommand command) {
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

        String password = passwordSupplier.get();
        ValidatorKeyGeneratorPort.GeneratedKeys generated =
                generator.generate(
                        ref,
                        chainOf(node.network()),
                        command.count(),
                        command.withdrawalAddress(),
                        password);
        importer.triggerImport(ref, password);

        Instant now = clock.instant();
        List<ValidatorKey> records =
                generated.pubkeys().stream()
                        .map(pk -> new ValidatorKey(UUID.randomUUID(), command.nodeId(), pk, now))
                        .toList();
        keys.saveAll(records);

        return new GenerateValidatorKeysResult(generated.mnemonic(), password, records);
    }

    private static String chainOf(Network network) {
        return network.name().toLowerCase(Locale.ROOT);
    }

    private static Supplier<String> defaultPasswordSupplier() {
        SecureRandom rng = new SecureRandom();
        return () -> {
            byte[] buf = new byte[PASSWORD_BYTES];
            rng.nextBytes(buf);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        };
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateTopupDepositUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.TopupDepositGeneratorPort;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class GenerateTopupDepositService implements GenerateTopupDepositUseCase {

    // partial-deposit produces a deposit that the consensus layer only honors as a top-up when
    // the target validator carries 0x02 (compounding) credentials — Pectra-only. Networks listed
    // here are those known to have activated Pectra.
    private static final Set<Network> PECTRA_ACTIVE = EnumSet.of(Network.HOODI, Network.SEPOLIA);

    private final NodeRepository nodes;
    private final TopupDepositGeneratorPort generator;

    public GenerateTopupDepositService(NodeRepository nodes, TopupDepositGeneratorPort generator) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public byte[] generate(GenerateTopupDepositCommand command) {
        Objects.requireNonNull(command, "command");
        Node node =
                nodes.findById(command.nodeId())
                        .orElseThrow(() -> new NodeNotFoundException(command.nodeId()));
        if (!node.owner().equals(command.requester())) {
            throw new NodeNotFoundException(command.nodeId());
        }
        if (!node.options().validator()) {
            throw new IllegalStateException(
                    "node is not configured for validator; nothing to top up");
        }
        if (!PECTRA_ACTIVE.contains(node.network())) {
            throw new IllegalStateException(
                    "top-up requires a Pectra-active network; got " + node.network());
        }
        DeploymentRef ref = node.deploymentRef();
        if (ref == null) {
            throw new IllegalStateException("node has no deployment yet, retry once provisioned");
        }
        return generator.generate(
                ref,
                chainOf(node.network()),
                command.pubkey(),
                command.amountEth(),
                command.keystorePassword());
    }

    private static String chainOf(Network network) {
        return network.name().toLowerCase(Locale.ROOT);
    }
}

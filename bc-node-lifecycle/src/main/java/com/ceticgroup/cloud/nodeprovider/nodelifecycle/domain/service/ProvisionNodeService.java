package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProvisionNodeService implements ProvisionNodeUseCase {

    private final NodeRepository repository;
    private final DomainEventPublisher publisher;

    public ProvisionNodeService(NodeRepository repository, DomainEventPublisher publisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public NodeId provision(ProvisionNodeCommand command) {
        Objects.requireNonNull(command, "command");
        rejectValidatorClients(command);
        NodeId id = new NodeId(UUID.randomUUID());
        Node node = Node.request(id, command.owner(), command.network(), command.clientPair());
        repository.save(node);
        List<NodeDomainEvent> events = node.pullEvents();
        events.forEach(publisher::publish);
        return id;
    }

    private static void rejectValidatorClients(ProvisionNodeCommand command) {
        if (command.clientPair().executionLayer().isValidator()
                || command.clientPair().consensusLayer().isValidator()) {
            throw new IllegalArgumentException(
                    "validator clients are not supported in v1");
        }
    }
}

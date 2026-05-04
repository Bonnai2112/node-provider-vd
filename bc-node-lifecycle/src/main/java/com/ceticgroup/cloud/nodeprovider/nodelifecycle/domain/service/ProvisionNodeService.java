package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class ProvisionNodeService implements ProvisionNodeUseCase {

    private final NodeRepository repository;
    private final DomainEventPublisher publisher;
    private final NodeOrchestrationPort orchestration;
    private final Executor executor;

    public ProvisionNodeService(
            NodeRepository repository,
            DomainEventPublisher publisher,
            NodeOrchestrationPort orchestration,
            Executor executor) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.orchestration = Objects.requireNonNull(orchestration, "orchestration");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public NodeId provision(ProvisionNodeCommand command) {
        Objects.requireNonNull(command, "command");
        rejectValidatorClients(command);
        NodeId id = new NodeId(UUID.randomUUID());
        Node node = Node.request(id, command.owner(), command.network(), command.clientPair());
        repository.save(node);
        publishPending(node);

        NodeSpec spec = new NodeSpec(id, command.owner(), command.network(), command.clientPair());
        executor.execute(() -> deployAsync(id, spec));
        return id;
    }

    private void deployAsync(NodeId id, NodeSpec spec) {
        try {
            DeploymentRef ref = orchestration.deploy(spec);
            transition(id, node -> node.startProvisioning(ref));
        } catch (RuntimeException e) {
            try {
                transition(id, node -> node.fail("deploy failed: " + safeMessage(e)));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void transition(NodeId id, java.util.function.Consumer<Node> mutation) {
        Node node = repository.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
        mutation.accept(node);
        repository.save(node);
        publishPending(node);
    }

    private void publishPending(Node node) {
        List<NodeDomainEvent> events = node.pullEvents();
        events.forEach(publisher::publish);
    }

    private static void rejectValidatorClients(ProvisionNodeCommand command) {
        if (command.clientPair().executionLayer().isValidator()
                || command.clientPair().consensusLayer().isValidator()) {
            throw new IllegalArgumentException("validator clients are not supported in v1");
        }
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.IllegalNodeTransitionException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.EnableValidatorUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

public final class EnableValidatorService implements EnableValidatorUseCase {

    private final NodeRepository repository;
    private final NodeOrchestrationPort orchestration;
    private final DomainEventPublisher publisher;
    private final Executor executor;

    public EnableValidatorService(
            NodeRepository repository,
            NodeOrchestrationPort orchestration,
            DomainEventPublisher publisher,
            Executor executor) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.orchestration = Objects.requireNonNull(orchestration, "orchestration");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void enable(
            NodeId id, OwnerId requester, String feeRecipient, Optional<String> graffiti) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requester, "requester");
        Node node = repository.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
        if (!node.owner().equals(requester)) {
            throw new NodeNotFoundException(id);
        }
        DeploymentRef ref = node.deploymentRef();
        if (ref == null) {
            throw new IllegalNodeTransitionException(
                    "Cannot enable validator: deployment is missing");
        }
        node.enableValidator(feeRecipient, graffiti);
        NodeSpec newSpec =
                new NodeSpec(
                        node.id(), node.owner(), node.network(), node.clientPair(), node.options());
        repository.save(node);
        publishPending(node);
        executor.execute(() -> applyAsync(id, ref, newSpec));
    }

    private void applyAsync(NodeId id, DeploymentRef ref, NodeSpec newSpec) {
        try {
            orchestration.applyOptionsChange(ref, newSpec);
        } catch (RuntimeException e) {
            try {
                transition(id, n -> n.fail("enable validator failed: " + safeMessage(e)));
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

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}

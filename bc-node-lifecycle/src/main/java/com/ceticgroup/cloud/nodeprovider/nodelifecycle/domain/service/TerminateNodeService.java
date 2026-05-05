package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.TerminateNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class TerminateNodeService implements TerminateNodeUseCase {

    private final NodeRepository repository;
    private final NodeOrchestrationPort orchestration;
    private final DomainEventPublisher publisher;
    private final Executor executor;

    public TerminateNodeService(
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
    public void terminate(NodeId id, OwnerId requester) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requester, "requester");
        Node node = repository.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
        // Don't leak existence to a caller who doesn't own the node — surface a 404.
        if (!node.owner().equals(requester)) {
            throw new NodeNotFoundException(id);
        }
        node.terminate();
        repository.save(node);
        publishPending(node);

        DeploymentRef ref = node.deploymentRef();
        executor.execute(() -> tearDownAsync(id, ref));
    }

    private void tearDownAsync(NodeId id, DeploymentRef ref) {
        try {
            if (ref != null) {
                orchestration.tearDown(ref);
            }
            transition(id, Node::markTerminated);
        } catch (RuntimeException e) {
            try {
                transition(id, n -> n.fail("teardown failed: " + safeMessage(e)));
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

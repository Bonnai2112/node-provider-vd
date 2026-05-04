package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.SyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ReconcileNodeStatusUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ReconcileNodeStatusService implements ReconcileNodeStatusUseCase {

    private final NodeRepository repository;
    private final NodeOrchestrationPort orchestration;
    private final BlockchainProbePort probe;
    private final DomainEventPublisher publisher;

    public ReconcileNodeStatusService(
            NodeRepository repository,
            NodeOrchestrationPort orchestration,
            BlockchainProbePort probe,
            DomainEventPublisher publisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.orchestration = Objects.requireNonNull(orchestration, "orchestration");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void reconcileAll() {
        for (Node node : repository.findNonTerminal()) {
            reconcileOne(node);
        }
    }

    private void reconcileOne(Node node) {
        DeploymentRef ref = node.deploymentRef();
        if (ref == null) {
            return;
        }
        RuntimeStatus runtime = orchestration.getDeploymentStatus(ref);
        Optional<JsonRpcEndpoint> elEndpoint = orchestration.endpointFor(ref);
        SyncStatus sync = elEndpoint.map(probe::probeSync).orElse(new SyncStatus.NotSyncing());
        int peers = elEndpoint.map(probe::probePeers).orElse(0);

        boolean changed = applyTransition(node, runtime, sync, peers, elEndpoint);
        if (changed) {
            repository.save(node);
            List<NodeDomainEvent> events = node.pullEvents();
            events.forEach(publisher::publish);
        }
    }

    private static boolean applyTransition(
            Node node,
            RuntimeStatus runtime,
            SyncStatus sync,
            int peers,
            Optional<JsonRpcEndpoint> elEndpoint) {
        return switch (node.status()) {
            case NodeStatus.Provisioning _ -> reconcileFromProvisioning(node, runtime);
            case NodeStatus.Syncing _ ->
                    reconcileFromSyncing(node, runtime, sync, peers, elEndpoint);
            case NodeStatus.Ready _ -> reconcileFromReady(node, runtime, sync, peers);
            case NodeStatus.Degraded _ ->
                    reconcileFromDegraded(node, runtime, sync, peers, elEndpoint);
            default -> false;
        };
    }

    private static boolean reconcileFromProvisioning(Node node, RuntimeStatus runtime) {
        return switch (runtime) {
            case RuntimeStatus.Running _ -> {
                node.markSyncing();
                yield true;
            }
            case RuntimeStatus.Crashed c -> {
                node.fail("container crashed during provisioning: " + c.reason());
                yield true;
            }
            default -> false;
        };
    }

    private static boolean reconcileFromSyncing(
            Node node,
            RuntimeStatus runtime,
            SyncStatus sync,
            int peers,
            Optional<JsonRpcEndpoint> elEndpoint) {
        if (runtime instanceof RuntimeStatus.Crashed c) {
            node.fail("container crashed while syncing: " + c.reason());
            return true;
        }
        if (runtime instanceof RuntimeStatus.Running
                && sync instanceof SyncStatus.Synced
                && peers >= 1
                && elEndpoint.isPresent()) {
            node.markReady(new Endpoint(elEndpoint.get().uri()));
            return true;
        }
        return false;
    }

    private static boolean reconcileFromReady(
            Node node, RuntimeStatus runtime, SyncStatus sync, int peers) {
        if (runtime instanceof RuntimeStatus.Crashed c) {
            node.markDegraded("container crashed: " + c.reason());
            return true;
        }
        if (runtime instanceof RuntimeStatus.Running
                && (!(sync instanceof SyncStatus.Synced) || peers == 0)) {
            node.markDegraded(degradedReason(sync, peers));
            return true;
        }
        return false;
    }

    private static boolean reconcileFromDegraded(
            Node node,
            RuntimeStatus runtime,
            SyncStatus sync,
            int peers,
            Optional<JsonRpcEndpoint> elEndpoint) {
        if (runtime instanceof RuntimeStatus.Running
                && sync instanceof SyncStatus.Synced
                && peers >= 1
                && elEndpoint.isPresent()) {
            node.markReady(new Endpoint(elEndpoint.get().uri()));
            return true;
        }
        return false;
    }

    private static String degradedReason(SyncStatus sync, int peers) {
        if (peers == 0) {
            return "no peers";
        }
        return "sync regressed: " + sync.getClass().getSimpleName();
    }
}

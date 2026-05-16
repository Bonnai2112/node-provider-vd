package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ConsensusSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ExecutionSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LastObservation;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LayerState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ReconcileNodeStatusUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ReconcileNodeStatusService implements ReconcileNodeStatusUseCase {

    private final NodeRepository repository;
    private final NodeOrchestrationPort orchestration;
    private final BlockchainProbePort probe;
    private final DomainEventPublisher publisher;
    private final Clock clock;

    public ReconcileNodeStatusService(
            NodeRepository repository,
            NodeOrchestrationPort orchestration,
            BlockchainProbePort probe,
            DomainEventPublisher publisher) {
        this(repository, orchestration, probe, publisher, Clock.systemUTC());
    }

    public ReconcileNodeStatusService(
            NodeRepository repository,
            NodeOrchestrationPort orchestration,
            BlockchainProbePort probe,
            DomainEventPublisher publisher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.orchestration = Objects.requireNonNull(orchestration, "orchestration");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        Optional<URI> clEndpoint = orchestration.clRestEndpointFor(ref);
        Optional<ExecutionSyncStatus> elSync = Optional.empty();
        OptionalInt peers = OptionalInt.empty();
        Optional<ConsensusSyncStatus> clSync = Optional.empty();
        if (elEndpoint.isPresent()) {
            elSync = probe.probeElSync(elEndpoint.get());
            peers = probe.probePeers(elEndpoint.get());
        }
        if (clEndpoint.isPresent()) {
            clSync = probe.probeClSync(clEndpoint.get());
        }

        Instant now = clock.instant();
        Optional<LastObservation> previous = node.lastObservation();
        OptionalDouble elBps = elBlocksPerSecond(previous, elSync, now);
        OptionalDouble clSps = clSlotsPerSecond(previous, clSync, now);
        // Sticky: a transient probe failure (ConnectException, 5xx) shouldn't blank out the
        // value we already showed at the previous tick — the API would flicker null otherwise.
        Optional<ExecutionSyncStatus> stickyElSync =
                elSync.isPresent() ? elSync : previous.flatMap(LastObservation::elSync);
        Optional<ConsensusSyncStatus> stickyClSync =
                clSync.isPresent() ? clSync : previous.flatMap(LastObservation::clSync);
        OptionalInt stickyPeers =
                peers.isPresent()
                        ? peers
                        : previous.map(LastObservation::peers).orElse(OptionalInt.empty());
        node.observe(
                new LastObservation(stickyElSync, stickyClSync, elBps, clSps, stickyPeers, now));
        boolean changed =
                applyTransition(
                        node,
                        runtime,
                        elSync,
                        clSync,
                        peers,
                        elEndpoint,
                        () -> orchestration.canRestart(ref));
        // We always persist so the observation is saved even when no transition fired.
        repository.save(node);
        if (changed) {
            List<NodeDomainEvent> events = node.pullEvents();
            events.forEach(publisher::publish);
        }
    }

    private static boolean applyTransition(
            Node node,
            RuntimeStatus runtime,
            Optional<ExecutionSyncStatus> elSync,
            Optional<ConsensusSyncStatus> clSync,
            OptionalInt peers,
            Optional<JsonRpcEndpoint> elEndpoint,
            java.util.function.BooleanSupplier canRestart) {
        return switch (node.status()) {
            case NodeStatus.Provisioning _ -> reconcileFromProvisioning(node, runtime, canRestart);
            case NodeStatus.Syncing _ ->
                    reconcileFromSyncing(
                            node, runtime, elSync, clSync, peers, elEndpoint, canRestart);
            case NodeStatus.Ready _ ->
                    reconcileFromReady(node, runtime, elSync, clSync, peers, canRestart);
            case NodeStatus.Degraded _ ->
                    reconcileFromDegraded(
                            node, runtime, elSync, clSync, peers, elEndpoint, canRestart);
            default -> false;
        };
    }

    private static boolean reconcileFromProvisioning(
            Node node, RuntimeStatus runtime, java.util.function.BooleanSupplier canRestart) {
        if (!(runtime instanceof RuntimeStatus.Healthy h)) {
            return false;
        }
        if (coreFault(h)) {
            haltOrFail(node, h, canRestart);
            return true;
        }
        if (bothRunning(h)) {
            node.markSyncing();
            return true;
        }
        return false;
    }

    private static boolean reconcileFromSyncing(
            Node node,
            RuntimeStatus runtime,
            Optional<ExecutionSyncStatus> elSync,
            Optional<ConsensusSyncStatus> clSync,
            OptionalInt peers,
            Optional<JsonRpcEndpoint> elEndpoint,
            java.util.function.BooleanSupplier canRestart) {
        if (!(runtime instanceof RuntimeStatus.Healthy h)) {
            return false;
        }
        if (coreFault(h)) {
            haltOrFail(node, h, canRestart);
            return true;
        }
        // Ready criterion is relaxed to EL synced + peers; CL sync and validator state are
        // observed but do not gate the transition (a validator can be idle while keys are
        // being imported).
        if (bothRunning(h) && isElSynced(elSync) && hasPeers(peers) && elEndpoint.isPresent()) {
            node.markReady(new Endpoint(elEndpoint.get().uri()));
            return true;
        }
        return false;
    }

    private static boolean reconcileFromReady(
            Node node,
            RuntimeStatus runtime,
            Optional<ExecutionSyncStatus> elSync,
            Optional<ConsensusSyncStatus> clSync,
            OptionalInt peers,
            java.util.function.BooleanSupplier canRestart) {
        if (!(runtime instanceof RuntimeStatus.Healthy h)) {
            return false;
        }
        if (coreFault(h)) {
            // Containers exited or were removed: halt the node so the operator can decide whether
            // to restart (workdir present) or accept the failure (workdir gone).
            haltOrFail(node, h, canRestart);
            return true;
        }
        if (coreTransient(h)) {
            // Starting layers are transient by design — stay observable as Degraded, the next tick
            // will catch the recovery.
            node.markDegraded(RuntimeStatus.formatLayers(h.el(), h.cl(), h.validator()));
            return true;
        }
        // EL+CL both running. Probe failures (Optional.empty) are transient, don't degrade.
        if (elSync.isPresent() && peers.isPresent()) {
            String reason = degradedReason(elSync.get(), peers.getAsInt());
            if (reason != null) {
                node.markDegraded(reason);
                return true;
            }
        }
        return false;
    }

    private static boolean reconcileFromDegraded(
            Node node,
            RuntimeStatus runtime,
            Optional<ExecutionSyncStatus> elSync,
            Optional<ConsensusSyncStatus> clSync,
            OptionalInt peers,
            Optional<JsonRpcEndpoint> elEndpoint,
            java.util.function.BooleanSupplier canRestart) {
        if (!(runtime instanceof RuntimeStatus.Healthy h)) {
            return false;
        }
        if (coreFault(h)) {
            haltOrFail(node, h, canRestart);
            return true;
        }
        if (bothRunning(h) && isElSynced(elSync) && hasPeers(peers) && elEndpoint.isPresent()) {
            node.markReady(new Endpoint(elEndpoint.get().uri()));
            return true;
        }
        return false;
    }

    private static void haltOrFail(
            Node node, RuntimeStatus.Healthy h, java.util.function.BooleanSupplier canRestart) {
        String reason = RuntimeStatus.formatLayers(h.el(), h.cl(), h.validator());
        if (canRestart.getAsBoolean()) {
            node.markStopped(reason);
        } else {
            node.fail(reason);
        }
    }

    private static boolean bothRunning(RuntimeStatus.Healthy h) {
        return h.el() instanceof LayerState.Running && h.cl() instanceof LayerState.Running;
    }

    private static boolean coreFault(RuntimeStatus.Healthy h) {
        return isFault(h.el()) || isFault(h.cl());
    }

    private static boolean coreTransient(RuntimeStatus.Healthy h) {
        return h.el() instanceof LayerState.Starting || h.cl() instanceof LayerState.Starting;
    }

    private static boolean isFault(LayerState s) {
        return s instanceof LayerState.Crashed || s instanceof LayerState.Absent;
    }

    private static boolean isElSynced(Optional<ExecutionSyncStatus> sync) {
        return sync.isPresent() && sync.get() instanceof ExecutionSyncStatus.Synced;
    }

    private static boolean hasPeers(OptionalInt peers) {
        return peers.isPresent() && peers.getAsInt() >= 1;
    }

    private static OptionalDouble elBlocksPerSecond(
            Optional<LastObservation> previous,
            Optional<ExecutionSyncStatus> current,
            Instant now) {
        if (previous.isEmpty() || current.isEmpty()) {
            return OptionalDouble.empty();
        }
        if (!(current.get() instanceof ExecutionSyncStatus.Syncing curSyncing)) {
            return OptionalDouble.empty();
        }
        if (previous.get().elSync().isEmpty()
                || !(previous.get().elSync().get() instanceof ExecutionSyncStatus.Syncing prv)) {
            return OptionalDouble.empty();
        }
        double dtSeconds = Duration.between(previous.get().observedAt(), now).toMillis() / 1000.0d;
        if (dtSeconds <= 0.0d) {
            return OptionalDouble.empty();
        }
        long delta = curSyncing.currentBlock() - prv.currentBlock();
        if (delta < 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(delta / dtSeconds);
    }

    private static OptionalDouble clSlotsPerSecond(
            Optional<LastObservation> previous,
            Optional<ConsensusSyncStatus> current,
            Instant now) {
        if (previous.isEmpty() || current.isEmpty()) {
            return OptionalDouble.empty();
        }
        if (!(current.get() instanceof ConsensusSyncStatus.Syncing curSyncing)) {
            return OptionalDouble.empty();
        }
        if (previous.get().clSync().isEmpty()
                || !(previous.get().clSync().get() instanceof ConsensusSyncStatus.Syncing prv)) {
            return OptionalDouble.empty();
        }
        double dtSeconds = Duration.between(previous.get().observedAt(), now).toMillis() / 1000.0d;
        if (dtSeconds <= 0.0d) {
            return OptionalDouble.empty();
        }
        long curCaughtUp = curSyncing.headSlot() - curSyncing.syncDistance();
        long prvCaughtUp = prv.headSlot() - prv.syncDistance();
        long delta = curCaughtUp - prvCaughtUp;
        if (delta < 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(delta / dtSeconds);
    }

    private static String degradedReason(ExecutionSyncStatus elSync, int peers) {
        if (peers == 0) {
            return "no peers";
        }
        if (!(elSync instanceof ExecutionSyncStatus.Synced)) {
            return "EL sync regressed: " + elSync.getClass().getSimpleName();
        }
        return null;
    }
}

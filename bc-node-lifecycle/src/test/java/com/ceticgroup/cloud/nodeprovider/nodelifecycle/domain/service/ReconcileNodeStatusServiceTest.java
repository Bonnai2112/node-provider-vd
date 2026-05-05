package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ConsensusSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ExecutionSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LastObservation;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LayerState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconcileNodeStatusServiceTest {

    private static final DeploymentRef REF = new DeploymentRef("{\"k\":\"v\"}");
    private static final JsonRpcEndpoint ENDPOINT =
            new JsonRpcEndpoint(URI.create("http://localhost:30100"));
    private static final URI CL_ENDPOINT = URI.create("http://localhost:30102/");

    @Mock private NodeRepository repository;
    @Mock private NodeOrchestrationPort orchestration;
    @Mock private BlockchainProbePort probe;
    @Mock private DomainEventPublisher publisher;

    private ReconcileNodeStatusService service;

    @BeforeEach
    void setUp() {
        service = new ReconcileNodeStatusService(repository, orchestration, probe, publisher);
    }

    @Test
    void reconcileAll_should_skipNode_when_deploymentRefIsNull() {
        Node node = newNodeIn(new NodeStatus.Requested(), null);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        verify(orchestration, never()).getDeploymentStatus(any());
        verify(repository, never()).save(any());
    }

    @Test
    void reconcileAll_should_transitionToSyncing_when_provisioningAndBothLayersRunning() {
        Node node = newNodeIn(new NodeStatus.Provisioning(), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.NotSyncing(),
                new ConsensusSyncStatus.NotSyncing(),
                0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Syncing.class);
        verify(repository).save(node);
    }

    @Test
    void reconcileAll_should_stayInProvisioning_when_oneLayerStillStarting() {
        Node node = newNodeIn(new NodeStatus.Provisioning(), REF);
        stubProbes(
                healthy(new LayerState.Running(), new LayerState.Starting()),
                new ExecutionSyncStatus.NotSyncing(),
                new ConsensusSyncStatus.NotSyncing(),
                0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Provisioning.class);
    }

    @Test
    void reconcileAll_should_failNode_when_provisioningAndElCrashed() {
        Node node = newNodeIn(new NodeStatus.Provisioning(), REF);
        stubProbes(
                healthy(new LayerState.Crashed("oom"), new LayerState.Running()),
                new ExecutionSyncStatus.NotSyncing(),
                new ConsensusSyncStatus.NotSyncing(),
                0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> {
                            assertThat(f.reason()).contains("EL=Crashed(oom)");
                            assertThat(f.reason()).contains("CL=Running");
                        });
        verify(repository).save(node);
    }

    @Test
    void reconcileAll_should_failNode_when_provisioningAndClCrashed() {
        Node node = newNodeIn(new NodeStatus.Provisioning(), REF);
        stubProbes(
                healthy(new LayerState.Running(), new LayerState.Crashed("panic")),
                new ExecutionSyncStatus.NotSyncing(),
                new ConsensusSyncStatus.NotSyncing(),
                0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> assertThat(f.reason()).contains("CL=Crashed(panic)"));
    }

    @Test
    void reconcileAll_should_transitionToReady_when_syncingAndBothSyncedAndPeers() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Synced(),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Ready.class,
                        r -> assertThat(r.endpoint().uri()).isEqualTo(ENDPOINT.uri()));
    }

    @Test
    void reconcileAll_should_transitionToReady_when_elSyncedEvenIfClStillSyncing() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Syncing(1000, 50),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        // Ready criterion is relaxed to EL synced; CL state is no longer gating.
        assertThat(node.status()).isInstanceOf(NodeStatus.Ready.class);
    }

    @Test
    void reconcileAll_should_stayInSyncing_when_clSyncedButElStillSyncing() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.Syncing(100, 50),
                new ConsensusSyncStatus.Synced(),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Syncing.class);
    }

    @Test
    void reconcileAll_should_failNode_when_syncingAndElCrashed() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(
                healthy(new LayerState.Crashed("EL panic"), new LayerState.Running()),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Synced(),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> assertThat(f.reason()).contains("EL=Crashed(EL panic)"));
    }

    @Test
    void reconcileAll_should_degrade_when_readyAndElCrashed() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(
                healthy(new LayerState.Crashed("disk"), new LayerState.Running()),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Synced(),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Degraded.class,
                        d -> assertThat(d.reason()).contains("EL=Crashed(disk)"));
    }

    @Test
    void reconcileAll_should_degrade_when_readyAndClRestarting() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(
                healthy(new LayerState.Running(), new LayerState.Starting()),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Synced(),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Degraded.class,
                        d -> assertThat(d.reason()).contains("CL=Starting"));
    }

    @Test
    void reconcileAll_should_degrade_when_readyAndPeersDropToZero() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Synced(),
                0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Degraded.class);
    }

    @Test
    void reconcileAll_should_degrade_when_readyAndElSyncRegresses() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.NotSyncing(),
                new ConsensusSyncStatus.Synced(),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Degraded.class,
                        d -> assertThat(d.reason()).contains("EL sync regressed"));
    }

    @Test
    void reconcileAll_should_stayInReady_when_clSyncRegressesButElRemainsSynced() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Syncing(1000, 100),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        // Ready criterion is gated on EL only; a transient CL regression shouldn't downgrade.
        assertThat(node.status()).isInstanceOf(NodeStatus.Ready.class);
    }

    @Test
    void reconcileAll_should_stayInSyncing_when_runtimeRunningButProbeUnreachable() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubUnreachableProbes(bothRunning());
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Syncing.class);
    }

    @Test
    void reconcileAll_should_stayInReady_when_runtimeRunningButProbeUnreachable() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubUnreachableProbes(bothRunning());
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Ready.class);
    }

    @Test
    void reconcileAll_should_stayInDegraded_when_runtimeRunningButProbeUnreachable() {
        Node node = newNodeIn(new NodeStatus.Degraded("flaky"), REF);
        stubUnreachableProbes(bothRunning());
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Degraded.class);
    }

    @Test
    void reconcileAll_should_returnToReady_when_degradedAndBothSyncedAndPeers() {
        Node node = newNodeIn(new NodeStatus.Degraded("flaky"), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.Synced(),
                new ConsensusSyncStatus.Synced(),
                5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Ready.class);
    }

    @Test
    void reconcileAll_should_stayInDegraded_when_runtimeUnknown() {
        Node node = newNodeIn(new NodeStatus.Degraded("flaky"), REF);
        when(orchestration.getDeploymentStatus(REF)).thenReturn(new RuntimeStatus.Unknown());
        when(orchestration.endpointFor(REF)).thenReturn(Optional.empty());
        when(orchestration.clRestEndpointFor(REF)).thenReturn(Optional.empty());
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Degraded.class);
    }

    @Test
    void reconcileAll_should_computeElAndClVelocity_acrossTwoTicks() {
        Instant t0 = Instant.parse("2026-05-04T10:00:00Z");
        MutableClock clock = new MutableClock(t0);
        ReconcileNodeStatusService scoped =
                new ReconcileNodeStatusService(repository, orchestration, probe, publisher, clock);
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        when(repository.findNonTerminal()).thenReturn(List.of(node));
        when(orchestration.getDeploymentStatus(REF)).thenReturn(bothRunning());
        when(orchestration.endpointFor(REF)).thenReturn(Optional.of(ENDPOINT));
        when(orchestration.clRestEndpointFor(REF)).thenReturn(Optional.of(CL_ENDPOINT));
        when(probe.probePeers(ENDPOINT)).thenReturn(OptionalInt.of(5));
        when(probe.probeElSync(ENDPOINT))
                .thenReturn(Optional.of(new ExecutionSyncStatus.Syncing(200, 100)))
                .thenReturn(Optional.of(new ExecutionSyncStatus.Syncing(200, 110)));
        when(probe.probeClSync(CL_ENDPOINT))
                .thenReturn(Optional.of(new ConsensusSyncStatus.Syncing(1000, 100)))
                .thenReturn(Optional.of(new ConsensusSyncStatus.Syncing(1000, 80)));

        scoped.reconcileAll();
        // First tick has no previous observation → no velocity.
        assertThat(node.lastObservation().get().elBlocksPerSecond()).isEmpty();
        assertThat(node.lastObservation().get().clSlotsPerSecond()).isEmpty();

        clock.advance(Duration.ofSeconds(10));
        scoped.reconcileAll();
        // 110 - 100 = 10 blocks in 10s → 1.0 block/s
        assertThat(node.lastObservation().get().elBlocksPerSecond()).hasValue(1.0d);
        // CL caught up went from (1000-100)=900 to (1000-80)=920 in 10s → 2.0 slot/s
        assertThat(node.lastObservation().get().clSlotsPerSecond()).hasValue(2.0d);
    }

    @Test
    void reconcileAll_should_keepPreviousProbeValue_when_currentProbeFails() {
        Instant t0 = Instant.parse("2026-05-04T10:00:00Z");
        MutableClock clock = new MutableClock(t0);
        ReconcileNodeStatusService scoped =
                new ReconcileNodeStatusService(repository, orchestration, probe, publisher, clock);
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        when(repository.findNonTerminal()).thenReturn(List.of(node));
        when(orchestration.getDeploymentStatus(REF)).thenReturn(bothRunning());
        when(orchestration.endpointFor(REF)).thenReturn(Optional.of(ENDPOINT));
        when(orchestration.clRestEndpointFor(REF)).thenReturn(Optional.of(CL_ENDPOINT));
        when(probe.probePeers(ENDPOINT))
                .thenReturn(OptionalInt.of(7))
                .thenReturn(OptionalInt.empty());
        when(probe.probeElSync(ENDPOINT))
                .thenReturn(Optional.of(new ExecutionSyncStatus.Syncing(200, 100)))
                .thenReturn(Optional.empty());
        when(probe.probeClSync(CL_ENDPOINT))
                .thenReturn(Optional.of(new ConsensusSyncStatus.Syncing(1000, 100)))
                .thenReturn(Optional.empty());

        scoped.reconcileAll();
        clock.advance(Duration.ofSeconds(30));
        scoped.reconcileAll();

        LastObservation persisted = node.lastObservation().get();
        assertThat(persisted.elSync()).get().isInstanceOf(ExecutionSyncStatus.Syncing.class);
        assertThat(persisted.clSync()).get().isInstanceOf(ConsensusSyncStatus.Syncing.class);
        assertThat(persisted.peers()).hasValue(7);
    }

    @Test
    void reconcileAll_should_persistObservation_evenWhenNoTransition() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(
                bothRunning(),
                new ExecutionSyncStatus.Syncing(100, 42),
                new ConsensusSyncStatus.Syncing(1000, 50),
                3);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        verify(repository).save(node);
        assertThat(node.lastObservation()).isPresent();
        assertThat(node.lastObservation().get().peers()).hasValue(3);
    }

    private Node newNodeIn(NodeStatus status, DeploymentRef ref) {
        return Node.restore(
                new NodeId(UUID.randomUUID()),
                new OwnerId(UUID.randomUUID()),
                Network.HOODI,
                ClientPair.besuTeku(),
                NodeOptions.defaults(),
                status,
                ref);
    }

    private void stubProbes(
            RuntimeStatus runtime,
            ExecutionSyncStatus elSync,
            ConsensusSyncStatus clSync,
            int peers) {
        when(orchestration.getDeploymentStatus(REF)).thenReturn(runtime);
        when(orchestration.endpointFor(REF)).thenReturn(Optional.of(ENDPOINT));
        when(orchestration.clRestEndpointFor(REF)).thenReturn(Optional.of(CL_ENDPOINT));
        when(probe.probeElSync(ENDPOINT)).thenReturn(Optional.of(elSync));
        when(probe.probePeers(ENDPOINT)).thenReturn(OptionalInt.of(peers));
        when(probe.probeClSync(CL_ENDPOINT)).thenReturn(Optional.of(clSync));
    }

    private void stubUnreachableProbes(RuntimeStatus runtime) {
        when(orchestration.getDeploymentStatus(REF)).thenReturn(runtime);
        when(orchestration.endpointFor(REF)).thenReturn(Optional.of(ENDPOINT));
        when(orchestration.clRestEndpointFor(REF)).thenReturn(Optional.of(CL_ENDPOINT));
        when(probe.probeElSync(ENDPOINT)).thenReturn(Optional.empty());
        when(probe.probePeers(ENDPOINT)).thenReturn(OptionalInt.empty());
        when(probe.probeClSync(CL_ENDPOINT)).thenReturn(Optional.empty());
    }

    private static RuntimeStatus.Healthy bothRunning() {
        return RuntimeStatus.Healthy.of(new LayerState.Running(), new LayerState.Running());
    }

    private static RuntimeStatus.Healthy healthy(LayerState el, LayerState cl) {
        return RuntimeStatus.Healthy.of(el, cl);
    }

    private static com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint endpoint() {
        return new com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint(ENDPOINT.uri());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}

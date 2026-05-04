package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.SyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
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
    void reconcileAll_should_transitionToSyncing_when_provisioningAndContainerRunning() {
        Node node = newNodeIn(new NodeStatus.Provisioning(), REF);
        stubProbes(new RuntimeStatus.Running(), new SyncStatus.NotSyncing(), 0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Syncing.class);
        verify(repository).save(node);
    }

    @Test
    void reconcileAll_should_transitionToFailed_when_provisioningAndContainerCrashed() {
        Node node = newNodeIn(new NodeStatus.Provisioning(), REF);
        stubProbes(new RuntimeStatus.Crashed("oom"), new SyncStatus.NotSyncing(), 0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class, f -> assertThat(f.reason()).contains("oom"));
        verify(repository).save(node);
    }

    @Test
    void reconcileAll_should_transitionToReady_when_syncingAndSyncedAndPeers() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(new RuntimeStatus.Running(), new SyncStatus.Synced(), 5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Ready.class,
                        r -> assertThat(r.endpoint().uri()).isEqualTo(ENDPOINT.uri()));
        verify(repository).save(node);
    }

    @Test
    void reconcileAll_should_stayInSyncing_when_syncingButNotYetSynced() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(new RuntimeStatus.Running(), new SyncStatus.Syncing(100, 50), 5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Syncing.class);
        verify(repository, never()).save(any());
    }

    @Test
    void reconcileAll_should_failNode_when_syncingAndContainerCrashed() {
        Node node = newNodeIn(new NodeStatus.Syncing(), REF);
        stubProbes(new RuntimeStatus.Crashed("EL panic"), new SyncStatus.Synced(), 5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Failed.class);
        verify(repository).save(node);
    }

    @Test
    void reconcileAll_should_degrade_when_readyAndContainerCrashed() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(new RuntimeStatus.Crashed("disk"), new SyncStatus.Synced(), 5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Degraded.class);
        verify(repository).save(node);
    }

    @Test
    void reconcileAll_should_degrade_when_readyAndPeersDropToZero() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(new RuntimeStatus.Running(), new SyncStatus.Synced(), 0);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Degraded.class);
    }

    @Test
    void reconcileAll_should_degrade_when_readyAndSyncRegresses() {
        Node node = newNodeIn(new NodeStatus.Ready(endpoint()), REF);
        stubProbes(new RuntimeStatus.Running(), new SyncStatus.NotSyncing(), 5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Degraded.class);
    }

    @Test
    void reconcileAll_should_returnToReady_when_degradedAndSyncedAndPeers() {
        Node node = newNodeIn(new NodeStatus.Degraded("flaky"), REF);
        stubProbes(new RuntimeStatus.Running(), new SyncStatus.Synced(), 5);
        when(repository.findNonTerminal()).thenReturn(List.of(node));

        service.reconcileAll();

        assertThat(node.status()).isInstanceOf(NodeStatus.Ready.class);
    }

    private Node newNodeIn(NodeStatus status, DeploymentRef ref) {
        return Node.restore(
                new NodeId(UUID.randomUUID()),
                new OwnerId(UUID.randomUUID()),
                Network.HOODI,
                ClientPair.besuTeku(),
                status,
                ref);
    }

    private void stubProbes(RuntimeStatus runtime, SyncStatus sync, int peers) {
        when(orchestration.getDeploymentStatus(REF)).thenReturn(runtime);
        when(orchestration.endpointFor(REF)).thenReturn(Optional.of(ENDPOINT));
        when(probe.probeSync(ENDPOINT)).thenReturn(sync);
        when(probe.probePeers(ENDPOINT)).thenReturn(peers);
    }

    private static com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint endpoint() {
        return new com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint(ENDPOINT.uri());
    }
}

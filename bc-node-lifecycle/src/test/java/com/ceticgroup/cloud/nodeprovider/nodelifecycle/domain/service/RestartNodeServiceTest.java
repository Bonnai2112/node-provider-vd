package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.IllegalNodeTransitionException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestartNodeServiceTest {

    @Mock private NodeRepository repository;
    @Mock private NodeOrchestrationPort orchestration;
    @Mock private DomainEventPublisher publisher;

    private final Executor executor = Runnable::run;
    private RestartNodeService service;

    @BeforeEach
    void setUp() {
        service = new RestartNodeService(repository, orchestration, publisher, executor);
    }

    @Test
    void restart_should_callOrchestrationRestartAndTransitionToProvisioning_when_stopped() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node = stoppedNode(id, owner, ref);
        when(repository.findById(id)).thenReturn(Optional.of(node));
        when(orchestration.canRestart(ref)).thenReturn(true);

        service.restart(id, owner);

        then(orchestration).should().restart(ref);
        assertThat(node.status()).isInstanceOf(NodeStatus.Provisioning.class);
    }

    @Test
    void restart_should_throwNotFound_when_ownerMismatch() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        OwnerId other = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        Node node = stoppedNode(id, owner, new DeploymentRef("{}"));
        when(repository.findById(id)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.restart(id, other))
                .isInstanceOf(NodeNotFoundException.class);
        then(orchestration).should(never()).restart(any());
    }

    @Test
    void restart_should_throwNotFound_when_nodeMissing() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restart(id, owner))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void restart_should_throwIllegalTransition_when_workdirMissing() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node = stoppedNode(id, owner, ref);
        when(repository.findById(id)).thenReturn(Optional.of(node));
        when(orchestration.canRestart(ref)).thenReturn(false);

        assertThatThrownBy(() -> service.restart(id, owner))
                .isInstanceOf(IllegalNodeTransitionException.class)
                .hasMessageContaining("workdir");
        then(orchestration).should(never()).restart(any());
        // Status preserved when the precondition fails — no half-transition leaked.
        assertThat(node.status()).isInstanceOf(NodeStatus.Stopped.class);
    }

    @Test
    void restart_should_throwIllegalTransition_when_nodeNotStopped() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node =
                Node.restore(
                        id,
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults(),
                        new NodeStatus.Syncing(),
                        ref);
        when(repository.findById(id)).thenReturn(Optional.of(node));
        when(orchestration.canRestart(ref)).thenReturn(true);

        assertThatThrownBy(() -> service.restart(id, owner))
                .isInstanceOf(IllegalNodeTransitionException.class);
        then(orchestration).should(never()).restart(any());
    }

    @Test
    void restart_should_failNode_when_orchestrationRestartThrows() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node = stoppedNode(id, owner, ref);
        when(repository.findById(id)).thenReturn(Optional.of(node));
        when(orchestration.canRestart(ref)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("ethd up boom"))
                .when(orchestration)
                .restart(ref);

        service.restart(id, owner);

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> assertThat(f.reason()).contains("restart failed"));
    }

    private static Node stoppedNode(NodeId id, OwnerId owner, DeploymentRef ref) {
        return Node.restore(
                id,
                owner,
                Network.HOODI,
                ClientPair.besuTeku(),
                NodeOptions.defaults(),
                new NodeStatus.Stopped("containers exited"),
                ref);
    }
}

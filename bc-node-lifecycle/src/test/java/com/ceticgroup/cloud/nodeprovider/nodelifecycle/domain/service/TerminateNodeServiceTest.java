package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminateNodeServiceTest {

    @Mock private NodeRepository repository;
    @Mock private NodeOrchestrationPort orchestration;
    @Mock private DomainEventPublisher publisher;

    private final Executor executor = Runnable::run;
    private TerminateNodeService service;

    @BeforeEach
    void setUp() {
        service = new TerminateNodeService(repository, orchestration, publisher, executor);
    }

    @Test
    void terminate_should_callTearDownAndMarkTerminated_when_ownerMatches() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node =
                Node.restore(
                        id,
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults(),
                        new NodeStatus.Ready(new Endpoint(URI.create("http://x"))),
                        ref);
        when(repository.findById(id)).thenReturn(Optional.of(node));

        service.terminate(id, owner);

        then(orchestration).should().tearDown(ref);
        assertThat(node.status()).isInstanceOf(NodeStatus.Terminated.class);
    }

    @Test
    void terminate_should_throwNotFound_when_ownerMismatch() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        OwnerId other = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        Node node =
                Node.restore(
                        id,
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults(),
                        new NodeStatus.Provisioning(),
                        new DeploymentRef("{}"));
        when(repository.findById(id)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.terminate(id, other))
                .isInstanceOf(NodeNotFoundException.class);
        then(orchestration).should(never()).tearDown(any());
    }

    @Test
    void terminate_should_throwNotFound_when_nodeMissing() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.terminate(id, owner))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void terminate_should_failNode_when_tearDownThrows() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node =
                Node.restore(
                        id,
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults(),
                        new NodeStatus.Provisioning(),
                        ref);
        when(repository.findById(id)).thenReturn(Optional.of(node));
        org.mockito.Mockito.doThrow(new IllegalStateException("ethd timeout"))
                .when(orchestration)
                .tearDown(ref);

        service.terminate(id, owner);

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> assertThat(f.reason()).contains("teardown failed"));
    }
}

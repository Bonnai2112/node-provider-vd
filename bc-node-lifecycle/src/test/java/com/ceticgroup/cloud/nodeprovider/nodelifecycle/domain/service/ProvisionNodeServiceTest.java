package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeFailed;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeProvisioningStarted;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeRequested;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeOrchestrationPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionNodeServiceTest {

    @Mock private NodeRepository repository;
    @Mock private DomainEventPublisher publisher;
    @Mock private NodeOrchestrationPort orchestration;

    private ProvisionNodeService service;
    private final Executor noopExecutor = task -> {};
    private final Executor inlineExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        service = new ProvisionNodeService(repository, publisher, orchestration, noopExecutor);
    }

    @Test
    void provision_should_returnNewNodeId() {
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults());

        NodeId id = service.provision(command);

        assertThat(id).isNotNull();
        assertThat(id.value()).isNotNull();
    }

    @Test
    void provision_should_persistNodeInRequestedStatus() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        owner, Network.SEPOLIA, ClientPair.besuTeku(), NodeOptions.defaults());

        NodeId id = service.provision(command);

        ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
        verify(repository).save(captor.capture());

        Node saved = captor.getValue();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.owner()).isEqualTo(owner);
        assertThat(saved.network()).isEqualTo(Network.SEPOLIA);
        assertThat(saved.status()).isInstanceOf(NodeStatus.Requested.class);
    }

    @Test
    void provision_should_publishNodeRequestedEvent() {
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults());

        NodeId id = service.provision(command);

        ArgumentCaptor<NodeRequested> captor = ArgumentCaptor.forClass(NodeRequested.class);
        verify(publisher).publish(captor.capture());

        NodeRequested event = captor.getValue();
        assertThat(event.nodeId()).isEqualTo(id);
        assertThat(event.network()).isEqualTo(Network.HOODI);
    }

    @Test
    void provision_should_publishExactlyOneEvent_when_deployNotYetExecuted() {
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults());

        service.provision(command);

        verify(publisher, times(1)).publish(any());
    }

    @Test
    void provision_should_throw_when_commandIsNull() {
        assertThatThrownBy(() -> service.provision(null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(repository, publisher, orchestration);
    }

    @Test
    void provision_should_persistNodeOptions_when_provided() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeOptions opts =
                new NodeOptions(
                        true,
                        true,
                        "0xdeadbeef00000000000000000000000000000000",
                        Optional.of("CETIC-DEMO"),
                        Optional.empty(),
                        java.util.OptionalInt.empty());
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(owner, Network.HOODI, ClientPair.besuTeku(), opts);

        service.provision(command);

        ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
        verify(repository).save(captor.capture());
        Node saved = captor.getValue();
        assertThat(saved.options().validator()).isTrue();
        assertThat(saved.options().mevBoost()).isTrue();
        assertThat(saved.options().graffiti()).hasValue("CETIC-DEMO");
    }

    @Test
    void provision_should_transitionToProvisioning_when_asyncDeploySucceeds() {
        service = new ProvisionNodeService(repository, publisher, orchestration, inlineExecutor);
        DeploymentRef ref = new DeploymentRef("{\"workdir\":\"/tmp/x\"}");
        when(orchestration.deploy(any())).thenReturn(ref);
        when(repository.findById(any()))
                .thenAnswer(
                        inv -> {
                            NodeId nodeId = inv.getArgument(0);
                            return Optional.of(
                                    Node.restore(
                                            nodeId,
                                            new OwnerId(UUID.randomUUID()),
                                            Network.HOODI,
                                            ClientPair.besuTeku(),
                                            NodeOptions.defaults(),
                                            new NodeStatus.Requested(),
                                            null));
                        });
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults());

        service.provision(command);

        verify(orchestration).deploy(any());
        ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
        verify(repository, times(2)).save(captor.capture());
        Node provisioning = captor.getAllValues().get(1);
        assertThat(provisioning.status()).isInstanceOf(NodeStatus.Provisioning.class);
        assertThat(provisioning.deploymentRef()).isEqualTo(ref);
        verify(publisher).publish(any(NodeProvisioningStarted.class));
    }

    @Test
    void provision_should_failNode_when_asyncDeployThrows() {
        service = new ProvisionNodeService(repository, publisher, orchestration, inlineExecutor);
        when(orchestration.deploy(any())).thenThrow(new IllegalStateException("docker down"));
        when(repository.findById(any()))
                .thenAnswer(
                        inv -> {
                            NodeId nodeId = inv.getArgument(0);
                            return Optional.of(
                                    Node.restore(
                                            nodeId,
                                            new OwnerId(UUID.randomUUID()),
                                            Network.HOODI,
                                            ClientPair.besuTeku(),
                                            NodeOptions.defaults(),
                                            new NodeStatus.Requested(),
                                            null));
                        });
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults());

        service.provision(command);

        ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
        verify(repository, times(2)).save(captor.capture());
        Node failed = captor.getAllValues().get(1);
        assertThat(failed.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> assertThat(f.reason()).contains("docker down"));
        verify(publisher).publish(any(NodeFailed.class));
        verify(publisher, never()).publish(any(NodeProvisioningStarted.class));
    }
}

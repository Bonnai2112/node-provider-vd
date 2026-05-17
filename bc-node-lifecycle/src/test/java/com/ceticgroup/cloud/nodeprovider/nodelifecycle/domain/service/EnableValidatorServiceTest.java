package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorAlreadyEnabledException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnableValidatorServiceTest {

    private static final String FEE = "0x1111111111111111111111111111111111111111";

    @Mock private NodeRepository repository;
    @Mock private NodeOrchestrationPort orchestration;
    @Mock private DomainEventPublisher publisher;

    private final Executor executor = Runnable::run;
    private EnableValidatorService service;

    @BeforeEach
    void setUp() {
        service = new EnableValidatorService(repository, orchestration, publisher, executor);
    }

    @Test
    void enable_should_mutateOptionsAndCallOrchestration_when_inReady() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node = readyNode(id, owner, ref, NodeOptions.defaults());
        when(repository.findById(id)).thenReturn(Optional.of(node));

        service.enable(id, owner, FEE, Optional.of("g"));

        assertThat(node.options().validator()).isTrue();
        assertThat(node.options().feeRecipient()).isEqualTo(FEE);
        ArgumentCaptor<NodeSpec> spec = ArgumentCaptor.forClass(NodeSpec.class);
        then(orchestration).should().applyOptionsChange(eq(ref), spec.capture());
        assertThat(spec.getValue().options().validator()).isTrue();
    }

    @Test
    void enable_should_throwNotFound_when_ownerMismatch() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        OwnerId other = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        Node node = readyNode(id, owner, new DeploymentRef("{}"), NodeOptions.defaults());
        when(repository.findById(id)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.enable(id, other, FEE, Optional.empty()))
                .isInstanceOf(NodeNotFoundException.class);
        then(orchestration).should(never()).applyOptionsChange(any(), any());
    }

    @Test
    void enable_should_propagateValidatorAlreadyEnabled_andNotCallOrchestration() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        Node node =
                readyNode(
                        id,
                        owner,
                        new DeploymentRef("{}"),
                        NodeOptions.defaults().withValidator(FEE, Optional.empty()));
        when(repository.findById(id)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.enable(id, owner, FEE, Optional.empty()))
                .isInstanceOf(ValidatorAlreadyEnabledException.class);
        then(orchestration).should(never()).applyOptionsChange(any(), any());
    }

    @Test
    void enable_should_failNode_when_orchestrationThrows() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node = readyNode(id, owner, ref, NodeOptions.defaults());
        when(repository.findById(id)).thenReturn(Optional.of(node));
        org.mockito.Mockito.doThrow(new IllegalStateException("compose boom"))
                .when(orchestration)
                .applyOptionsChange(eq(ref), any());

        service.enable(id, owner, FEE, Optional.empty());

        assertThat(node.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> assertThat(f.reason()).contains("enable validator failed"));
    }

    private static Node readyNode(
            NodeId id, OwnerId owner, DeploymentRef ref, NodeOptions options) {
        return Node.restore(
                id,
                owner,
                Network.HOODI,
                ClientPair.besuTeku(),
                options,
                new NodeStatus.Ready(new Endpoint(URI.create("https://example/"))),
                ref);
    }
}

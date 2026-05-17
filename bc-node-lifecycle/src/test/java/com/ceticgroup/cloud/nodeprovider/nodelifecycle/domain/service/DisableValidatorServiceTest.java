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
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.MevBoostRequiresValidatorException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
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
class DisableValidatorServiceTest {

    private static final String FEE = "0x1111111111111111111111111111111111111111";

    @Mock private NodeRepository repository;
    @Mock private NodeOrchestrationPort orchestration;
    @Mock private DomainEventPublisher publisher;

    private final Executor executor = Runnable::run;
    private DisableValidatorService service;

    @BeforeEach
    void setUp() {
        service = new DisableValidatorService(repository, orchestration, publisher, executor);
    }

    @Test
    void disable_should_mutateOptionsAndCallOrchestration_when_validatorOnAndMevOff() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        DeploymentRef ref = new DeploymentRef("{}");
        Node node =
                readyNode(
                        id,
                        owner,
                        ref,
                        NodeOptions.defaults().withValidator(FEE, Optional.empty()));
        when(repository.findById(id)).thenReturn(Optional.of(node));

        service.disable(id, owner);

        assertThat(node.options().validator()).isFalse();
        then(orchestration).should().applyOptionsChange(eq(ref), any());
    }

    @Test
    void disable_should_propagateMevRequires_when_mevStillEnabled() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId id = new NodeId(UUID.randomUUID());
        Node node =
                readyNode(
                        id,
                        owner,
                        new DeploymentRef("{}"),
                        NodeOptions.defaults()
                                .withValidator(FEE, Optional.empty())
                                .withMevBoost("0.05", 90));
        when(repository.findById(id)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.disable(id, owner))
                .isInstanceOf(MevBoostRequiresValidatorException.class);
        then(orchestration).should(never()).applyOptionsChange(any(), any());
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

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeRequested;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionNodeServiceTest {

    @Mock private NodeRepository repository;

    @Mock private DomainEventPublisher publisher;

    @InjectMocks private ProvisionNodeService service;

    @Test
    void provision_should_returnNewNodeId() {
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()), Network.HOODI, ClientPair.besuTeku());

        NodeId id = service.provision(command);

        assertThat(id).isNotNull();
        assertThat(id.value()).isNotNull();
    }

    @Test
    void provision_should_persistNodeInRequestedStatus() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(owner, Network.SEPOLIA, ClientPair.besuTeku());

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
                        new OwnerId(UUID.randomUUID()), Network.HOODI, ClientPair.besuTeku());

        NodeId id = service.provision(command);

        ArgumentCaptor<NodeRequested> captor = ArgumentCaptor.forClass(NodeRequested.class);
        verify(publisher).publish(captor.capture());

        NodeRequested event = captor.getValue();
        assertThat(event.nodeId()).isEqualTo(id);
        assertThat(event.network()).isEqualTo(Network.HOODI);
    }

    @Test
    void provision_should_publishExactlyOneEvent() {
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()), Network.HOODI, ClientPair.besuTeku());

        service.provision(command);

        verify(publisher, times(1)).publish(any());
    }

    @Test
    void provision_should_throw_when_commandIsNull() {
        assertThatThrownBy(() -> service.provision(null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(repository, publisher);
    }

    @Test
    void provision_should_throw_when_executionLayerIsValidator() {
        ElClient validatorEL = org.mockito.Mockito.mock(ElClient.class);
        when(validatorEL.isValidator()).thenReturn(true);
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        new ClientPair(validatorEL, ClClient.TEKU));

        assertThatThrownBy(() -> service.provision(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validator");

        verifyNoInteractions(repository, publisher);
    }

    @Test
    void provision_should_throw_when_consensusLayerIsValidator() {
        ClClient validatorCL = org.mockito.Mockito.mock(ClClient.class);
        when(validatorCL.isValidator()).thenReturn(true);
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        new ClientPair(ElClient.BESU, validatorCL));

        assertThatThrownBy(() -> service.provision(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validator");

        verifyNoInteractions(repository, publisher);
    }
}

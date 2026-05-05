package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListNodesByOwnerServiceTest {

    @Mock private NodeRepository repository;

    @Test
    void listByOwner_should_delegateToRepository() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        Node node =
                Node.request(
                        new NodeId(UUID.randomUUID()),
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .defaults());
        when(repository.findByOwner(owner)).thenReturn(List.of(node));
        ListNodesByOwnerService service = new ListNodesByOwnerService(repository);

        List<Node> result = service.listByOwner(owner);

        assertThat(result).containsExactly(node);
    }
}

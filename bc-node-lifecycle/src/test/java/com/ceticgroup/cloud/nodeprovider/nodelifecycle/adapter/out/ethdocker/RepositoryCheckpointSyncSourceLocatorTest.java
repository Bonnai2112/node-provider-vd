package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepositoryCheckpointSyncSourceLocatorTest {

    @Mock private NodeRepository nodes;

    @Test
    void findFor_should_returnEmpty_when_noReadyNodeOnNetwork() {
        when(nodes.findReadyOnNetwork(Network.HOODI)).thenReturn(List.of());
        RepositoryCheckpointSyncSourceLocator locator =
                new RepositoryCheckpointSyncSourceLocator(nodes);

        Optional<URI> result = locator.findFor(Network.HOODI);

        assertThat(result).isEmpty();
    }

    @Test
    void findFor_should_returnUrlBuiltFromShortId_when_aReadyNodeExists() {
        UUID leaderId = UUID.fromString("deadbeef-90ab-cdef-1234-567890abcdef");
        Node leader = ready(leaderId);
        when(nodes.findReadyOnNetwork(Network.HOODI)).thenReturn(List.of(leader));
        RepositoryCheckpointSyncSourceLocator locator =
                new RepositoryCheckpointSyncSourceLocator(nodes);

        Optional<URI> result = locator.findFor(Network.HOODI);

        assertThat(result).hasValue(URI.create("http://node-deadbeef-consensus:5052"));
    }

    @Test
    void findFor_should_pickFirstReadyNode_when_multipleAvailable() {
        Node first = ready(UUID.fromString("aaaaaaaa-90ab-cdef-1234-567890abcdef"));
        Node second = ready(UUID.fromString("bbbbbbbb-90ab-cdef-1234-567890abcdef"));
        when(nodes.findReadyOnNetwork(Network.HOODI)).thenReturn(List.of(first, second));
        RepositoryCheckpointSyncSourceLocator locator =
                new RepositoryCheckpointSyncSourceLocator(nodes);

        Optional<URI> result = locator.findFor(Network.HOODI);

        assertThat(result).hasValue(URI.create("http://node-aaaaaaaa-consensus:5052"));
    }

    private static Node ready(UUID id) {
        return Node.restore(
                new NodeId(id),
                new OwnerId(UUID.randomUUID()),
                Network.HOODI,
                ClientPair.besuTeku(),
                NodeOptions.defaults(),
                new NodeStatus.Ready(new Endpoint(URI.create("http://localhost:8545"))),
                new DeploymentRef("{}"));
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListValidatorKeysServiceTest {

    @Mock private NodeRepository nodes;
    @Mock private ValidatorKeyRepository keys;

    @Test
    void listByNode_should_returnKeys_when_ownerMatches() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeId nodeId = new NodeId(UUID.randomUUID());
        Node node =
                Node.restore(
                        nodeId,
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults(),
                        new NodeStatus.Provisioning(),
                        null);
        ValidatorKey key =
                new ValidatorKey(UUID.randomUUID(), nodeId, "0x" + "a".repeat(96), Instant.now());
        when(nodes.findById(nodeId)).thenReturn(Optional.of(node));
        when(keys.findByNode(nodeId)).thenReturn(List.of(key));
        ListValidatorKeysService service = new ListValidatorKeysService(nodes, keys);

        List<ValidatorKey> result = service.listByNode(nodeId, owner);

        assertThat(result).containsExactly(key);
    }

    @Test
    void listByNode_should_throwNotFound_when_ownerMismatch() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        OwnerId other = new OwnerId(UUID.randomUUID());
        NodeId nodeId = new NodeId(UUID.randomUUID());
        Node node =
                Node.restore(
                        nodeId,
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        NodeOptions.defaults(),
                        new NodeStatus.Provisioning(),
                        null);
        when(nodes.findById(nodeId)).thenReturn(Optional.of(node));
        ListValidatorKeysService service = new ListValidatorKeysService(nodes, keys);

        assertThatThrownBy(() -> service.listByNode(nodeId, other))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void listByNode_should_throwNotFound_when_nodeMissing() {
        NodeId nodeId = new NodeId(UUID.randomUUID());
        when(nodes.findById(nodeId)).thenReturn(Optional.empty());
        ListValidatorKeysService service = new ListValidatorKeysService(nodes, keys);

        assertThatThrownBy(() -> service.listByNode(nodeId, new OwnerId(UUID.randomUUID())))
                .isInstanceOf(NodeNotFoundException.class);
    }
}

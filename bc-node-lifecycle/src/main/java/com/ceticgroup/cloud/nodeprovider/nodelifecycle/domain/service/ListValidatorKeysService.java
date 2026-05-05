package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ListValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyRepository;
import java.util.List;
import java.util.Objects;

public final class ListValidatorKeysService implements ListValidatorKeysUseCase {

    private final NodeRepository nodes;
    private final ValidatorKeyRepository keys;

    public ListValidatorKeysService(NodeRepository nodes, ValidatorKeyRepository keys) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    @Override
    public List<ValidatorKey> listByNode(NodeId nodeId, OwnerId requester) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(requester, "requester");
        Node node = nodes.findById(nodeId).orElseThrow(() -> new NodeNotFoundException(nodeId));
        if (!node.owner().equals(requester)) {
            throw new NodeNotFoundException(nodeId);
        }
        return keys.findByNode(nodeId);
    }
}

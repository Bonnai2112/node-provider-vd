package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.util.List;
import java.util.Optional;

public interface NodeRepository {

    void save(Node node);

    Optional<Node> findById(NodeId id);

    List<Node> findByOwner(OwnerId owner);

    List<Node> findNonTerminal();

    /**
     * Returns nodes already past initial sync (READY) on the given network. Used to elect a local
     * checkpoint-sync source for newly-created CL nodes — see {@code CheckpointSyncSourceLocator}.
     */
    List<Node> findReadyOnNetwork(Network network);
}

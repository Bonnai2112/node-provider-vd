package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.util.List;
import java.util.Optional;

public interface NodeRepository {

    void save(Node node);

    Optional<Node> findById(NodeId id);

    List<Node> findByOwner(OwnerId owner);
}

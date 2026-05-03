package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;

public interface GetNodeUseCase {

    Node getById(NodeId id);
}

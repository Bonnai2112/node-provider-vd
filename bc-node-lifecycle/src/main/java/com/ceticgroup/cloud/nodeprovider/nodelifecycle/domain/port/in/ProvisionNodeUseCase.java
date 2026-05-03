package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;

public interface ProvisionNodeUseCase {

    NodeId provision(ProvisionNodeCommand command);
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;

public interface NodeOrchestrationPort {

    DeploymentRef deploy(NodeSpec spec);

    void tearDown(DeploymentRef ref);

    RuntimeStatus getDeploymentStatus(DeploymentRef ref);
}

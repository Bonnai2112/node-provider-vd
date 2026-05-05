package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeSpec;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import java.net.URI;
import java.util.Optional;

public interface NodeOrchestrationPort {

    DeploymentRef deploy(NodeSpec spec);

    void tearDown(DeploymentRef ref);

    RuntimeStatus getDeploymentStatus(DeploymentRef ref);

    Optional<JsonRpcEndpoint> endpointFor(DeploymentRef ref);

    Optional<URI> clRestEndpointFor(DeploymentRef ref);
}

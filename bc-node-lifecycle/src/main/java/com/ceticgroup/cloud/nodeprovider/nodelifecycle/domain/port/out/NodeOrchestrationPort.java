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

    /**
     * True when the underlying workdir/volumes are still on disk so the deployment can be brought
     * back up without re-provisioning. False means the operator nuked the workdir (e.g. rm -rf
     * /var/lib/platform/nodes/{id}) and the node must be re-deployed from scratch.
     */
    boolean canRestart(DeploymentRef ref);

    /**
     * Re-launches the containers of an existing deployment in place. Idempotent: no port
     * re-allocation, no clone, no datadir template restore — just `docker compose up` against the
     * existing workdir.
     */
    void restart(DeploymentRef ref);
}

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

    /**
     * Endpoint advertised to external API consumers (bound to the host's public IP). May not be
     * reachable from the backend itself if the cloud provider doesn't support hairpin NAT — for
     * health probes use {@link #internalEndpointFor} instead.
     */
    Optional<JsonRpcEndpoint> endpointFor(DeploymentRef ref);

    /**
     * Loopback-bound endpoint used by the in-process reconciler to probe the EL. Always reachable
     * from the same VM since the container publishes on 0.0.0.0:{port}.
     */
    Optional<JsonRpcEndpoint> internalEndpointFor(DeploymentRef ref);

    /** Loopback-bound CL REST endpoint used by the reconciler. Not exposed via the public API. */
    Optional<URI> internalClRestEndpointFor(DeploymentRef ref);

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

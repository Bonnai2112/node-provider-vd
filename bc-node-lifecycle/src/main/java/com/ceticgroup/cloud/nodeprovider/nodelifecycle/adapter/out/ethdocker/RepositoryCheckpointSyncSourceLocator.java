package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.CheckpointSyncSourceLocator;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public class RepositoryCheckpointSyncSourceLocator implements CheckpointSyncSourceLocator {

    // eth-docker exposes the CL REST API on this port inside every consensus container,
    // regardless of the underlying client (lighthouse, teku, prysm, nimbus, lodestar). Cross-node
    // calls go over the shared docker network and target this internal port directly — no host
    // mapping is involved.
    static final int CL_REST_INTERNAL_PORT = 5052;

    private final NodeRepository nodes;

    public RepositoryCheckpointSyncSourceLocator(NodeRepository nodes) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
    }

    @Override
    public Optional<URI> findFor(Network network) {
        Objects.requireNonNull(network, "network");
        return nodes.findReadyOnNetwork(network).stream()
                .findFirst()
                .map(RepositoryCheckpointSyncSourceLocator::checkpointUrlFor);
    }

    static URI checkpointUrlFor(Node leader) {
        return URI.create("http://" + consensusAlias(leader.id()) + ":" + CL_REST_INTERNAL_PORT);
    }

    static String consensusAlias(NodeId id) {
        return "node-" + shortId(id) + "-consensus";
    }

    static String shortId(NodeId id) {
        return id.value().toString().substring(0, 8);
    }
}

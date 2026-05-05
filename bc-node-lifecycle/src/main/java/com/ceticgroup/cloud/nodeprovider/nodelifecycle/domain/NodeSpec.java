package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;

public record NodeSpec(
        NodeId nodeId, OwnerId owner, Network network, ClientPair clientPair, NodeOptions options) {

    public NodeSpec {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(clientPair, "clientPair");
        Objects.requireNonNull(options, "options");
    }
}

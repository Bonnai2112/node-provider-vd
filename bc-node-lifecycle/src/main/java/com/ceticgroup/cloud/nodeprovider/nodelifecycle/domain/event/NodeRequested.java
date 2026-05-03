package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.time.Instant;
import java.util.Objects;

public record NodeRequested(
        NodeId nodeId, OwnerId owner, Network network, ClientPair clientPair, Instant occurredAt)
        implements NodeDomainEvent {

    public NodeRequested {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(clientPair, "clientPair");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

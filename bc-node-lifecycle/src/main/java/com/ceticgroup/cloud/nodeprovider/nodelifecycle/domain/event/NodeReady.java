package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import java.time.Instant;
import java.util.Objects;

public record NodeReady(NodeId nodeId, Endpoint endpoint, Instant occurredAt)
        implements NodeDomainEvent {

    public NodeReady {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

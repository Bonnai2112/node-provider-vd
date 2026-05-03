package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import java.time.Instant;
import java.util.Objects;

public record NodeFailed(NodeId nodeId, String reason, Instant occurredAt)
        implements NodeDomainEvent {

    public NodeFailed {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

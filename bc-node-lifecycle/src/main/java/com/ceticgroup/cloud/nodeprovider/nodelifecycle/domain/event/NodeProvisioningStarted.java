package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import java.time.Instant;
import java.util.Objects;

public record NodeProvisioningStarted(NodeId nodeId, Instant occurredAt)
        implements NodeDomainEvent {

    public NodeProvisioningStarted {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import java.time.Instant;

public sealed interface NodeDomainEvent
        permits NodeRequested, NodeProvisioningStarted, NodeReady, NodeFailed {

    NodeId nodeId();

    Instant occurredAt();
}

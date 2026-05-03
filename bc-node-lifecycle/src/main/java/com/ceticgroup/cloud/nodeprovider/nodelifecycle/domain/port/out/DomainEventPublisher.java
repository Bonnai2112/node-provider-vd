package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;

public interface DomainEventPublisher {

    void publish(NodeDomainEvent event);
}

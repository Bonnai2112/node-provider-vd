package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.messaging;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringApplicationDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    SpringApplicationDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(NodeDomainEvent event) {
        delegate.publishEvent(event);
    }
}

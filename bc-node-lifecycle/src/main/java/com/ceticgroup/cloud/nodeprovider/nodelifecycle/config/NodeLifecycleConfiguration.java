package com.ceticgroup.cloud.nodeprovider.nodelifecycle.config;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GetNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.DomainEventPublisher;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.GetNodeService;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service.ProvisionNodeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NodeLifecycleConfiguration {

    @Bean
    ProvisionNodeUseCase provisionNodeUseCase(
            NodeRepository repository, DomainEventPublisher publisher) {
        return new ProvisionNodeService(repository, publisher);
    }

    @Bean
    GetNodeUseCase getNodeUseCase(NodeRepository repository) {
        return new GetNodeService(repository);
    }
}

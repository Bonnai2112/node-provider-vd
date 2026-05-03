package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GetNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.Objects;

public final class GetNodeService implements GetNodeUseCase {

    private final NodeRepository repository;

    public GetNodeService(NodeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Node getById(NodeId id) {
        Objects.requireNonNull(id, "id");
        return repository.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
    }
}

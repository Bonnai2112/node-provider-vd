package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ListNodesByOwnerUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.util.List;
import java.util.Objects;

public final class ListNodesByOwnerService implements ListNodesByOwnerUseCase {

    private final NodeRepository repository;

    public ListNodesByOwnerService(NodeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<Node> listByOwner(OwnerId owner) {
        Objects.requireNonNull(owner, "owner");
        return repository.findByOwner(owner);
    }
}

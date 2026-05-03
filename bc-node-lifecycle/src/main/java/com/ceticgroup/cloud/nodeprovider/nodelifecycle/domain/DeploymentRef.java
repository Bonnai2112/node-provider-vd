package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;
import java.util.UUID;

public record DeploymentRef(UUID id) {

    public DeploymentRef {
        Objects.requireNonNull(id, "id");
    }
}

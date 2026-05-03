package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;
import java.util.UUID;

public record NodeId(UUID value) {

    public NodeId {
        Objects.requireNonNull(value, "value");
    }
}

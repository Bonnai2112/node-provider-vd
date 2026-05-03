package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;
import java.util.UUID;

public record OwnerId(UUID value) {

    public OwnerId {
        Objects.requireNonNull(value, "value");
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;
import java.util.UUID;

public record KeyGenerationJobId(UUID value) {

    public KeyGenerationJobId {
        Objects.requireNonNull(value, "value");
    }
}

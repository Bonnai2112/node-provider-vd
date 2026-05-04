package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;

public record DeploymentRef(String payload) {

    public DeploymentRef {
        Objects.requireNonNull(payload, "payload");
        if (payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
    }
}

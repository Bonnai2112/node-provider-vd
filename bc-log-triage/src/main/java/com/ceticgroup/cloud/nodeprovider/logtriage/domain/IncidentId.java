package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.Objects;
import java.util.UUID;

public record IncidentId(UUID value) {

    public IncidentId {
        Objects.requireNonNull(value, "value");
    }

    public static IncidentId random() {
        return new IncidentId(UUID.randomUUID());
    }
}

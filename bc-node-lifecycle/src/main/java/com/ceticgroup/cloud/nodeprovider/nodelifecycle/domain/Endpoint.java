package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.net.URI;
import java.util.Objects;

public record Endpoint(URI uri) {

    public Endpoint {
        Objects.requireNonNull(uri, "uri");
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.net.URI;
import java.util.Objects;

public record JsonRpcEndpoint(URI uri) {

    public JsonRpcEndpoint {
        Objects.requireNonNull(uri, "uri");
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;

public record ClientPair(ElClient executionLayer, ClClient consensusLayer) {

    public ClientPair {
        Objects.requireNonNull(executionLayer, "executionLayer");
        Objects.requireNonNull(consensusLayer, "consensusLayer");
    }

    public static ClientPair besuTeku() {
        return new ClientPair(ElClient.BESU, ClClient.TEKU);
    }
}

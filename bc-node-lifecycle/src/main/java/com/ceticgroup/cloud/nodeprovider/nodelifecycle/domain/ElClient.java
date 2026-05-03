package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public enum ElClient {
    BESU,
    GETH,
    NETHERMIND,
    ERIGON;

    public boolean isValidator() {
        return false;
    }
}

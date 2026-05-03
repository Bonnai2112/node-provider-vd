package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public enum ClClient {
    TEKU,
    LIGHTHOUSE,
    PRYSM,
    NIMBUS,
    LODESTAR;

    public boolean isValidator() {
        return false;
    }
}

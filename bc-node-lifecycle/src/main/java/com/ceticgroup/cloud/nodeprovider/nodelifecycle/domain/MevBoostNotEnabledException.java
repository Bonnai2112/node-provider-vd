package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public final class MevBoostNotEnabledException extends RuntimeException {

    public MevBoostNotEnabledException() {
        super("MEV-Boost is not enabled on this node");
    }
}

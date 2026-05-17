package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public final class MevBoostAlreadyEnabledException extends RuntimeException {

    public MevBoostAlreadyEnabledException() {
        super("MEV-Boost is already enabled on this node");
    }
}

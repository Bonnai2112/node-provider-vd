package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public final class ValidatorAlreadyEnabledException extends RuntimeException {

    public ValidatorAlreadyEnabledException() {
        super("Validator is already enabled on this node");
    }
}

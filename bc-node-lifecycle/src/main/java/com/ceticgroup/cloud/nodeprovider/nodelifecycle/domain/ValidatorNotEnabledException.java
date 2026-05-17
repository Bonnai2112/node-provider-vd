package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public final class ValidatorNotEnabledException extends RuntimeException {

    public ValidatorNotEnabledException() {
        super("Validator is not enabled on this node");
    }
}

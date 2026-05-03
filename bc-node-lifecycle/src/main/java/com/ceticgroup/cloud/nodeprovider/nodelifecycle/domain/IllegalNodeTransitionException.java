package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public final class IllegalNodeTransitionException extends RuntimeException {

    public IllegalNodeTransitionException(String message) {
        super(message);
    }

    static IllegalNodeTransitionException from(NodeStatus current, String operation) {
        return new IllegalNodeTransitionException(
                "Cannot " + operation + " from " + current.getClass().getSimpleName() + " state");
    }
}

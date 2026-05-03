package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public final class NodeNotFoundException extends RuntimeException {

    private final NodeId id;

    public NodeNotFoundException(NodeId id) {
        super("Node not found: " + id.value());
        this.id = id;
    }

    public NodeId id() {
        return id;
    }
}

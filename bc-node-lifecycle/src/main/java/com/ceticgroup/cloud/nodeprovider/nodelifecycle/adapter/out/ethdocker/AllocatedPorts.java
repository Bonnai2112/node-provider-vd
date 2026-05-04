package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

public record AllocatedPorts(int elRpcPort, int elWsPort, int clRestPort) {

    public AllocatedPorts {
        requireValidPort(elRpcPort, "elRpcPort");
        requireValidPort(elWsPort, "elWsPort");
        requireValidPort(clRestPort, "clRestPort");
        if (elRpcPort == elWsPort || elRpcPort == clRestPort || elWsPort == clRestPort) {
            throw new IllegalArgumentException("ports must be distinct");
        }
    }

    private static void requireValidPort(int port, String name) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(name + " must be in [1, 65535]");
        }
    }
}

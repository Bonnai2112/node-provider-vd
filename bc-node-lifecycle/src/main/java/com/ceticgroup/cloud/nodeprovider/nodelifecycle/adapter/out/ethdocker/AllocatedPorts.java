package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.util.Set;

public record AllocatedPorts(
        int elRpcPort,
        int elWsPort,
        int elP2pPort,
        int erigonTorrentPort,
        int clRestPort,
        int clP2pPort,
        int clQuicPort) {

    public AllocatedPorts {
        requireValidPort(elRpcPort, "elRpcPort");
        requireValidPort(elWsPort, "elWsPort");
        requireValidPort(elP2pPort, "elP2pPort");
        requireValidPort(erigonTorrentPort, "erigonTorrentPort");
        requireValidPort(clRestPort, "clRestPort");
        requireValidPort(clP2pPort, "clP2pPort");
        requireValidPort(clQuicPort, "clQuicPort");
        Set<Integer> distinct =
                Set.of(
                        elRpcPort,
                        elWsPort,
                        elP2pPort,
                        erigonTorrentPort,
                        clRestPort,
                        clP2pPort,
                        clQuicPort);
        if (distinct.size() != 7) {
            throw new IllegalArgumentException("ports must be distinct");
        }
    }

    private static void requireValidPort(int port, String name) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(name + " must be in [1, 65535]");
        }
    }
}

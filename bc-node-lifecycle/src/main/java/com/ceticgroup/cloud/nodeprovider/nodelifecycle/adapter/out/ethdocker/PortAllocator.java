package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class PortAllocator {

    public AllocatedPorts allocate() {
        List<ServerSocket> sockets = new ArrayList<>(3);
        try {
            for (int i = 0; i < 3; i++) {
                sockets.add(new ServerSocket(0));
            }
            return new AllocatedPorts(
                    sockets.get(0).getLocalPort(),
                    sockets.get(1).getLocalPort(),
                    sockets.get(2).getLocalPort());
        } catch (IOException e) {
            throw new IllegalStateException("failed to allocate ports", e);
        } finally {
            for (ServerSocket s : sockets) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}

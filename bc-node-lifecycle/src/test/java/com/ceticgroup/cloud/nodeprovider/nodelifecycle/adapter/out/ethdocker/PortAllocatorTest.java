package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PortAllocatorTest {

    private final PortAllocator allocator = new PortAllocator();

    @Test
    void allocate_should_returnSevenDistinctPorts_when_called() {
        AllocatedPorts ports = allocator.allocate();

        Set<Integer> distinct =
                Set.of(
                        ports.elRpcPort(),
                        ports.elWsPort(),
                        ports.elP2pPort(),
                        ports.erigonTorrentPort(),
                        ports.clRestPort(),
                        ports.clP2pPort(),
                        ports.clQuicPort());
        assertThat(distinct).hasSize(7);
    }

    @Test
    void allocate_should_returnPortsInValidRange_when_called() {
        AllocatedPorts ports = allocator.allocate();

        assertThat(ports.elRpcPort()).isBetween(1, 65535);
        assertThat(ports.elWsPort()).isBetween(1, 65535);
        assertThat(ports.elP2pPort()).isBetween(1, 65535);
        assertThat(ports.erigonTorrentPort()).isBetween(1, 65535);
        assertThat(ports.clRestPort()).isBetween(1, 65535);
        assertThat(ports.clP2pPort()).isBetween(1, 65535);
        assertThat(ports.clQuicPort()).isBetween(1, 65535);
    }
}

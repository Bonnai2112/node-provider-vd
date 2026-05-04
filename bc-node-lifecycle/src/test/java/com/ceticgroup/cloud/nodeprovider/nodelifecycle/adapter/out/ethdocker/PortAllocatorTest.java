package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortAllocatorTest {

    private final PortAllocator allocator = new PortAllocator();

    @Test
    void allocate_should_returnThreeDistinctPorts_when_called() {
        AllocatedPorts ports = allocator.allocate();

        assertThat(ports.elRpcPort()).isNotEqualTo(ports.elWsPort());
        assertThat(ports.elRpcPort()).isNotEqualTo(ports.clRestPort());
        assertThat(ports.elWsPort()).isNotEqualTo(ports.clRestPort());
    }

    @Test
    void allocate_should_returnPortsInValidRange_when_called() {
        AllocatedPorts ports = allocator.allocate();

        assertThat(ports.elRpcPort()).isBetween(1, 65535);
        assertThat(ports.elWsPort()).isBetween(1, 65535);
        assertThat(ports.clRestPort()).isBetween(1, 65535);
    }
}

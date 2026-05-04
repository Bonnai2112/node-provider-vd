package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AllocatedPortsTest {

    @Test
    void constructor_should_exposePorts_when_validValues() {
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102);

        assertThat(ports.elRpcPort()).isEqualTo(30100);
        assertThat(ports.elWsPort()).isEqualTo(30101);
        assertThat(ports.clRestPort()).isEqualTo(30102);
    }

    @Test
    void constructor_should_throw_when_elRpcOutOfRange() {
        assertThatThrownBy(() -> new AllocatedPorts(0, 30101, 30102))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AllocatedPorts(70000, 30101, 30102))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_should_throw_when_portsCollide() {
        assertThatThrownBy(() -> new AllocatedPorts(30100, 30100, 30102))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AllocatedPorts(30100, 30101, 30100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

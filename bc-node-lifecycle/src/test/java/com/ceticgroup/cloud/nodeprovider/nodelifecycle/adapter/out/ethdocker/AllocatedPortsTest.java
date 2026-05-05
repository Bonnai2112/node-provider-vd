package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AllocatedPortsTest {

    @Test
    void constructor_should_exposePorts_when_validValues() {
        AllocatedPorts ports = new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30106);

        assertThat(ports.elRpcPort()).isEqualTo(30100);
        assertThat(ports.elWsPort()).isEqualTo(30101);
        assertThat(ports.elP2pPort()).isEqualTo(30102);
        assertThat(ports.erigonTorrentPort()).isEqualTo(30103);
        assertThat(ports.clRestPort()).isEqualTo(30104);
        assertThat(ports.clP2pPort()).isEqualTo(30105);
        assertThat(ports.clQuicPort()).isEqualTo(30106);
    }

    @Test
    void constructor_should_throw_when_anyPortOutOfRange() {
        assertThatThrownBy(() -> new AllocatedPorts(0, 30101, 30102, 30103, 30104, 30105, 30106))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 70000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_should_throw_when_anyPairCollides() {
        assertThatThrownBy(
                        () -> new AllocatedPorts(30100, 30100, 30102, 30103, 30104, 30105, 30106))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new AllocatedPorts(30100, 30101, 30102, 30103, 30104, 30105, 30100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

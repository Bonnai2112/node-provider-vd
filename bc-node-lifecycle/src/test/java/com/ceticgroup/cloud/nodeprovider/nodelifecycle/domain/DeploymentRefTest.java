package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeploymentRefTest {

    @Test
    void constructor_should_throw_when_payloadIsNull() {
        assertThatThrownBy(() -> new DeploymentRef(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_throw_when_payloadIsBlank() {
        assertThatThrownBy(() -> new DeploymentRef("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_should_exposePayload_when_payloadIsProvided() {
        String payload = "{\"workdir\":\"/tmp/eth-docker\"}";

        DeploymentRef ref = new DeploymentRef(payload);

        assertThat(ref.payload()).isEqualTo(payload);
    }
}

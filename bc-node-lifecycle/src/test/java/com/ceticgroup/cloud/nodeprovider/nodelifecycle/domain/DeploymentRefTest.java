package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeploymentRefTest {

    @Test
    void constructor_should_throw_when_idIsNull() {
        assertThatThrownBy(() -> new DeploymentRef(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_exposeId_when_idIsProvided() {
        UUID id = UUID.randomUUID();

        DeploymentRef ref = new DeploymentRef(id);

        assertThat(ref.id()).isEqualTo(id);
    }
}

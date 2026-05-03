package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimeStatusTest {

    @Test
    void crashed_should_throw_when_reasonIsNull() {
        assertThatThrownBy(() -> new RuntimeStatus.Crashed(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void crashed_should_exposeReason_when_reasonIsProvided() {
        RuntimeStatus.Crashed crashed = new RuntimeStatus.Crashed("OOM killed");

        assertThat(crashed.reason()).isEqualTo("OOM killed");
    }

    @Test
    void switch_should_coverAllVariants_when_patternMatching() {
        RuntimeStatus[] all = {
            new RuntimeStatus.Running(),
            new RuntimeStatus.Starting(),
            new RuntimeStatus.Crashed("err"),
            new RuntimeStatus.Unknown()
        };

        for (RuntimeStatus s : all) {
            String label =
                    switch (s) {
                        case RuntimeStatus.Running r -> "running";
                        case RuntimeStatus.Starting st -> "starting";
                        case RuntimeStatus.Crashed c -> "crashed:" + c.reason();
                        case RuntimeStatus.Unknown u -> "unknown";
                    };
            assertThat(label).isNotBlank();
        }
    }
}

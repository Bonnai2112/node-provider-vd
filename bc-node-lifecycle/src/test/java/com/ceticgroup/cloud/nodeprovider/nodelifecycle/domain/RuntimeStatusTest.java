package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeStatusTest {

    @Test
    void healthy_should_throw_when_elIsNull() {
        assertThatThrownBy(
                        () ->
                                new RuntimeStatus.Healthy(
                                        null, new LayerState.Running(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void healthy_should_throw_when_clIsNull() {
        assertThatThrownBy(
                        () ->
                                new RuntimeStatus.Healthy(
                                        new LayerState.Running(), null, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void healthy_should_throw_when_validatorOptionalIsNull() {
        assertThatThrownBy(
                        () ->
                                new RuntimeStatus.Healthy(
                                        new LayerState.Running(), new LayerState.Running(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void healthy_should_exposeLayers_when_constructed() {
        RuntimeStatus.Healthy h =
                RuntimeStatus.Healthy.of(new LayerState.Running(), new LayerState.Starting());

        assertThat(h.el()).isInstanceOf(LayerState.Running.class);
        assertThat(h.cl()).isInstanceOf(LayerState.Starting.class);
        assertThat(h.validator()).isEmpty();
    }

    @Test
    void healthy_should_exposeValidator_when_provided() {
        RuntimeStatus.Healthy h =
                RuntimeStatus.Healthy.of(
                        new LayerState.Running(),
                        new LayerState.Running(),
                        new LayerState.Crashed("oom"));

        assertThat(h.validator()).get().isInstanceOf(LayerState.Crashed.class);
    }

    @Test
    void switch_should_coverAllVariants_when_patternMatching() {
        RuntimeStatus[] all = {
            RuntimeStatus.Healthy.of(new LayerState.Running(), new LayerState.Running()),
            new RuntimeStatus.Unknown()
        };

        for (RuntimeStatus s : all) {
            String label =
                    switch (s) {
                        case RuntimeStatus.Healthy h ->
                                "healthy:"
                                        + RuntimeStatus.formatLayers(h.el(), h.cl(), h.validator());
                        case RuntimeStatus.Unknown u -> "unknown";
                    };
            assertThat(label).isNotBlank();
        }
    }

    @Test
    void formatLayers_should_renderHumanReadableJoin_when_bothLayersProvided() {
        String text =
                RuntimeStatus.formatLayers(new LayerState.Running(), new LayerState.Crashed("oom"));

        assertThat(text).isEqualTo("EL=Running, CL=Crashed(oom)");
    }

    @Test
    void formatLayers_should_appendValidator_when_present() {
        String text =
                RuntimeStatus.formatLayers(
                        new LayerState.Running(),
                        new LayerState.Running(),
                        Optional.of(new LayerState.Starting()));

        assertThat(text).isEqualTo("EL=Running, CL=Running, VAL=Starting");
    }

    @Test
    void describe_should_emitNameOfEachLayerVariant() {
        assertThat(RuntimeStatus.describe(new LayerState.Running())).isEqualTo("Running");
        assertThat(RuntimeStatus.describe(new LayerState.Starting())).isEqualTo("Starting");
        assertThat(RuntimeStatus.describe(new LayerState.Crashed("dead")))
                .isEqualTo("Crashed(dead)");
        assertThat(RuntimeStatus.describe(new LayerState.Absent())).isEqualTo("Absent");
    }
}

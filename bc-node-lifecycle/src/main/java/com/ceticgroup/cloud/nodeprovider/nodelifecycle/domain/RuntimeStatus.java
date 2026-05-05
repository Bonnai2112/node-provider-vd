package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;
import java.util.Optional;

public sealed interface RuntimeStatus permits RuntimeStatus.Healthy, RuntimeStatus.Unknown {

    record Healthy(LayerState el, LayerState cl, Optional<LayerState> validator)
            implements RuntimeStatus {
        public Healthy {
            Objects.requireNonNull(el, "el");
            Objects.requireNonNull(cl, "cl");
            Objects.requireNonNull(validator, "validator");
        }

        public static Healthy of(LayerState el, LayerState cl) {
            return new Healthy(el, cl, Optional.empty());
        }

        public static Healthy of(LayerState el, LayerState cl, LayerState validator) {
            return new Healthy(el, cl, Optional.of(validator));
        }
    }

    record Unknown() implements RuntimeStatus {}

    static String describe(LayerState state) {
        return switch (state) {
            case LayerState.Running r -> "Running";
            case LayerState.Starting s -> "Starting";
            case LayerState.Crashed c -> "Crashed(" + c.reason() + ")";
            case LayerState.Absent a -> "Absent";
        };
    }

    static String formatLayers(LayerState el, LayerState cl) {
        return "EL=" + describe(el) + ", CL=" + describe(cl);
    }

    static String formatLayers(LayerState el, LayerState cl, Optional<LayerState> validator) {
        String base = formatLayers(el, cl);
        return validator.map(v -> base + ", VAL=" + describe(v)).orElse(base);
    }
}

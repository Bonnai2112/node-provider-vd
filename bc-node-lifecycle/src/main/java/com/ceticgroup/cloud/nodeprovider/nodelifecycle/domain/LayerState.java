package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;

public sealed interface LayerState
        permits LayerState.Running, LayerState.Starting, LayerState.Crashed, LayerState.Absent {

    record Running() implements LayerState {}

    record Starting() implements LayerState {}

    record Crashed(String reason) implements LayerState {
        public Crashed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Absent() implements LayerState {}
}

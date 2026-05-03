package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;

public sealed interface RuntimeStatus
        permits RuntimeStatus.Running,
                RuntimeStatus.Starting,
                RuntimeStatus.Crashed,
                RuntimeStatus.Unknown {

    record Running() implements RuntimeStatus {}

    record Starting() implements RuntimeStatus {}

    record Crashed(String reason) implements RuntimeStatus {
        public Crashed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Unknown() implements RuntimeStatus {}
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;

public sealed interface NodeStatus
        permits NodeStatus.Requested,
                NodeStatus.Provisioning,
                NodeStatus.Syncing,
                NodeStatus.Ready,
                NodeStatus.Degraded,
                NodeStatus.Stopped,
                NodeStatus.Terminating,
                NodeStatus.Terminated,
                NodeStatus.Failed {

    record Requested() implements NodeStatus {}

    record Provisioning() implements NodeStatus {}

    record Syncing() implements NodeStatus {}

    record Ready(Endpoint endpoint) implements NodeStatus {
        public Ready {
            Objects.requireNonNull(endpoint, "endpoint");
        }
    }

    record Degraded(String reason) implements NodeStatus {
        public Degraded {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Stopped(String reason) implements NodeStatus {
        public Stopped {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Terminating() implements NodeStatus {}

    record Terminated() implements NodeStatus {}

    record Failed(String reason) implements NodeStatus {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

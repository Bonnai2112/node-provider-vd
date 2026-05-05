package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public record LastObservation(
        Optional<ExecutionSyncStatus> elSync,
        Optional<ConsensusSyncStatus> clSync,
        OptionalDouble elBlocksPerSecond,
        OptionalDouble clSlotsPerSecond,
        OptionalInt peers,
        Instant observedAt) {

    public LastObservation {
        Objects.requireNonNull(elSync, "elSync");
        Objects.requireNonNull(clSync, "clSync");
        Objects.requireNonNull(elBlocksPerSecond, "elBlocksPerSecond");
        Objects.requireNonNull(clSlotsPerSecond, "clSlotsPerSecond");
        Objects.requireNonNull(peers, "peers");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}

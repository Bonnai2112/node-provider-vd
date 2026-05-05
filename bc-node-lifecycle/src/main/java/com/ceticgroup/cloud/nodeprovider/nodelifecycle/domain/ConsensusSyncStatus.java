package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.OptionalDouble;

public sealed interface ConsensusSyncStatus
        permits ConsensusSyncStatus.Synced,
                ConsensusSyncStatus.Syncing,
                ConsensusSyncStatus.NotSyncing {

    record Synced() implements ConsensusSyncStatus {}

    record Syncing(long headSlot, long syncDistance) implements ConsensusSyncStatus {
        public Syncing {
            if (headSlot < 0) {
                throw new IllegalArgumentException("headSlot must be >= 0");
            }
            if (syncDistance < 0) {
                throw new IllegalArgumentException("syncDistance must be >= 0");
            }
        }
    }

    record NotSyncing() implements ConsensusSyncStatus {}

    default OptionalDouble percentage() {
        return switch (this) {
            case Synced s -> OptionalDouble.of(100.0d);
            case Syncing sy -> {
                if (sy.headSlot() <= 0) {
                    yield OptionalDouble.empty();
                }
                long current = Math.max(0L, sy.headSlot() - sy.syncDistance());
                double pct = (double) current / (double) sy.headSlot() * 100.0d;
                yield OptionalDouble.of(Math.min(100.0d, Math.max(0.0d, pct)));
            }
            case NotSyncing n -> OptionalDouble.empty();
        };
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.OptionalDouble;

public sealed interface ExecutionSyncStatus
        permits ExecutionSyncStatus.Synced,
                ExecutionSyncStatus.Syncing,
                ExecutionSyncStatus.NotSyncing {

    record Synced() implements ExecutionSyncStatus {}

    record Syncing(long highestBlock, long currentBlock) implements ExecutionSyncStatus {
        public Syncing {
            if (highestBlock < 0) {
                throw new IllegalArgumentException("highestBlock must be >= 0");
            }
            if (currentBlock < 0) {
                throw new IllegalArgumentException("currentBlock must be >= 0");
            }
        }
    }

    record NotSyncing() implements ExecutionSyncStatus {}

    default OptionalDouble percentage() {
        return switch (this) {
            case Synced s -> OptionalDouble.of(100.0d);
            case Syncing sy -> {
                if (sy.highestBlock() <= 0) {
                    yield OptionalDouble.empty();
                }
                double pct = (double) sy.currentBlock() / (double) sy.highestBlock() * 100.0d;
                yield OptionalDouble.of(Math.min(100.0d, Math.max(0.0d, pct)));
            }
            case NotSyncing n -> OptionalDouble.empty();
        };
    }
}

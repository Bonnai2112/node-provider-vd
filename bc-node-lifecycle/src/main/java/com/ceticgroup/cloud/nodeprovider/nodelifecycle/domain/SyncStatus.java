package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public sealed interface SyncStatus
        permits SyncStatus.Synced, SyncStatus.Syncing, SyncStatus.NotSyncing {

    record Synced() implements SyncStatus {}

    record Syncing(long headSlot, long currentSlot) implements SyncStatus {
        public Syncing {
            if (headSlot < 0) {
                throw new IllegalArgumentException("headSlot must be >= 0");
            }
            if (currentSlot < 0) {
                throw new IllegalArgumentException("currentSlot must be >= 0");
            }
        }
    }

    record NotSyncing() implements SyncStatus {}
}

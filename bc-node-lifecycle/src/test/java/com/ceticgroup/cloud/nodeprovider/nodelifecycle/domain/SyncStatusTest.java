package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SyncStatusTest {

    @Test
    void syncing_should_exposeHeadAndCurrentSlot_when_constructed() {
        SyncStatus.Syncing syncing = new SyncStatus.Syncing(1000L, 500L);

        assertThat(syncing.headSlot()).isEqualTo(1000L);
        assertThat(syncing.currentSlot()).isEqualTo(500L);
    }

    @Test
    void syncing_should_throw_when_headSlotIsNegative() {
        assertThatThrownBy(() -> new SyncStatus.Syncing(-1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void syncing_should_throw_when_currentSlotIsNegative() {
        assertThatThrownBy(() -> new SyncStatus.Syncing(0L, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void switch_should_coverAllVariants_when_patternMatching() {
        SyncStatus[] all = {
            new SyncStatus.Synced(), new SyncStatus.Syncing(10L, 5L), new SyncStatus.NotSyncing()
        };

        for (SyncStatus s : all) {
            String label =
                    switch (s) {
                        case SyncStatus.Synced sd -> "synced";
                        case SyncStatus.Syncing sg -> "syncing:" + sg.currentSlot();
                        case SyncStatus.NotSyncing ns -> "notSyncing";
                    };
            assertThat(label).isNotBlank();
        }
    }
}

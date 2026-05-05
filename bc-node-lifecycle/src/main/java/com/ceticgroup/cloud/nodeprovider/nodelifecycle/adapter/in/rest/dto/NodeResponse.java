package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record NodeResponse(
        UUID id,
        UUID ownerId,
        String network,
        String executionLayer,
        String consensusLayer,
        String status,
        String endpoint,
        String reason,
        Options options,
        ElSync elSync,
        ClSync clSync,
        Integer peers,
        Instant lastObservedAt) {

    public record Options(
            boolean validator,
            boolean mevBoost,
            String feeRecipient,
            String graffiti,
            String mevMinBid,
            Integer mevBuildFactor) {}

    public record ElSync(
            String kind,
            Long currentBlock,
            Long highestBlock,
            Double percentage,
            Double blocksPerSecond,
            Instant etaCompleteAt) {}

    public record ClSync(
            String kind,
            Long headSlot,
            Long syncDistance,
            Double percentage,
            Double slotsPerSecond,
            Instant etaCompleteAt) {}
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import java.util.UUID;

public record NodeResponse(
        UUID id,
        UUID ownerId,
        String network,
        String executionLayer,
        String consensusLayer,
        String status,
        String endpoint,
        String reason) {}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNodeRequest(
        @NotBlank String network,
        @NotBlank String executionLayer,
        @NotBlank String consensusLayer,
        Boolean validator,
        Boolean mevBoost,
        String feeRecipient,
        String graffiti,
        String mevMinBid,
        Integer mevBuildFactor) {}

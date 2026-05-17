package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnableMevBoostRequest(
        @NotBlank String mevMinBid, @NotNull @Min(1) @Max(100) Integer mevBuildFactor) {}

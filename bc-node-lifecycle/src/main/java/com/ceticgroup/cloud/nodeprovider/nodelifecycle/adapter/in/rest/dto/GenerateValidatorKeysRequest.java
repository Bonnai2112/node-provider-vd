package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateValidatorKeysRequest(
        @NotNull @Min(1) Integer count, @NotBlank String withdrawalAddress) {}

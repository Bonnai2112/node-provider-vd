package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EnableValidatorRequest(
        @NotBlank
                @Pattern(
                        regexp = "^0x[0-9a-fA-F]{40}$",
                        message = "feeRecipient must be a 0x-prefixed 40-hex-char Ethereum address")
                String feeRecipient,
        String graffiti) {}

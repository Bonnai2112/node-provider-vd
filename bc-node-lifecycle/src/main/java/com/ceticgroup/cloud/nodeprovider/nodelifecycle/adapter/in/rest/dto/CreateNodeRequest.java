package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateNodeRequest(
        @NotNull UUID ownerId,
        @NotBlank String network,
        @NotBlank String executionLayer,
        @NotBlank String consensusLayer) {}

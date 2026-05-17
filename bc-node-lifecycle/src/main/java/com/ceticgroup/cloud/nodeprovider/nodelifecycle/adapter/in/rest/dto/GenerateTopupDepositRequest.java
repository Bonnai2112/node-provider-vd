package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record GenerateTopupDepositRequest(
        @NotNull BigDecimal amountEth, @NotBlank String keystorePassword) {}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import java.util.List;

public record GenerateValidatorKeysResponse(
        String mnemonic, String password, List<ValidatorKeyResponse> keys) {}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeyGenerationJobStatusResponse(
        Status status, GenerateValidatorKeysResponse result, String error) {

    public enum Status {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public static KeyGenerationJobStatusResponse running() {
        return new KeyGenerationJobStatusResponse(Status.RUNNING, null, null);
    }

    public static KeyGenerationJobStatusResponse succeeded(GenerateValidatorKeysResponse result) {
        return new KeyGenerationJobStatusResponse(Status.SUCCEEDED, result, null);
    }

    public static KeyGenerationJobStatusResponse failed(String error) {
        return new KeyGenerationJobStatusResponse(Status.FAILED, null, error);
    }
}

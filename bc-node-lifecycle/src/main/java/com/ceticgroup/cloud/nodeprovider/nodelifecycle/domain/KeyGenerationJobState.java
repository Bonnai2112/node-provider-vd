package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysResult;
import java.util.Objects;

public sealed interface KeyGenerationJobState
        permits KeyGenerationJobState.Running,
                KeyGenerationJobState.Succeeded,
                KeyGenerationJobState.Failed {

    record Running() implements KeyGenerationJobState {}

    record Succeeded(GenerateValidatorKeysResult result) implements KeyGenerationJobState {
        public Succeeded {
            Objects.requireNonNull(result, "result");
        }
    }

    record Failed(String message) implements KeyGenerationJobState {
        public Failed {
            Objects.requireNonNull(message, "message");
        }
    }
}

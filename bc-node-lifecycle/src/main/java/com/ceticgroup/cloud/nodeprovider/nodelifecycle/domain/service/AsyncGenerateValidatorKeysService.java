package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.PollKeyGenerationJobUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.StartGenerateValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.KeyGenerationJobRegistry;
import java.util.Objects;
import java.util.Optional;

public final class AsyncGenerateValidatorKeysService
        implements StartGenerateValidatorKeysUseCase, PollKeyGenerationJobUseCase {

    private final GenerateValidatorKeysUseCase synchronousGenerator;
    private final KeyGenerationJobRegistry registry;

    public AsyncGenerateValidatorKeysService(
            GenerateValidatorKeysUseCase synchronousGenerator, KeyGenerationJobRegistry registry) {
        this.synchronousGenerator =
                Objects.requireNonNull(synchronousGenerator, "synchronousGenerator");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public KeyGenerationJobId start(GenerateValidatorKeysCommand command) {
        Objects.requireNonNull(command, "command");
        return registry.submit(command.requester(), () -> synchronousGenerator.generate(command));
    }

    @Override
    public Optional<KeyGenerationJobState> poll(KeyGenerationJobId id, OwnerId requester) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requester, "requester");
        return registry.poll(id, requester);
    }
}

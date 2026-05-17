package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysResult;
import java.util.Optional;
import java.util.function.Supplier;

public interface KeyGenerationJobRegistry {

    /**
     * Submits an asynchronous key-generation task. The {@code work} supplier is executed off the
     * caller's thread; its result (or thrown exception) is stored in the registry and made
     * observable via {@link #poll(KeyGenerationJobId, OwnerId)}.
     */
    KeyGenerationJobId submit(OwnerId owner, Supplier<GenerateValidatorKeysResult> work);

    /**
     * Returns the current state, removing the entry when terminal (Succeeded or Failed) so the
     * mnemonic/password are not retained beyond the first authenticated read. Returns {@code
     * Optional.empty()} when the id is unknown OR the owner does not match the submitter (the two
     * are not distinguished by design: it prevents enumeration of foreign job ids).
     */
    Optional<KeyGenerationJobState> poll(KeyGenerationJobId id, OwnerId requester);
}

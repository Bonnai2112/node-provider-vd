package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.KeyGenerationJobState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.util.Optional;

public interface PollKeyGenerationJobUseCase {

    Optional<KeyGenerationJobState> poll(KeyGenerationJobId id, OwnerId requester);
}

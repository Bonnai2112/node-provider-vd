package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.util.Optional;

public interface EnableValidatorUseCase {

    void enable(NodeId id, OwnerId requester, String feeRecipient, Optional<String> graffiti);
}

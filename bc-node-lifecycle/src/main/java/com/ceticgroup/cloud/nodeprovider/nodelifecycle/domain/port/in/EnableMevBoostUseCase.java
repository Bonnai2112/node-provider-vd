package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;

public interface EnableMevBoostUseCase {

    void enable(NodeId id, OwnerId requester, String mevMinBid, int mevBuildFactor);
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import java.util.List;

public interface ValidatorKeyRepository {

    void saveAll(List<ValidatorKey> keys);

    List<ValidatorKey> findByNode(NodeId nodeId);
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;

public interface DownloadValidatorKeysUseCase {

    /**
     * Returns a zip archive containing all keystore-*.json files for the node's validator. The
     * keystores remain encrypted with the password the operator provided at generation/import time
     * — this method does NOT bundle the password.
     */
    byte[] downloadKeystores(NodeId nodeId, OwnerId requester);
}

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

    /**
     * Returns a UTF-8 JSON array containing every deposit datum produced by deposit-cli for the
     * node's validators. The launchpad consumes exactly this shape — the operator just uploads it
     * and signs the deposits with their own wallet.
     */
    byte[] downloadDepositData(NodeId nodeId, OwnerId requester);

    /** Returns the encrypted keystore JSON file for a single pubkey on the node's validator. */
    byte[] downloadKeystoreFor(NodeId nodeId, OwnerId requester, String pubkey);

    /**
     * Returns a single-entry JSON array (launchpad-shaped) containing the deposit datum matching
     * the given pubkey on the node's validator.
     */
    byte[] downloadDepositDataFor(NodeId nodeId, OwnerId requester, String pubkey);
}

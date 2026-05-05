package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;

public interface ValidatorKeyArchiverPort {

    /**
     * Reads all keystore-*.json files from the deployment workdir and packages them into a single
     * zip archive. The keystores stay encrypted with the password used at creation time — the
     * password itself is NOT included.
     */
    byte[] archive(DeploymentRef ref);

    /**
     * Reads every deposit_data-*.json file produced by deposit-cli in the deployment workdir and
     * concatenates their entries into a single JSON array (the launchpad expects exactly this
     * shape). The returned bytes are UTF-8 encoded JSON.
     */
    byte[] depositData(DeploymentRef ref);

    /**
     * Returns the encrypted keystore JSON file matching the given pubkey. Pubkey matching is
     * case-insensitive and tolerant to a leading {@code 0x}.
     */
    byte[] keystoreFor(DeploymentRef ref, String pubkey);

    /**
     * Returns a single-entry JSON array containing the deposit datum matching the given pubkey.
     * Returning an array (not a bare object) keeps the file directly uploadable to the launchpad.
     */
    byte[] depositDataFor(DeploymentRef ref, String pubkey);
}

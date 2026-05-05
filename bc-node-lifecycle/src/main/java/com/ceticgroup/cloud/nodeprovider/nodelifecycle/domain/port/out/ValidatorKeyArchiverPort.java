package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;

public interface ValidatorKeyArchiverPort {

    /**
     * Reads all keystore-*.json files from the deployment workdir and packages them into a single
     * zip archive. The keystores stay encrypted with the password used at creation time — the
     * password itself is NOT included.
     */
    byte[] archive(DeploymentRef ref);
}

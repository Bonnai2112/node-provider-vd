package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import java.math.BigDecimal;

public interface TopupDepositGeneratorPort {

    /**
     * Runs {@code partial-deposit} against the keystore matching {@code pubkey} in the deployment
     * workdir and returns the launchpad-shaped {@code deposit_data.json} payload (UTF-8 encoded
     * JSON array, single entry). The keystore must already exist on disk; the caller is responsible
     * for ensuring the validator is active on the target chain.
     */
    byte[] generate(
            DeploymentRef ref,
            String chain,
            String pubkey,
            BigDecimal amountEth,
            String keystorePassword);
}

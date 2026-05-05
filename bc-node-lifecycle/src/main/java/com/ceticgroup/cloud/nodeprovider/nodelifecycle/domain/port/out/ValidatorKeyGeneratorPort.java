package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import java.util.List;

public interface ValidatorKeyGeneratorPort {

    /**
     * Generates {@code count} validator keystores into the deployment workdir, each encrypted with
     * the given password. Returns the freshly-created BIP39 mnemonic (24 words) and the BLS pubkeys
     * of the generated validators. Caller is responsible for triggering the subsequent {@code ethd
     * keys import}.
     */
    GeneratedKeys generate(
            DeploymentRef ref, String chain, int count, String withdrawalAddress, String password);

    record GeneratedKeys(String mnemonic, List<String> pubkeys) {}
}

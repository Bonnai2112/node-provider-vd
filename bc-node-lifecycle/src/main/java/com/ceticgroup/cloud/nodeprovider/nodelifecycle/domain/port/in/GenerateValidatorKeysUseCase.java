package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import java.util.List;
import java.util.Objects;

public interface GenerateValidatorKeysUseCase {

    GenerateValidatorKeysResult generate(GenerateValidatorKeysCommand command);

    record GenerateValidatorKeysCommand(
            NodeId nodeId, OwnerId requester, int count, String withdrawalAddress) {

        private static final int MAX_VALIDATORS_PER_REQUEST = 100;

        public GenerateValidatorKeysCommand {
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(withdrawalAddress, "withdrawalAddress");
            if (count < 1 || count > MAX_VALIDATORS_PER_REQUEST) {
                throw new IllegalArgumentException(
                        "count must be in [1, " + MAX_VALIDATORS_PER_REQUEST + "]");
            }
            if (!isValidEthereumAddress(withdrawalAddress)) {
                throw new IllegalArgumentException(
                        "withdrawalAddress must be a 0x-prefixed 40-hex-char Ethereum address");
            }
        }

        private static boolean isValidEthereumAddress(String addr) {
            if (!(addr.startsWith("0x") || addr.startsWith("0X")) || addr.length() != 42) {
                return false;
            }
            for (int i = 2; i < addr.length(); i++) {
                char c = addr.charAt(i);
                boolean isHex =
                        (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                if (!isHex) {
                    return false;
                }
            }
            return true;
        }
    }

    record GenerateValidatorKeysResult(String mnemonic, String password, List<ValidatorKey> keys) {

        public GenerateValidatorKeysResult {
            Objects.requireNonNull(mnemonic, "mnemonic");
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(keys, "keys");
        }
    }
}

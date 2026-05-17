package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.math.BigDecimal;
import java.util.Objects;

public interface GenerateTopupDepositUseCase {

    /**
     * Generates a partial-deposit {@code deposit_data.json} payload for an already-active
     * validator. Returns the raw launchpad-shaped JSON bytes — the operator signs and submits the
     * deposit to the deposit contract themselves.
     */
    byte[] generate(GenerateTopupDepositCommand command);

    record GenerateTopupDepositCommand(
            NodeId nodeId,
            OwnerId requester,
            String pubkey,
            BigDecimal amountEth,
            String keystorePassword) {

        private static final int PUBKEY_HEX_LEN = 96;
        private static final BigDecimal MIN_AMOUNT_ETH = BigDecimal.ONE;

        public GenerateTopupDepositCommand {
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(pubkey, "pubkey");
            Objects.requireNonNull(amountEth, "amountEth");
            Objects.requireNonNull(keystorePassword, "keystorePassword");
            if (!isValidPubkey(pubkey)) {
                throw new IllegalArgumentException(
                        "pubkey must be a 0x-prefixed 96-hex-char BLS public key");
            }
            if (amountEth.compareTo(MIN_AMOUNT_ETH) < 0) {
                throw new IllegalArgumentException(
                        "amount must be >= 1 ETH (got " + amountEth.toPlainString() + ")");
            }
            if (keystorePassword.isBlank()) {
                throw new IllegalArgumentException("keystorePassword must not be blank");
            }
        }

        private static boolean isValidPubkey(String pubkey) {
            if (!pubkey.startsWith("0x") && !pubkey.startsWith("0X")) {
                return false;
            }
            if (pubkey.length() != PUBKEY_HEX_LEN + 2) {
                return false;
            }
            for (int i = 2; i < pubkey.length(); i++) {
                char c = pubkey.charAt(i);
                boolean isHex =
                        (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                if (!isHex) {
                    return false;
                }
            }
            return true;
        }
    }
}

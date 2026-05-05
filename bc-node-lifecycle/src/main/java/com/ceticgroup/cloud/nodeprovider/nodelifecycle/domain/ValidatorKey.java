package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ValidatorKey(UUID id, NodeId nodeId, String pubkey, Instant importedAt) {

    private static final int PUBKEY_HEX_LEN = 96;

    public ValidatorKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(pubkey, "pubkey");
        Objects.requireNonNull(importedAt, "importedAt");
        if (!isValidPubkey(pubkey)) {
            throw new IllegalArgumentException(
                    "pubkey must be a 0x-prefixed 96-hex-char BLS public key");
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

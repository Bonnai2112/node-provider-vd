package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record NodeOptions(
        boolean validator,
        boolean mevBoost,
        String feeRecipient,
        Optional<String> graffiti,
        Optional<String> mevMinBid,
        OptionalInt mevBuildFactor) {

    public static final String DEFAULT_FEE_RECIPIENT = "0x0000000000000000000000000000000000000000";
    public static final String DEFAULT_MEV_MIN_BID = "0.05";
    public static final int DEFAULT_MEV_BUILD_FACTOR = 90;

    public NodeOptions {
        Objects.requireNonNull(feeRecipient, "feeRecipient");
        Objects.requireNonNull(graffiti, "graffiti");
        Objects.requireNonNull(mevMinBid, "mevMinBid");
        Objects.requireNonNull(mevBuildFactor, "mevBuildFactor");
        if (feeRecipient.isBlank()) {
            throw new IllegalArgumentException("feeRecipient must not be blank");
        }
        if (mevBuildFactor.isPresent()) {
            int v = mevBuildFactor.getAsInt();
            if (v < 1 || v > 100) {
                throw new IllegalArgumentException("mevBuildFactor must be in [1, 100]");
            }
        }
    }

    public static NodeOptions defaults() {
        return new NodeOptions(
                false,
                false,
                DEFAULT_FEE_RECIPIENT,
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty());
    }
}

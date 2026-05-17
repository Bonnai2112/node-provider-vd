package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

public final class MevBoostRequiresValidatorException extends RuntimeException {

    public MevBoostRequiresValidatorException(String message) {
        super(message);
    }

    public static MevBoostRequiresValidatorException onEnable() {
        return new MevBoostRequiresValidatorException(
                "MEV-Boost requires the validator client to be enabled first");
    }

    public static MevBoostRequiresValidatorException onDisableValidator() {
        return new MevBoostRequiresValidatorException(
                "Cannot disable the validator client while MEV-Boost is still enabled — disable"
                        + " MEV-Boost first");
    }
}

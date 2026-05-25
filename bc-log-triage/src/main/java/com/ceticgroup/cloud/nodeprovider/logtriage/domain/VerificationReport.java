package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.Objects;

public record VerificationReport(boolean buildPassed, boolean testsPassed, String output) {

    public VerificationReport {
        Objects.requireNonNull(output, "output");
    }

    public boolean isSuccess() {
        return buildPassed && testsPassed;
    }
}

package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

public record Confidence(int percent) {

    public Confidence {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException(
                    "confidence percent must be in [0, 100], got " + percent);
        }
    }

    public boolean isAtLeast(Confidence threshold) {
        return this.percent >= threshold.percent;
    }

    public static Confidence of(int percent) {
        return new Confidence(percent);
    }
}

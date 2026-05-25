package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.Objects;

/**
 * Un {@link ProposedFix} qui a passé la vérification de périmètre (allowlist), le seuil de
 * confiance, et la phase de build+tests. Seul un {@code ValidatedFix} peut être présenté au port de
 * merge request.
 */
public record ValidatedFix(ProposedFix fix, VerificationReport verification) {

    public ValidatedFix {
        Objects.requireNonNull(fix, "fix");
        Objects.requireNonNull(verification, "verification");
        if (!verification.isSuccess()) {
            throw new IllegalStateException(
                    "cannot construct ValidatedFix on a failed verification");
        }
    }
}

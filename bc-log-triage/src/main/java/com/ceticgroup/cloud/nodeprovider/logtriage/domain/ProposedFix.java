package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.List;
import java.util.Objects;

/**
 * Sortie structurée du port de suggestion : un diagnostic court, un niveau de confiance, et la
 * liste des fichiers à modifier. Aucun champ "action" libre — la seule action possible est "ouvrir
 * une MR draft" et c'est le domaine qui la déclenche, jamais le contenu de cette sortie.
 */
public record ProposedFix(
        String rootCause,
        String summary,
        Confidence confidence,
        List<FilePatch> patches,
        String suggestedBranchName,
        String suggestedCommitMessage) {

    public ProposedFix {
        Objects.requireNonNull(rootCause, "rootCause");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(patches, "patches");
        Objects.requireNonNull(suggestedBranchName, "suggestedBranchName");
        Objects.requireNonNull(suggestedCommitMessage, "suggestedCommitMessage");
        if (patches.isEmpty()) {
            throw new IllegalArgumentException("proposed fix must contain at least one patch");
        }
        patches = List.copyOf(patches);
    }
}

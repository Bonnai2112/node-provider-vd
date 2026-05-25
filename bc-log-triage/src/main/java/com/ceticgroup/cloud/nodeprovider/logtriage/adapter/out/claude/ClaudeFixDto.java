package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.claude;

import java.util.List;

/**
 * Format imposé à la sortie structurée du modèle. Strict : pas de champ "action", pas de commande,
 * pas d'URL — seulement la description d'un patch. Le domaine reste seul maître de l'action
 * déclenchée (ouverture de MR).
 */
public record ClaudeFixDto(
        String rootCause,
        String summary,
        int confidencePercent,
        List<Patch> patches,
        String suggestedBranchName,
        String suggestedCommitMessage) {

    public record Patch(String path, String newContent) {}
}

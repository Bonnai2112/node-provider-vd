package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.Objects;

/**
 * Un fichier modifié par le correctif. {@code path} est relatif à la racine du repo. {@code
 * newContent} est le contenu intégral du fichier après application — pas un diff — pour rester
 * trivial à valider et appliquer côté adapter Git.
 */
public record FilePatch(String path, String newContent) {

    public FilePatch {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(newContent, "newContent");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
    }
}

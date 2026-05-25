package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.List;
import java.util.Objects;

/**
 * Liste de préfixes de chemins (relatifs à la racine du repo) autorisés pour un correctif. Tout
 * chemin qui ne commence par AUCUN préfixe est rejeté. Sécurité : empêche un correctif de toucher
 * la CI, des secrets, l'infra, etc.
 */
public record PathAllowlist(List<String> allowedPrefixes) {

    public PathAllowlist {
        Objects.requireNonNull(allowedPrefixes, "allowedPrefixes");
        if (allowedPrefixes.isEmpty()) {
            throw new IllegalArgumentException("allowedPrefixes must not be empty");
        }
        allowedPrefixes = List.copyOf(allowedPrefixes);
    }

    public boolean permits(String path) {
        Objects.requireNonNull(path, "path");
        String normalized = normalize(path);
        if (normalized.contains("..")) {
            return false;
        }
        return allowedPrefixes.stream()
                .map(PathAllowlist::normalize)
                .anyMatch(normalized::startsWith);
    }

    public boolean permitsAll(List<FilePatch> patches) {
        Objects.requireNonNull(patches, "patches");
        return patches.stream().map(FilePatch::path).allMatch(this::permits);
    }

    private static String normalize(String p) {
        String s = p.replace('\\', '/');
        while (s.startsWith("./")) {
            s = s.substring(2);
        }
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }
}

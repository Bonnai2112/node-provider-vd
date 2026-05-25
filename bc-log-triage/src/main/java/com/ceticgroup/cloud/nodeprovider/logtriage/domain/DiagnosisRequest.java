package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.List;
import java.util.Objects;

/**
 * Tout le contexte assemblé par le domaine pour demander un diagnostic au port de suggestion.
 * Contient l'allowlist : le port peut s'en servir pour cadrer son raisonnement, mais le domaine la
 * revérifie systématiquement sur la sortie.
 */
public record DiagnosisRequest(
        Incident incident,
        List<LogSnippet> logs,
        List<CodeSnippet> codeContext,
        PathAllowlist allowlist) {

    public DiagnosisRequest {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(logs, "logs");
        Objects.requireNonNull(codeContext, "codeContext");
        Objects.requireNonNull(allowlist, "allowlist");
        logs = List.copyOf(logs);
        codeContext = List.copyOf(codeContext);
    }
}

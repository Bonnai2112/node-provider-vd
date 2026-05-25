package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.List;

/**
 * Résultat exhaustif d'un triage. Riche par design : la couche application traduit chaque variant
 * en métriques sans avoir à inspecter le domaine.
 */
public sealed interface TriageOutcome {

    IncidentId incidentId();

    record MergeRequestOpened(IncidentId incidentId, MergeRequestRef ref, Confidence confidence)
            implements TriageOutcome {}

    record RejectedKillSwitchActive(IncidentId incidentId) implements TriageOutcome {}

    record RejectedQuotaExceeded(IncidentId incidentId, int dailyQuota) implements TriageOutcome {}

    record RejectedNoFixProposed(IncidentId incidentId) implements TriageOutcome {}

    record RejectedOutOfScope(IncidentId incidentId, List<String> offendingPaths)
            implements TriageOutcome {
        public RejectedOutOfScope {
            offendingPaths = List.copyOf(offendingPaths);
        }
    }

    record RejectedLowConfidence(IncidentId incidentId, Confidence actual, Confidence threshold)
            implements TriageOutcome {}

    record RejectedVerificationFailed(IncidentId incidentId, VerificationReport report)
            implements TriageOutcome {}

    record Failed(IncidentId incidentId, String reason) implements TriageOutcome {}
}

package com.ceticgroup.cloud.nodeprovider.logtriage.domain.service;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.CodeSnippet;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Confidence;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.DiagnosisRequest;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.FilePatch;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.IncidentId;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.LogSnippet;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.MergeRequestRef;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.PathAllowlist;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ProposedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.TriageOutcome;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ValidatedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.VerificationReport;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.in.HandleIncidentUseCase;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.CodeContextPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FeatureTogglePort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixSuggestionPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixVerificationPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.LogRetrievalPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestQuotaPort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TriageService implements HandleIncidentUseCase {

    private final LogRetrievalPort logRetrieval;
    private final CodeContextPort codeContext;
    private final FixSuggestionPort fixSuggestion;
    private final FixVerificationPort fixVerification;
    private final MergeRequestPort mergeRequest;
    private final FeatureTogglePort featureToggle;
    private final MergeRequestQuotaPort quota;
    private final PathAllowlist allowlist;
    private final Confidence confidenceThreshold;

    public TriageService(
            LogRetrievalPort logRetrieval,
            CodeContextPort codeContext,
            FixSuggestionPort fixSuggestion,
            FixVerificationPort fixVerification,
            MergeRequestPort mergeRequest,
            FeatureTogglePort featureToggle,
            MergeRequestQuotaPort quota,
            PathAllowlist allowlist,
            Confidence confidenceThreshold) {
        this.logRetrieval = Objects.requireNonNull(logRetrieval, "logRetrieval");
        this.codeContext = Objects.requireNonNull(codeContext, "codeContext");
        this.fixSuggestion = Objects.requireNonNull(fixSuggestion, "fixSuggestion");
        this.fixVerification = Objects.requireNonNull(fixVerification, "fixVerification");
        this.mergeRequest = Objects.requireNonNull(mergeRequest, "mergeRequest");
        this.featureToggle = Objects.requireNonNull(featureToggle, "featureToggle");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.allowlist = Objects.requireNonNull(allowlist, "allowlist");
        this.confidenceThreshold =
                Objects.requireNonNull(confidenceThreshold, "confidenceThreshold");
    }

    @Override
    public TriageOutcome handle(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        IncidentId id = incident.id();

        if (!featureToggle.isLogTriageEnabled()) {
            return new TriageOutcome.RejectedKillSwitchActive(id);
        }

        try {
            List<LogSnippet> logs = logRetrieval.retrieveAround(incident);
            List<CodeSnippet> code = codeContext.gatherFor(incident, logs);
            DiagnosisRequest request = new DiagnosisRequest(incident, logs, code, allowlist);

            Optional<ProposedFix> proposal = fixSuggestion.propose(request);
            if (proposal.isEmpty()) {
                return new TriageOutcome.RejectedNoFixProposed(id);
            }
            ProposedFix fix = proposal.get();

            List<String> offending = offendingPaths(fix);
            if (!offending.isEmpty()) {
                return new TriageOutcome.RejectedOutOfScope(id, offending);
            }

            if (!fix.confidence().isAtLeast(confidenceThreshold)) {
                return new TriageOutcome.RejectedLowConfidence(
                        id, fix.confidence(), confidenceThreshold);
            }

            VerificationReport report = fixVerification.verify(fix);
            if (!report.isSuccess()) {
                return new TriageOutcome.RejectedVerificationFailed(id, report);
            }

            if (!quota.tryReserve()) {
                return new TriageOutcome.RejectedQuotaExceeded(id, quota.dailyLimit());
            }

            ValidatedFix validated = new ValidatedFix(fix, report);
            MergeRequestRef ref = mergeRequest.openDraft(validated, incident);
            return new TriageOutcome.MergeRequestOpened(id, ref, fix.confidence());

        } catch (RuntimeException e) {
            return new TriageOutcome.Failed(id, safeMessage(e));
        }
    }

    private List<String> offendingPaths(ProposedFix fix) {
        return fix.patches().stream()
                .map(FilePatch::path)
                .filter(p -> !allowlist.permits(p))
                .toList();
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}

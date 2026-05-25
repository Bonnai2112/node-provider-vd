package com.ceticgroup.cloud.nodeprovider.logtriage.domain.service;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.CodeSnippet;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.DiagnosisRequest;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.MergeRequestRef;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ProposedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ValidatedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.VerificationReport;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.CodeContextPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FeatureTogglePort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixSuggestionPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixVerificationPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.LogRetrievalPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestPort;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestQuotaPort;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

final class Fakes {

    private Fakes() {}

    static LogRetrievalPort emptyLogs() {
        return incident -> List.of();
    }

    static CodeContextPort emptyCode() {
        return (incident, logs) -> List.of();
    }

    static CodeContextPort codeReturning(List<CodeSnippet> snippets) {
        return (incident, logs) -> snippets;
    }

    static FixSuggestionPort suggesting(ProposedFix fix) {
        return request -> Optional.of(fix);
    }

    static FixSuggestionPort noSuggestion() {
        return request -> Optional.empty();
    }

    static FixSuggestionPort throwing(RuntimeException e) {
        return request -> {
            throw e;
        };
    }

    static FixVerificationPort verifyingOk() {
        return fix -> new VerificationReport(true, true, "all green");
    }

    static FixVerificationPort verifyingFail() {
        return fix -> new VerificationReport(false, false, "tests failed");
    }

    static FeatureTogglePort enabled() {
        return () -> true;
    }

    static FeatureTogglePort disabled() {
        return () -> false;
    }

    static MergeRequestQuotaPort unlimitedQuota() {
        return new MergeRequestQuotaPort() {
            @Override
            public boolean tryReserve() {
                return true;
            }

            @Override
            public int dailyLimit() {
                return Integer.MAX_VALUE;
            }
        };
    }

    static MergeRequestQuotaPort exhaustedQuota(int dailyLimit) {
        return new MergeRequestQuotaPort() {
            @Override
            public boolean tryReserve() {
                return false;
            }

            @Override
            public int dailyLimit() {
                return dailyLimit;
            }
        };
    }

    static RecordingMergeRequestPort recordingMergeRequest() {
        return new RecordingMergeRequestPort();
    }

    static MergeRequestPort failingMergeRequest(RuntimeException e) {
        return (fix, incident) -> {
            throw e;
        };
    }

    static final class RecordingMergeRequestPort implements MergeRequestPort {
        final AtomicInteger calls = new AtomicInteger();
        DiagnosisRequest lastRequest;
        ValidatedFix lastFix;
        Incident lastIncident;

        @Override
        public MergeRequestRef openDraft(ValidatedFix fix, Incident incident) {
            calls.incrementAndGet();
            lastFix = fix;
            lastIncident = incident;
            return new MergeRequestRef(
                    "group/proj",
                    42L,
                    URI.create("https://gitlab.example/group/proj/-/merge_requests/42"));
        }
    }
}

package com.ceticgroup.cloud.nodeprovider.logtriage.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Confidence;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.FilePatch;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.IncidentId;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.PathAllowlist;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ProposedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.TriageOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TriageServiceTest {

    private static final PathAllowlist ALLOWLIST =
            new PathAllowlist(List.of("src/main/java/", "src/test/java/"));
    private static final Confidence THRESHOLD = Confidence.of(70);

    @Test
    void handle_should_returnKillSwitchActive_when_featureDisabled() {
        TriageService service =
                service(Fakes.disabled(), Fakes.unlimitedQuota(), Fakes.suggesting(fix(80)));

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome).isInstanceOf(TriageOutcome.RejectedKillSwitchActive.class);
    }

    @Test
    void handle_should_returnNoFix_when_suggestionPortReturnsEmpty() {
        TriageService service =
                new TriageService(
                        Fakes.emptyLogs(),
                        Fakes.emptyCode(),
                        Fakes.noSuggestion(),
                        Fakes.verifyingOk(),
                        Fakes.recordingMergeRequest(),
                        Fakes.enabled(),
                        Fakes.unlimitedQuota(),
                        ALLOWLIST,
                        THRESHOLD);

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome).isInstanceOf(TriageOutcome.RejectedNoFixProposed.class);
    }

    @Test
    void handle_should_returnOutOfScope_when_patchTouchesForbiddenPath() {
        ProposedFix fix =
                new ProposedFix(
                        "cause",
                        "summary",
                        Confidence.of(90),
                        List.of(new FilePatch("infra/secrets.yaml", "secret: pwn")),
                        "fix/x",
                        "fix x");
        TriageService service =
                service(Fakes.enabled(), Fakes.unlimitedQuota(), Fakes.suggesting(fix));

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        TriageOutcome.RejectedOutOfScope.class,
                        o -> assertThat(o.offendingPaths()).containsExactly("infra/secrets.yaml"));
    }

    @Test
    void handle_should_returnLowConfidence_when_belowThreshold() {
        TriageService service =
                service(Fakes.enabled(), Fakes.unlimitedQuota(), Fakes.suggesting(fix(50)));

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        TriageOutcome.RejectedLowConfidence.class,
                        o -> {
                            assertThat(o.actual()).isEqualTo(Confidence.of(50));
                            assertThat(o.threshold()).isEqualTo(THRESHOLD);
                        });
    }

    @Test
    void handle_should_returnVerificationFailed_when_buildOrTestsFail() {
        TriageService service =
                new TriageService(
                        Fakes.emptyLogs(),
                        Fakes.emptyCode(),
                        Fakes.suggesting(fix(90)),
                        Fakes.verifyingFail(),
                        Fakes.recordingMergeRequest(),
                        Fakes.enabled(),
                        Fakes.unlimitedQuota(),
                        ALLOWLIST,
                        THRESHOLD);

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome).isInstanceOf(TriageOutcome.RejectedVerificationFailed.class);
    }

    @Test
    void handle_should_returnQuotaExceeded_when_quotaReservationFails() {
        TriageService service =
                service(Fakes.enabled(), Fakes.exhaustedQuota(5), Fakes.suggesting(fix(90)));

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        TriageOutcome.RejectedQuotaExceeded.class,
                        o -> assertThat(o.dailyQuota()).isEqualTo(5));
    }

    @Test
    void handle_should_openMergeRequest_when_allChecksPass() {
        Fakes.RecordingMergeRequestPort merge = Fakes.recordingMergeRequest();
        TriageService service =
                new TriageService(
                        Fakes.emptyLogs(),
                        Fakes.emptyCode(),
                        Fakes.suggesting(fix(90)),
                        Fakes.verifyingOk(),
                        merge,
                        Fakes.enabled(),
                        Fakes.unlimitedQuota(),
                        ALLOWLIST,
                        THRESHOLD);

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome).isInstanceOf(TriageOutcome.MergeRequestOpened.class);
        assertThat(merge.calls.get()).isEqualTo(1);
    }

    @Test
    void handle_should_returnFailed_when_adapterThrowsRuntimeException() {
        TriageService service =
                new TriageService(
                        Fakes.emptyLogs(),
                        Fakes.emptyCode(),
                        Fakes.throwing(new IllegalStateException("claude down")),
                        Fakes.verifyingOk(),
                        Fakes.recordingMergeRequest(),
                        Fakes.enabled(),
                        Fakes.unlimitedQuota(),
                        ALLOWLIST,
                        THRESHOLD);

        TriageOutcome outcome = service.handle(incident());

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        TriageOutcome.Failed.class,
                        f -> assertThat(f.reason()).contains("claude down"));
    }

    @Test
    void handle_should_notReserveQuota_when_verificationFails() {
        Fakes.RecordingMergeRequestPort merge = Fakes.recordingMergeRequest();
        TriageService service =
                new TriageService(
                        Fakes.emptyLogs(),
                        Fakes.emptyCode(),
                        Fakes.suggesting(fix(90)),
                        Fakes.verifyingFail(),
                        merge,
                        Fakes.enabled(),
                        Fakes.exhaustedQuota(0),
                        ALLOWLIST,
                        THRESHOLD);

        TriageOutcome outcome = service.handle(incident());

        // Si la vérification échoue, on doit voir RejectedVerificationFailed, pas
        // RejectedQuotaExceeded
        // → le quota n'a pas été interrogé en premier.
        assertThat(outcome).isInstanceOf(TriageOutcome.RejectedVerificationFailed.class);
        assertThat(merge.calls.get()).isZero();
    }

    private TriageService service(
            com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FeatureTogglePort feature,
            com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestQuotaPort quota,
            com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixSuggestionPort
                    suggestion) {
        return new TriageService(
                Fakes.emptyLogs(),
                Fakes.emptyCode(),
                suggestion,
                Fakes.verifyingOk(),
                Fakes.recordingMergeRequest(),
                feature,
                quota,
                ALLOWLIST,
                THRESHOLD);
    }

    private static Incident incident() {
        return new Incident(
                IncidentId.random(),
                "HighErrorRate",
                "checkout-service",
                "elevated 500 rate",
                Instant.parse("2026-05-25T10:00:00Z"),
                Map.of("env", "prod"),
                Optional.empty(),
                Optional.empty());
    }

    private static ProposedFix fix(int confidence) {
        return new ProposedFix(
                "NullPointerException on null cart",
                "guard against null cart in CheckoutService",
                Confidence.of(confidence),
                List.of(
                        new FilePatch(
                                "src/main/java/com/example/CheckoutService.java",
                                "...new content...")),
                "fix/null-cart",
                "fix: guard against null cart");
    }
}

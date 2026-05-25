package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.in.web;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.TriageOutcome;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.in.HandleIncidentUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consomme la file d'incidents et appelle le use case de façon asynchrone. Vie liée à un thread
 * virtuel unique : un seul triage à la fois, simple et sérialisé. Les ports en aval sont eux- mêmes
 * synchrones — la résilience (retry, circuit breaker) est portée par les adapters.
 */
public final class IncidentQueueWorker {

    private static final Logger LOG = LoggerFactory.getLogger(IncidentQueueWorker.class);
    private static final long POLL_TIMEOUT_MS = 500L;

    private final IncidentQueue queue;
    private final HandleIncidentUseCase useCase;
    private final MeterRegistry meterRegistry;
    private volatile boolean running;
    private ExecutorService executor;

    public IncidentQueueWorker(
            IncidentQueue queue, HandleIncidentUseCase useCase, MeterRegistry meterRegistry) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @PostConstruct
    public void start() {
        running = true;
        executor =
                Executors.newSingleThreadExecutor(
                        r -> Thread.ofVirtual().name("log-triage-worker").unstarted(r));
        executor.submit(this::loop);
        LOG.info("IncidentQueueWorker started");
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        running = false;
        if (executor != null) {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        LOG.info("IncidentQueueWorker stopped");
    }

    private void loop() {
        while (running) {
            try {
                Incident incident = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (incident == null) {
                    continue;
                }
                TriageOutcome outcome = useCase.handle(incident);
                recordOutcome(outcome);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                LOG.error("triage loop swallowed exception", e);
            }
        }
    }

    private void recordOutcome(TriageOutcome outcome) {
        String variant = outcome.getClass().getSimpleName();
        Counter.builder("log_triage.outcome")
                .tag("variant", variant)
                .register(meterRegistry)
                .increment();
        if (LOG.isInfoEnabled()) {
            LOG.info("incident={} outcome={}", outcome.incidentId().value(), variant);
        }
    }
}

package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.in.web;

import com.ceticgroup.cloud.nodeprovider.logtriage.adapter.in.web.dto.GrafanaWebhookPayload;
import com.ceticgroup.cloud.nodeprovider.logtriage.config.LogTriageWebhookProperties;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.IncidentId;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/grafana")
class GrafanaWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(GrafanaWebhookController.class);

    private final IncidentQueue queue;
    private final LogTriageWebhookProperties properties;

    GrafanaWebhookController(IncidentQueue queue, LogTriageWebhookProperties properties) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @PostMapping
    ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Webhook-Token", required = false) String token,
            @RequestBody GrafanaWebhookPayload payload) {

        if (!constantTimeEquals(properties.token(), token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (payload == null || payload.alerts() == null || payload.alerts().isEmpty()) {
            return ResponseEntity.accepted().build();
        }

        int dropped = 0;
        for (GrafanaWebhookPayload.Alert alert : payload.alerts()) {
            if (!"firing".equalsIgnoreCase(alert.status())) {
                continue;
            }
            Incident incident = toIncident(alert);
            if (!queue.offer(incident)) {
                dropped++;
            }
        }
        if (dropped > 0) {
            LOG.warn("incident-queue full, dropped {} alert(s)", dropped);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.accepted().build();
    }

    private static Incident toIncident(GrafanaWebhookPayload.Alert alert) {
        Map<String, String> labels = alert.labels() == null ? Map.of() : alert.labels();
        Map<String, String> annotations =
                alert.annotations() == null ? Map.of() : alert.annotations();
        String alertName = labels.getOrDefault("alertname", "unknown-alert");
        String service =
                labels.getOrDefault("service", labels.getOrDefault("job", "unknown-service"));
        String summary =
                annotations.getOrDefault("summary", annotations.getOrDefault("description", ""));
        Instant detectedAt = alert.startsAt() == null ? Instant.now() : alert.startsAt();
        Optional<String> traceId = Optional.ofNullable(labels.get("trace_id"));
        Optional<URI> origin = parseUri(alert.generatorURL());

        return new Incident(
                IncidentId.random(),
                alertName,
                service,
                summary,
                detectedAt,
                labels,
                traceId,
                origin);
    }

    private static Optional<URI> parseUri(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI(value));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes();
        byte[] b = actual.getBytes();
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}

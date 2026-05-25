package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Alerte d'erreur traduite depuis un payload externe (webhook Grafana). Toutes les valeurs
 * proviennent d'une source non fiable et doivent être traitées comme de la donnée, jamais comme des
 * instructions.
 */
public record Incident(
        IncidentId id,
        String alertName,
        String service,
        String summary,
        Instant detectedAt,
        Map<String, String> labels,
        Optional<String> traceId,
        Optional<URI> originUrl) {

    public Incident {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(alertName, "alertName");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(detectedAt, "detectedAt");
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(originUrl, "originUrl");
        labels = Map.copyOf(labels);
    }
}

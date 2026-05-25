package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record LogSnippet(
        Instant timestamp, String level, String message, Map<String, String> labels) {

    public LogSnippet {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(labels, "labels");
        labels = Map.copyOf(labels);
    }
}

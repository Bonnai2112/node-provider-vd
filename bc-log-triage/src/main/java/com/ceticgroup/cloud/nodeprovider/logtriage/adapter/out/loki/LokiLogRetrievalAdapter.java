package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.loki;

import com.ceticgroup.cloud.nodeprovider.logtriage.config.LokiProperties;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.LogSnippet;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.LogRetrievalPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LokiLogRetrievalAdapter implements LogRetrievalPort {

    private static final Logger LOG = LoggerFactory.getLogger(LokiLogRetrievalAdapter.class);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final LokiProperties properties;

    public LokiLogRetrievalAdapter(
            HttpClient http, ObjectMapper mapper, LokiProperties properties) {
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public List<LogSnippet> retrieveAround(Incident incident) {
        Instant center = incident.detectedAt();
        Instant start = center.minus(properties.windowBefore());
        Instant end = center.plus(properties.windowAfter());

        String query = buildQuery(incident);
        URI uri = buildUri(query, start, end);

        try {
            HttpRequest request =
                    HttpRequest.newBuilder(uri).header("Accept", "application/json").GET().build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOG.warn(
                        "loki query_range non-2xx: status={} body={}",
                        response.statusCode(),
                        response.body());
                return List.of();
            }
            return parse(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("loki query failed: {}", e.toString());
            return List.of();
        }
    }

    private String buildQuery(Incident incident) {
        return String.format(
                "{service=\"%s\"} |~ \"(?i)error|exception|fatal\"", incident.service());
    }

    private URI buildUri(String query, Instant start, Instant end) {
        String base = properties.baseUrl().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url =
                base
                        + "/loki/api/v1/query_range?query="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&start="
                        + nanos(start)
                        + "&end="
                        + nanos(end)
                        + "&limit="
                        + properties.maxLines();
        return URI.create(url);
    }

    private static String nanos(Instant t) {
        return Long.toString(t.getEpochSecond() * 1_000_000_000L + t.getNano());
    }

    private List<LogSnippet> parse(String body) throws IOException {
        LokiQueryRangeResponse parsed = mapper.readValue(body, LokiQueryRangeResponse.class);
        List<LogSnippet> out = new ArrayList<>();
        if (parsed.data == null || parsed.data.result == null) {
            return out;
        }
        for (LokiStream stream : parsed.data.result) {
            Map<String, String> labels =
                    stream.stream == null ? Map.of() : Map.copyOf(stream.stream);
            String level = labels.getOrDefault("level", labels.getOrDefault("severity", "INFO"));
            if (stream.values == null) {
                continue;
            }
            for (List<String> entry : stream.values) {
                if (entry.size() < 2) {
                    continue;
                }
                Instant ts = parseNanos(entry.get(0));
                Map<String, String> entryLabels = new HashMap<>(labels);
                out.add(new LogSnippet(ts, level, entry.get(1), entryLabels));
            }
        }
        return out;
    }

    private static Instant parseNanos(String s) {
        long nanos = Long.parseLong(s);
        return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LokiQueryRangeResponse {
        public String status;
        public LokiData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LokiData {
        public String resultType;
        public List<LokiStream> result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LokiStream {
        public Map<String, String> stream;
        public List<List<String>> values;
    }
}

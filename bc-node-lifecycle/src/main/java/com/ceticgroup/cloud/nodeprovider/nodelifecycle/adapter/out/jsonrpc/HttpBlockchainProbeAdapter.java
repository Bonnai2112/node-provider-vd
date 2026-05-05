package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.jsonrpc;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ConsensusSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ExecutionSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

public class HttpBlockchainProbeAdapter implements BlockchainProbePort {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_ATTEMPTS = 2;
    private static final String CL_SYNCING_PATH = "/eth/v1/node/syncing";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public HttpBlockchainProbeAdapter(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public Optional<ExecutionSyncStatus> probeElSync(JsonRpcEndpoint endpoint) {
        Optional<JsonNode> resultOpt = callJsonRpc(endpoint.uri(), "eth_syncing");
        if (resultOpt.isEmpty()) {
            return Optional.empty();
        }
        JsonNode result = resultOpt.get();
        if (result.isBoolean() && !result.booleanValue()) {
            return Optional.of(new ExecutionSyncStatus.Synced());
        }
        if (result.isObject()
                && result.hasNonNull("currentBlock")
                && result.hasNonNull("highestBlock")) {
            long current = parseHex(result.get("currentBlock").asText());
            long highest = parseHex(result.get("highestBlock").asText());
            return Optional.of(new ExecutionSyncStatus.Syncing(highest, current));
        }
        return Optional.of(new ExecutionSyncStatus.NotSyncing());
    }

    @Override
    public OptionalInt probePeers(JsonRpcEndpoint endpoint) {
        Optional<JsonNode> resultOpt = callJsonRpc(endpoint.uri(), "net_peerCount");
        if (resultOpt.isEmpty()) {
            return OptionalInt.empty();
        }
        JsonNode result = resultOpt.get();
        if (!result.isTextual()) {
            return OptionalInt.of(0);
        }
        long count = parseHex(result.asText());
        return OptionalInt.of((int) Math.min(count, Integer.MAX_VALUE));
    }

    @Override
    public Optional<ConsensusSyncStatus> probeClSync(URI clRestEndpoint) {
        URI target = clRestEndpoint.resolve(CL_SYNCING_PATH);
        Optional<JsonNode> bodyOpt = callBeaconGet(target);
        if (bodyOpt.isEmpty()) {
            return Optional.empty();
        }
        JsonNode data = bodyOpt.get().path("data");
        if (data.isMissingNode() || !data.hasNonNull("head_slot")) {
            return Optional.of(new ConsensusSyncStatus.NotSyncing());
        }
        boolean isSyncing =
                data.hasNonNull("is_syncing") && data.get("is_syncing").asBoolean(false);
        long headSlot = parseLong(data.get("head_slot").asText());
        long syncDistance =
                data.hasNonNull("sync_distance")
                        ? parseLong(data.get("sync_distance").asText())
                        : 0L;
        if (!isSyncing && syncDistance == 0L) {
            return Optional.of(new ConsensusSyncStatus.Synced());
        }
        return Optional.of(new ConsensusSyncStatus.Syncing(headSlot, syncDistance));
    }

    private Optional<JsonNode> callJsonRpc(URI uri, String method) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":[]}";
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest req =
                        HttpRequest.newBuilder(uri)
                                .timeout(HTTP_TIMEOUT)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                HttpResponse<String> resp =
                        httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    continue;
                }
                JsonNode root = mapper.readTree(resp.body());
                JsonNode result = root.get("result");
                if (result == null) {
                    continue;
                }
                return Optional.of(result);
            } catch (IOException e) {
                // probe failure (connect refused, timeout, parse error) — fall through to retry
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while calling " + method, e);
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> callBeaconGet(URI uri) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest req =
                        HttpRequest.newBuilder(uri)
                                .timeout(HTTP_TIMEOUT)
                                .header("Accept", "application/json")
                                .GET()
                                .build();
                HttpResponse<String> resp =
                        httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    continue;
                }
                return Optional.of(mapper.readTree(resp.body()));
            } catch (IOException e) {
                // probe failure — fall through to retry
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while calling " + uri, e);
            }
        }
        return Optional.empty();
    }

    private static long parseHex(String hex) {
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        return s.isEmpty() ? 0L : Long.parseUnsignedLong(s, 16);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

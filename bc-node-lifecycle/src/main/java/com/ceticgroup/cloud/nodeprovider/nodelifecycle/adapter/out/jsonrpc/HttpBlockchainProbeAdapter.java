package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.jsonrpc;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.SyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.BlockchainProbePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpBlockchainProbeAdapter implements BlockchainProbePort {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_ATTEMPTS = 2;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public HttpBlockchainProbeAdapter(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public SyncStatus probeSync(JsonRpcEndpoint endpoint) {
        JsonNode result = callJsonRpc(endpoint.uri(), "eth_syncing");
        if (result.isBoolean() && !result.booleanValue()) {
            return new SyncStatus.Synced();
        }
        if (result.isObject()
                && result.hasNonNull("currentBlock")
                && result.hasNonNull("highestBlock")) {
            long current = parseHex(result.get("currentBlock").asText());
            long highest = parseHex(result.get("highestBlock").asText());
            return new SyncStatus.Syncing(highest, current);
        }
        return new SyncStatus.NotSyncing();
    }

    @Override
    public int probePeers(JsonRpcEndpoint endpoint) {
        JsonNode result = callJsonRpc(endpoint.uri(), "net_peerCount");
        if (!result.isTextual()) {
            return 0;
        }
        long count = parseHex(result.asText());
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private JsonNode callJsonRpc(URI uri, String method) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":[]}";
        IOException last = null;
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
                    last = new IOException("HTTP " + resp.statusCode() + " from " + uri);
                    continue;
                }
                JsonNode root = mapper.readTree(resp.body());
                JsonNode result = root.get("result");
                if (result == null) {
                    last = new IOException("missing result field for " + method);
                    continue;
                }
                return result;
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while calling " + method, e);
            }
        }
        throw new IllegalStateException("JSON-RPC call " + method + " failed", last);
    }

    private static long parseHex(String hex) {
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        return s.isEmpty() ? 0L : Long.parseUnsignedLong(s, 16);
    }
}

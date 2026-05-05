package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.jsonrpc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ConsensusSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ExecutionSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpBlockchainProbeAdapterTest {

    private WireMockServer server;
    private HttpBlockchainProbeAdapter adapter;
    private JsonRpcEndpoint endpoint;
    private URI clBaseUri;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        adapter = new HttpBlockchainProbeAdapter(HttpClient.newHttpClient(), new ObjectMapper());
        endpoint = new JsonRpcEndpoint(URI.create(server.baseUrl() + "/"));
        clBaseUri = URI.create(server.baseUrl() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void probeElSync_should_returnSynced_when_eth_syncing_returnsFalse() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("eth_syncing"))
                        .willReturn(jsonRpcResult("false")));

        Optional<ExecutionSyncStatus> status = adapter.probeElSync(endpoint);

        assertThat(status).get().isInstanceOf(ExecutionSyncStatus.Synced.class);
    }

    @Test
    void probeElSync_should_returnSyncing_when_eth_syncing_returnsObject() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("eth_syncing"))
                        .willReturn(
                                jsonRpcResult(
                                        "{\"startingBlock\":\"0x0\","
                                                + "\"currentBlock\":\"0x10\","
                                                + "\"highestBlock\":\"0x20\"}")));

        Optional<ExecutionSyncStatus> status = adapter.probeElSync(endpoint);

        assertThat(status)
                .get()
                .isInstanceOfSatisfying(
                        ExecutionSyncStatus.Syncing.class,
                        s -> {
                            assertThat(s.highestBlock()).isEqualTo(32L);
                            assertThat(s.currentBlock()).isEqualTo(16L);
                        });
    }

    @Test
    void probeElSync_should_returnNotSyncing_when_resultShapeUnknown() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("eth_syncing"))
                        .willReturn(jsonRpcResult("\"unexpected\"")));

        Optional<ExecutionSyncStatus> status = adapter.probeElSync(endpoint);

        assertThat(status).get().isInstanceOf(ExecutionSyncStatus.NotSyncing.class);
    }

    @Test
    void probePeers_should_parseHexResult_when_net_peerCountReturns() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .willReturn(jsonRpcResult("\"0x10\"")));

        OptionalInt peers = adapter.probePeers(endpoint);

        assertThat(peers).hasValue(16);
    }

    @Test
    void probePeers_should_return0_when_resultIsNotTextual() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .willReturn(jsonRpcResult("0")));

        OptionalInt peers = adapter.probePeers(endpoint);

        assertThat(peers).hasValue(0);
    }

    @Test
    void call_should_retryOnce_when_firstResponseIs5xx() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .inScenario("retry")
                        .whenScenarioStateIs("Started")
                        .willReturn(serverError())
                        .willSetStateTo("retry-succeeds"));
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .inScenario("retry")
                        .whenScenarioStateIs("retry-succeeds")
                        .willReturn(jsonRpcResult("\"0x05\"")));

        OptionalInt peers = adapter.probePeers(endpoint);

        assertThat(peers).hasValue(5);
    }

    @Test
    void probePeers_should_returnEmpty_when_bothAttemptsFail() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .willReturn(serverError()));

        OptionalInt peers = adapter.probePeers(endpoint);

        assertThat(peers).isEmpty();
    }

    @Test
    void probeElSync_should_returnEmpty_when_endpointUnreachable() {
        JsonRpcEndpoint dead = new JsonRpcEndpoint(URI.create("http://127.0.0.1:1/"));

        Optional<ExecutionSyncStatus> status = adapter.probeElSync(dead);

        assertThat(status).isEmpty();
    }

    @Test
    void probeClSync_should_returnSynced_when_isSyncingFalseAndDistanceZero() {
        server.stubFor(
                get(urlEqualTo("/eth/v1/node/syncing"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"data\":{\"head_slot\":\"123456\","
                                                        + "\"sync_distance\":\"0\","
                                                        + "\"is_syncing\":false}}")));

        Optional<ConsensusSyncStatus> status = adapter.probeClSync(clBaseUri);

        assertThat(status).get().isInstanceOf(ConsensusSyncStatus.Synced.class);
    }

    @Test
    void probeClSync_should_returnSyncing_when_isSyncingTrue() {
        server.stubFor(
                get(urlEqualTo("/eth/v1/node/syncing"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"data\":{\"head_slot\":\"100000\","
                                                        + "\"sync_distance\":\"123\","
                                                        + "\"is_syncing\":true}}")));

        Optional<ConsensusSyncStatus> status = adapter.probeClSync(clBaseUri);

        assertThat(status)
                .get()
                .isInstanceOfSatisfying(
                        ConsensusSyncStatus.Syncing.class,
                        s -> {
                            assertThat(s.headSlot()).isEqualTo(100000L);
                            assertThat(s.syncDistance()).isEqualTo(123L);
                        });
    }

    @Test
    void probeClSync_should_returnEmpty_when_endpointUnreachable() {
        URI dead = URI.create("http://127.0.0.1:1/");

        Optional<ConsensusSyncStatus> status = adapter.probeClSync(dead);

        assertThat(status).isEmpty();
    }

    @Test
    void probeClSync_should_returnEmpty_when_serverErrorOnAllAttempts() {
        server.stubFor(get(urlEqualTo("/eth/v1/node/syncing")).willReturn(serverError()));

        Optional<ConsensusSyncStatus> status = adapter.probeClSync(clBaseUri);

        assertThat(status).isEmpty();
    }

    private static com.github.tomakehurst.wiremock.matching.StringValuePattern matchingMethod(
            String method) {
        return equalToJson(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":[]}",
                true,
                false);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonRpcResult(
            String resultJson) {
        return aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}");
    }
}

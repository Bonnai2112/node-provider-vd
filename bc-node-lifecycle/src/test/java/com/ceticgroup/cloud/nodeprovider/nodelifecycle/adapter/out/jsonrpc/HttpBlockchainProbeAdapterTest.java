package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.jsonrpc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.SyncStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpBlockchainProbeAdapterTest {

    private WireMockServer server;
    private HttpBlockchainProbeAdapter adapter;
    private JsonRpcEndpoint endpoint;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        adapter = new HttpBlockchainProbeAdapter(HttpClient.newHttpClient(), new ObjectMapper());
        endpoint = new JsonRpcEndpoint(URI.create(server.baseUrl() + "/"));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void probeSync_should_returnSynced_when_eth_syncing_returnsFalse() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("eth_syncing"))
                        .willReturn(jsonRpcResult("false")));

        SyncStatus status = adapter.probeSync(endpoint);

        assertThat(status).isInstanceOf(SyncStatus.Synced.class);
    }

    @Test
    void probeSync_should_returnSyncing_when_eth_syncing_returnsObject() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("eth_syncing"))
                        .willReturn(
                                jsonRpcResult(
                                        "{\"startingBlock\":\"0x0\","
                                                + "\"currentBlock\":\"0x10\","
                                                + "\"highestBlock\":\"0x20\"}")));

        SyncStatus status = adapter.probeSync(endpoint);

        assertThat(status)
                .isInstanceOfSatisfying(
                        SyncStatus.Syncing.class,
                        s -> {
                            assertThat(s.headSlot()).isEqualTo(32L);
                            assertThat(s.currentSlot()).isEqualTo(16L);
                        });
    }

    @Test
    void probeSync_should_returnNotSyncing_when_resultShapeUnknown() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("eth_syncing"))
                        .willReturn(jsonRpcResult("\"unexpected\"")));

        SyncStatus status = adapter.probeSync(endpoint);

        assertThat(status).isInstanceOf(SyncStatus.NotSyncing.class);
    }

    @Test
    void probePeers_should_parseHexResult_when_net_peerCountReturns() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .willReturn(jsonRpcResult("\"0x10\"")));

        int peers = adapter.probePeers(endpoint);

        assertThat(peers).isEqualTo(16);
    }

    @Test
    void probePeers_should_return0_when_resultIsNotTextual() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .willReturn(jsonRpcResult("0")));

        int peers = adapter.probePeers(endpoint);

        assertThat(peers).isZero();
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

        int peers = adapter.probePeers(endpoint);

        assertThat(peers).isEqualTo(5);
    }

    @Test
    void call_should_throw_when_bothAttemptsFail() {
        server.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingMethod("net_peerCount"))
                        .willReturn(serverError()));

        assertThatThrownBy(() -> adapter.probePeers(endpoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("net_peerCount");
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

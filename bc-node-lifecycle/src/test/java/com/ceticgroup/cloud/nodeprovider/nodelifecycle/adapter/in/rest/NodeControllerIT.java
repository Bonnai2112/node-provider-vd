package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GetNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NodeController.class)
@Import(NodeProblemDetailsAdvice.class)
class NodeControllerIT {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProvisionNodeUseCase provisionNodeUseCase;

    @MockitoBean private GetNodeUseCase getNodeUseCase;

    @Test
    void post_should_return_202_with_location_header() throws Exception {
        UUID generatedId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        given(provisionNodeUseCase.provision(any(ProvisionNodeCommand.class)))
                .willReturn(new NodeId(generatedId));

        String body =
                """
                {
                  "ownerId": "%s",
                  "network": "HOODI",
                  "executionLayer": "BESU",
                  "consensusLayer": "TEKU"
                }
                """
                        .formatted(ownerId);

        mockMvc.perform(post("/api/v1/nodes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/nodes/" + generatedId))
                .andExpect(jsonPath("$.id").value(generatedId.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void post_should_return_400_when_payload_invalid() throws Exception {
        String body =
                """
                {
                  "ownerId": null,
                  "network": "HOODI"
                }
                """;

        mockMvc.perform(post("/api/v1/nodes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_should_return_400_when_network_unknown() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String body =
                """
                {
                  "ownerId": "%s",
                  "network": "MAINNET",
                  "executionLayer": "BESU",
                  "consensusLayer": "TEKU"
                }
                """
                        .formatted(ownerId);

        mockMvc.perform(post("/api/v1/nodes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void get_should_return_node_payload() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Node node =
                Node.restore(
                        new NodeId(id),
                        new OwnerId(ownerId),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        new com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus.Ready(
                                new Endpoint(URI.create("https://rpc.example.com"))));
        given(getNodeUseCase.getById(new NodeId(id))).willReturn(node);

        mockMvc.perform(get("/api/v1/nodes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.network").value("HOODI"))
                .andExpect(jsonPath("$.executionLayer").value("BESU"))
                .andExpect(jsonPath("$.consensusLayer").value("TEKU"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.endpoint").value("https://rpc.example.com"));
    }

    @Test
    void get_should_return_404_problem_when_not_found() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new NodeNotFoundException(new NodeId(id)))
                .given(getNodeUseCase)
                .getById(new NodeId(id));

        mockMvc.perform(get("/api/v1/nodes/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Node not found"))
                .andExpect(jsonPath("$.nodeId").value(id.toString()));
    }
}

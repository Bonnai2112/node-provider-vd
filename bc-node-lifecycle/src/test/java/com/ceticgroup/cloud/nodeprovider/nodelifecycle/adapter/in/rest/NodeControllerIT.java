package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.DisableMevBoostUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.DisableValidatorUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.EnableMevBoostUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.EnableValidatorUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GetNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ListNodesByOwnerUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.RestartNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.TerminateNodeUseCase;
import java.net.URI;
import java.util.List;
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

    @MockitoBean private ListNodesByOwnerUseCase listNodesByOwnerUseCase;

    @MockitoBean private TerminateNodeUseCase terminateNodeUseCase;

    @MockitoBean private RestartNodeUseCase restartNodeUseCase;

    @MockitoBean private EnableValidatorUseCase enableValidatorUseCase;

    @MockitoBean private DisableValidatorUseCase disableValidatorUseCase;

    @MockitoBean private EnableMevBoostUseCase enableMevBoostUseCase;

    @MockitoBean private DisableMevBoostUseCase disableMevBoostUseCase;

    @Test
    void post_should_return_202_with_location_header() throws Exception {
        UUID generatedId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        given(provisionNodeUseCase.provision(any(ProvisionNodeCommand.class)))
                .willReturn(new NodeId(generatedId));

        String body =
                """
                {
                  "network": "HOODI",
                  "executionLayer": "BESU",
                  "consensusLayer": "TEKU"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/nodes")
                                .header("X-Owner-Id", ownerId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/nodes/" + generatedId))
                .andExpect(jsonPath("$.id").value(generatedId.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void post_should_return_400_when_ownerHeaderMissing() throws Exception {
        String body =
                """
                {
                  "network": "HOODI",
                  "executionLayer": "BESU",
                  "consensusLayer": "TEKU"
                }
                """;

        mockMvc.perform(post("/api/v1/nodes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_should_return_400_when_payload_invalid() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String body =
                """
                {
                  "network": "HOODI"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/nodes")
                                .header("X-Owner-Id", ownerId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_should_return_400_when_network_unknown() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String body =
                """
                {
                  "network": "MAINNET",
                  "executionLayer": "BESU",
                  "consensusLayer": "TEKU"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/nodes")
                                .header("X-Owner-Id", ownerId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void get_should_return_node_payload() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        given(getNodeUseCase.getById(new NodeId(id))).willReturn(readyNode(id, ownerId));

        mockMvc.perform(get("/api/v1/nodes/{id}", id).header("X-Owner-Id", ownerId.toString()))
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
    void get_should_return_404_when_ownerMismatch() throws Exception {
        UUID id = UUID.randomUUID();
        UUID realOwner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        given(getNodeUseCase.getById(new NodeId(id))).willReturn(readyNode(id, realOwner));

        mockMvc.perform(get("/api/v1/nodes/{id}", id).header("X-Owner-Id", otherOwner.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_should_return_404_problem_when_not_found() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        willThrow(new NodeNotFoundException(new NodeId(id)))
                .given(getNodeUseCase)
                .getById(new NodeId(id));

        mockMvc.perform(get("/api/v1/nodes/{id}", id).header("X-Owner-Id", ownerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Node not found"))
                .andExpect(jsonPath("$.nodeId").value(id.toString()));
    }

    @Test
    void list_should_return_nodes_filteredByOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        given(listNodesByOwnerUseCase.listByOwner(new OwnerId(ownerId)))
                .willReturn(List.of(readyNode(nodeId, ownerId)));

        mockMvc.perform(get("/api/v1/nodes").header("X-Owner-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(nodeId.toString()))
                .andExpect(jsonPath("$[0].ownerId").value(ownerId.toString()));
    }

    @Test
    void list_should_return_400_when_ownerHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/nodes")).andExpect(status().isBadRequest());
        then(listNodesByOwnerUseCase).should(never()).listByOwner(any());
    }

    @Test
    void delete_should_return_202_when_terminateInvoked() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/nodes/{id}", id).header("X-Owner-Id", ownerId.toString()))
                .andExpect(status().isAccepted());

        then(terminateNodeUseCase).should().terminate(new NodeId(id), new OwnerId(ownerId));
    }

    @Test
    void delete_should_return_404_when_nodeNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        willThrow(new NodeNotFoundException(new NodeId(id)))
                .given(terminateNodeUseCase)
                .terminate(new NodeId(id), new OwnerId(ownerId));

        mockMvc.perform(delete("/api/v1/nodes/{id}", id).header("X-Owner-Id", ownerId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void restart_should_return_202_when_restartInvoked() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/nodes/{id}/restart", id)
                                .header("X-Owner-Id", ownerId.toString()))
                .andExpect(status().isAccepted());

        then(restartNodeUseCase).should().restart(new NodeId(id), new OwnerId(ownerId));
    }

    @Test
    void restart_should_return_404_when_nodeNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        willThrow(new NodeNotFoundException(new NodeId(id)))
                .given(restartNodeUseCase)
                .restart(new NodeId(id), new OwnerId(ownerId));

        mockMvc.perform(
                        post("/api/v1/nodes/{id}/restart", id)
                                .header("X-Owner-Id", ownerId.toString()))
                .andExpect(status().isNotFound());
    }

    private static Node readyNode(UUID id, UUID ownerId) {
        return Node.restore(
                new NodeId(id),
                new OwnerId(ownerId),
                Network.HOODI,
                ClientPair.besuTeku(),
                com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions.defaults(),
                new NodeStatus.Ready(new Endpoint(URI.create("https://rpc.example.com"))),
                null);
    }
}

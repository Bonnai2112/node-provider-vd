package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.CreateNodeRequest;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.NodeResponse;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GetNodeUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ProvisionNodeUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nodes")
class NodeController {

    private final ProvisionNodeUseCase provisionNodeUseCase;
    private final GetNodeUseCase getNodeUseCase;

    NodeController(ProvisionNodeUseCase provisionNodeUseCase, GetNodeUseCase getNodeUseCase) {
        this.provisionNodeUseCase = provisionNodeUseCase;
        this.getNodeUseCase = getNodeUseCase;
    }

    @PostMapping
    ResponseEntity<NodeAcceptedResponse> create(@Valid @RequestBody CreateNodeRequest request) {
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(request.ownerId()),
                        Network.valueOf(request.network()),
                        new ClientPair(
                                ElClient.valueOf(request.executionLayer()),
                                ClClient.valueOf(request.consensusLayer())));

        NodeId id = provisionNodeUseCase.provision(command);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/nodes/" + id.value()))
                .body(new NodeAcceptedResponse(id.value(), "REQUESTED"));
    }

    @GetMapping("/{id}")
    NodeResponse get(@PathVariable UUID id) {
        Node node = getNodeUseCase.getById(new NodeId(id));
        return toResponse(node);
    }

    private static NodeResponse toResponse(Node node) {
        NodeStatus status = node.status();
        String kind = status.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
        String endpoint =
                status instanceof NodeStatus.Ready ready ? ready.endpoint().uri().toString() : null;
        String reason =
                switch (status) {
                    case NodeStatus.Degraded d -> d.reason();
                    case NodeStatus.Failed f -> f.reason();
                    default -> null;
                };

        return new NodeResponse(
                node.id().value(),
                node.owner().value(),
                node.network().name(),
                node.clientPair().executionLayer().name(),
                node.clientPair().consensusLayer().name(),
                kind,
                endpoint,
                reason);
    }

    record NodeAcceptedResponse(UUID id, String status) {}
}

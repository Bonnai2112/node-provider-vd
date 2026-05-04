package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaNodeRepository implements NodeRepository {

    private final SpringDataNodeJpaRepository jpa;

    JpaNodeRepository(SpringDataNodeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Node node) {
        jpa.save(toEntity(node));
    }

    @Override
    public Optional<Node> findById(NodeId id) {
        return jpa.findById(id.value()).map(JpaNodeRepository::toDomain);
    }

    @Override
    public List<Node> findByOwner(OwnerId owner) {
        return jpa.findByOwnerId(owner.value()).stream().map(JpaNodeRepository::toDomain).toList();
    }

    @Override
    public List<Node> findNonTerminal() {
        return jpa.findByStatusKindNotIn(List.of("TERMINATED", "FAILED")).stream()
                .map(JpaNodeRepository::toDomain)
                .toList();
    }

    private static NodeJpaEntity toEntity(Node node) {
        NodeStatus status = node.status();
        String kind = statusKind(status);
        String endpointUri =
                status instanceof NodeStatus.Ready ready ? ready.endpoint().uri().toString() : null;
        String reason =
                switch (status) {
                    case NodeStatus.Degraded d -> d.reason();
                    case NodeStatus.Failed f -> f.reason();
                    default -> null;
                };
        String deploymentRef = node.deploymentRef() == null ? null : node.deploymentRef().payload();

        return new NodeJpaEntity(
                node.id().value(),
                node.owner().value(),
                node.network().name(),
                node.clientPair().executionLayer().name(),
                node.clientPair().consensusLayer().name(),
                kind,
                endpointUri,
                reason,
                deploymentRef);
    }

    private static Node toDomain(NodeJpaEntity entity) {
        ClientPair clientPair =
                new ClientPair(
                        ElClient.valueOf(entity.getElClient()),
                        ClClient.valueOf(entity.getClClient()));
        DeploymentRef deploymentRef =
                entity.getDeploymentRef() == null
                        ? null
                        : new DeploymentRef(entity.getDeploymentRef());
        return Node.restore(
                new NodeId(entity.getId()),
                new OwnerId(entity.getOwnerId()),
                Network.valueOf(entity.getNetwork()),
                clientPair,
                toStatus(entity),
                deploymentRef);
    }

    private static String statusKind(NodeStatus status) {
        return switch (status) {
            case NodeStatus.Requested _ -> "REQUESTED";
            case NodeStatus.Provisioning _ -> "PROVISIONING";
            case NodeStatus.Syncing _ -> "SYNCING";
            case NodeStatus.Ready _ -> "READY";
            case NodeStatus.Degraded _ -> "DEGRADED";
            case NodeStatus.Terminating _ -> "TERMINATING";
            case NodeStatus.Terminated _ -> "TERMINATED";
            case NodeStatus.Failed _ -> "FAILED";
        };
    }

    private static NodeStatus toStatus(NodeJpaEntity entity) {
        return switch (entity.getStatusKind()) {
            case "REQUESTED" -> new NodeStatus.Requested();
            case "PROVISIONING" -> new NodeStatus.Provisioning();
            case "SYNCING" -> new NodeStatus.Syncing();
            case "READY" -> new NodeStatus.Ready(new Endpoint(URI.create(entity.getEndpointUri())));
            case "DEGRADED" -> new NodeStatus.Degraded(entity.getStatusReason());
            case "TERMINATING" -> new NodeStatus.Terminating();
            case "TERMINATED" -> new NodeStatus.Terminated();
            case "FAILED" -> new NodeStatus.Failed(entity.getStatusReason());
            default ->
                    throw new IllegalStateException(
                            "Unknown status_kind: " + entity.getStatusKind());
        };
    }
}

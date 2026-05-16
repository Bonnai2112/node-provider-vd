package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ConsensusSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ExecutionSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LastObservation;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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
        return jpa.findByStatusKindNotIn(List.of("TERMINATED", "FAILED", "STOPPED")).stream()
                .map(JpaNodeRepository::toDomain)
                .toList();
    }

    @Override
    public List<Node> findReadyOnNetwork(Network network) {
        return jpa.findByNetworkAndStatusKindIn(network.name(), List.of("READY")).stream()
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
                    case NodeStatus.Stopped s -> s.reason();
                    case NodeStatus.Failed f -> f.reason();
                    default -> null;
                };
        String deploymentRef = node.deploymentRef() == null ? null : node.deploymentRef().payload();

        Optional<LastObservation> obs = node.lastObservation();
        return new NodeJpaEntity(
                node.id().value(),
                node.owner().value(),
                node.network().name(),
                node.clientPair().executionLayer().name(),
                node.clientPair().consensusLayer().name(),
                kind,
                endpointUri,
                reason,
                deploymentRef,
                obs.flatMap(o -> o.elSync().map(JpaNodeRepository::elSyncKind)).orElse(null),
                obs.flatMap(o -> o.elSync().map(JpaNodeRepository::elSyncCurrent)).orElse(null),
                obs.flatMap(o -> o.elSync().map(JpaNodeRepository::elSyncHighest)).orElse(null),
                obs.flatMap(o -> o.clSync().map(JpaNodeRepository::clSyncKind)).orElse(null),
                obs.flatMap(o -> o.clSync().map(JpaNodeRepository::clSyncHead)).orElse(null),
                obs.flatMap(o -> o.clSync().map(JpaNodeRepository::clSyncDistance)).orElse(null),
                obs.flatMap(
                                o ->
                                        o.peers().isPresent()
                                                ? Optional.of(o.peers().getAsInt())
                                                : Optional.<Integer>empty())
                        .orElse(null),
                obs.map(LastObservation::observedAt).orElse(null),
                obs.flatMap(
                                o ->
                                        o.elBlocksPerSecond().isPresent()
                                                ? Optional.of(o.elBlocksPerSecond().getAsDouble())
                                                : Optional.<Double>empty())
                        .orElse(null),
                obs.flatMap(
                                o ->
                                        o.clSlotsPerSecond().isPresent()
                                                ? Optional.of(o.clSlotsPerSecond().getAsDouble())
                                                : Optional.<Double>empty())
                        .orElse(null),
                node.options().validator(),
                node.options().mevBoost(),
                node.options().feeRecipient(),
                node.options().graffiti().orElse(null),
                node.options().mevMinBid().orElse(null),
                node.options().mevBuildFactor().isPresent()
                        ? node.options().mevBuildFactor().getAsInt()
                        : null);
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
        LastObservation observation = toObservation(entity);
        NodeOptions options = toOptions(entity);
        return Node.restore(
                new NodeId(entity.getId()),
                new OwnerId(entity.getOwnerId()),
                Network.valueOf(entity.getNetwork()),
                clientPair,
                options,
                toStatus(entity),
                deploymentRef,
                observation);
    }

    private static NodeOptions toOptions(NodeJpaEntity entity) {
        return new NodeOptions(
                entity.isValidator(),
                entity.isMevBoost(),
                entity.getFeeRecipient(),
                Optional.ofNullable(entity.getGraffiti()),
                Optional.ofNullable(entity.getMevMinBid()),
                entity.getMevBuildFactor() == null
                        ? java.util.OptionalInt.empty()
                        : java.util.OptionalInt.of(entity.getMevBuildFactor()));
    }

    private static String statusKind(NodeStatus status) {
        return switch (status) {
            case NodeStatus.Requested _ -> "REQUESTED";
            case NodeStatus.Provisioning _ -> "PROVISIONING";
            case NodeStatus.Syncing _ -> "SYNCING";
            case NodeStatus.Ready _ -> "READY";
            case NodeStatus.Degraded _ -> "DEGRADED";
            case NodeStatus.Stopped _ -> "STOPPED";
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
            case "STOPPED" -> new NodeStatus.Stopped(entity.getStatusReason());
            case "TERMINATING" -> new NodeStatus.Terminating();
            case "TERMINATED" -> new NodeStatus.Terminated();
            case "FAILED" -> new NodeStatus.Failed(entity.getStatusReason());
            default ->
                    throw new IllegalStateException(
                            "Unknown status_kind: " + entity.getStatusKind());
        };
    }

    private static String elSyncKind(ExecutionSyncStatus s) {
        return switch (s) {
            case ExecutionSyncStatus.Synced sy -> "SYNCED";
            case ExecutionSyncStatus.Syncing sy -> "SYNCING";
            case ExecutionSyncStatus.NotSyncing sy -> "NOT_SYNCING";
        };
    }

    private static Long elSyncCurrent(ExecutionSyncStatus s) {
        return s instanceof ExecutionSyncStatus.Syncing sy ? sy.currentBlock() : null;
    }

    private static Long elSyncHighest(ExecutionSyncStatus s) {
        return s instanceof ExecutionSyncStatus.Syncing sy ? sy.highestBlock() : null;
    }

    private static String clSyncKind(ConsensusSyncStatus s) {
        return switch (s) {
            case ConsensusSyncStatus.Synced sy -> "SYNCED";
            case ConsensusSyncStatus.Syncing sy -> "SYNCING";
            case ConsensusSyncStatus.NotSyncing sy -> "NOT_SYNCING";
        };
    }

    private static Long clSyncHead(ConsensusSyncStatus s) {
        return s instanceof ConsensusSyncStatus.Syncing sy ? sy.headSlot() : null;
    }

    private static Long clSyncDistance(ConsensusSyncStatus s) {
        return s instanceof ConsensusSyncStatus.Syncing sy ? sy.syncDistance() : null;
    }

    private static LastObservation toObservation(NodeJpaEntity entity) {
        if (entity.getLastObservedAt() == null) {
            return null;
        }
        return new LastObservation(
                Optional.ofNullable(entity.getElSyncKind()).map(k -> elSyncFromRow(k, entity)),
                Optional.ofNullable(entity.getClSyncKind()).map(k -> clSyncFromRow(k, entity)),
                entity.getElBlocksPerSecond() == null
                        ? OptionalDouble.empty()
                        : OptionalDouble.of(entity.getElBlocksPerSecond()),
                entity.getClSlotsPerSecond() == null
                        ? OptionalDouble.empty()
                        : OptionalDouble.of(entity.getClSlotsPerSecond()),
                entity.getPeers() == null ? OptionalInt.empty() : OptionalInt.of(entity.getPeers()),
                entity.getLastObservedAt());
    }

    private static ExecutionSyncStatus elSyncFromRow(String kind, NodeJpaEntity entity) {
        return switch (kind) {
            case "SYNCED" -> new ExecutionSyncStatus.Synced();
            case "SYNCING" ->
                    new ExecutionSyncStatus.Syncing(
                            entity.getElSyncHighestBlock() == null
                                    ? 0L
                                    : entity.getElSyncHighestBlock(),
                            entity.getElSyncCurrentBlock() == null
                                    ? 0L
                                    : entity.getElSyncCurrentBlock());
            case "NOT_SYNCING" -> new ExecutionSyncStatus.NotSyncing();
            default -> throw new IllegalStateException("Unknown el_sync_kind: " + kind);
        };
    }

    private static ConsensusSyncStatus clSyncFromRow(String kind, NodeJpaEntity entity) {
        return switch (kind) {
            case "SYNCED" -> new ConsensusSyncStatus.Synced();
            case "SYNCING" ->
                    new ConsensusSyncStatus.Syncing(
                            entity.getClSyncHeadSlot() == null ? 0L : entity.getClSyncHeadSlot(),
                            entity.getClSyncDistance() == null ? 0L : entity.getClSyncDistance());
            case "NOT_SYNCING" -> new ConsensusSyncStatus.NotSyncing();
            default -> throw new IllegalStateException("Unknown cl_sync_kind: " + kind);
        };
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.CreateNodeRequest;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.EnableMevBoostRequest;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.EnableValidatorRequest;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest.dto.NodeResponse;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ConsensusSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ExecutionSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LastObservation;
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
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nodes")
class NodeController {

    private static final String OWNER_HEADER = "X-Owner-Id";

    private final ProvisionNodeUseCase provisionNodeUseCase;
    private final GetNodeUseCase getNodeUseCase;
    private final ListNodesByOwnerUseCase listNodesByOwnerUseCase;
    private final TerminateNodeUseCase terminateNodeUseCase;
    private final RestartNodeUseCase restartNodeUseCase;
    private final EnableValidatorUseCase enableValidatorUseCase;
    private final DisableValidatorUseCase disableValidatorUseCase;
    private final EnableMevBoostUseCase enableMevBoostUseCase;
    private final DisableMevBoostUseCase disableMevBoostUseCase;

    NodeController(
            ProvisionNodeUseCase provisionNodeUseCase,
            GetNodeUseCase getNodeUseCase,
            ListNodesByOwnerUseCase listNodesByOwnerUseCase,
            TerminateNodeUseCase terminateNodeUseCase,
            RestartNodeUseCase restartNodeUseCase,
            EnableValidatorUseCase enableValidatorUseCase,
            DisableValidatorUseCase disableValidatorUseCase,
            EnableMevBoostUseCase enableMevBoostUseCase,
            DisableMevBoostUseCase disableMevBoostUseCase) {
        this.provisionNodeUseCase = provisionNodeUseCase;
        this.getNodeUseCase = getNodeUseCase;
        this.listNodesByOwnerUseCase = listNodesByOwnerUseCase;
        this.terminateNodeUseCase = terminateNodeUseCase;
        this.restartNodeUseCase = restartNodeUseCase;
        this.enableValidatorUseCase = enableValidatorUseCase;
        this.disableValidatorUseCase = disableValidatorUseCase;
        this.enableMevBoostUseCase = enableMevBoostUseCase;
        this.disableMevBoostUseCase = disableMevBoostUseCase;
    }

    @PostMapping
    ResponseEntity<NodeAcceptedResponse> create(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @Valid @RequestBody CreateNodeRequest request) {
        ProvisionNodeCommand command =
                new ProvisionNodeCommand(
                        new OwnerId(ownerId),
                        Network.valueOf(request.network()),
                        new ClientPair(
                                ElClient.valueOf(request.executionLayer()),
                                ClClient.valueOf(request.consensusLayer())),
                        toOptions(request));

        NodeId id = provisionNodeUseCase.provision(command);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/nodes/" + id.value()))
                .body(new NodeAcceptedResponse(id.value(), "REQUESTED"));
    }

    private static com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions toOptions(
            CreateNodeRequest request) {
        boolean validator = Boolean.TRUE.equals(request.validator());
        boolean mevBoost = Boolean.TRUE.equals(request.mevBoost());
        String feeRecipient =
                request.feeRecipient() == null || request.feeRecipient().isBlank()
                        ? com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions
                                .DEFAULT_FEE_RECIPIENT
                        : request.feeRecipient();
        return new com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions(
                validator,
                mevBoost,
                feeRecipient,
                Optional.ofNullable(request.graffiti()).filter(s -> !s.isBlank()),
                Optional.ofNullable(request.mevMinBid()).filter(s -> !s.isBlank()),
                request.mevBuildFactor() == null
                        ? java.util.OptionalInt.empty()
                        : java.util.OptionalInt.of(request.mevBuildFactor()));
    }

    @GetMapping
    List<NodeResponse> list(@RequestHeader(OWNER_HEADER) UUID ownerId) {
        return listNodesByOwnerUseCase.listByOwner(new OwnerId(ownerId)).stream()
                .map(NodeController::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    NodeResponse get(@RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        Node node = getNodeUseCase.getById(new NodeId(id));
        if (!node.owner().value().equals(ownerId)) {
            throw new NodeNotFoundException(new NodeId(id));
        }
        return toResponse(node);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> terminate(
            @RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        terminateNodeUseCase.terminate(new NodeId(id), new OwnerId(ownerId));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/restart")
    ResponseEntity<Void> restart(@RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        restartNodeUseCase.restart(new NodeId(id), new OwnerId(ownerId));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/validator/enable")
    ResponseEntity<Void> enableValidator(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID id,
            @Valid @RequestBody EnableValidatorRequest request) {
        enableValidatorUseCase.enable(
                new NodeId(id),
                new OwnerId(ownerId),
                request.feeRecipient(),
                Optional.ofNullable(request.graffiti()).filter(s -> !s.isBlank()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/validator/disable")
    ResponseEntity<Void> disableValidator(
            @RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        disableValidatorUseCase.disable(new NodeId(id), new OwnerId(ownerId));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/mev-boost/enable")
    ResponseEntity<Void> enableMevBoost(
            @RequestHeader(OWNER_HEADER) UUID ownerId,
            @PathVariable UUID id,
            @Valid @RequestBody EnableMevBoostRequest request) {
        enableMevBoostUseCase.enable(
                new NodeId(id),
                new OwnerId(ownerId),
                request.mevMinBid(),
                request.mevBuildFactor());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/mev-boost/disable")
    ResponseEntity<Void> disableMevBoost(
            @RequestHeader(OWNER_HEADER) UUID ownerId, @PathVariable UUID id) {
        disableMevBoostUseCase.disable(new NodeId(id), new OwnerId(ownerId));
        return ResponseEntity.accepted().build();
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

        Optional<LastObservation> obs = node.lastObservation();
        com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions opts = node.options();
        NodeResponse.Options optsDto =
                new NodeResponse.Options(
                        opts.validator(),
                        opts.mevBoost(),
                        opts.feeRecipient(),
                        opts.graffiti().orElse(null),
                        opts.mevMinBid().orElse(null),
                        opts.mevBuildFactor().isPresent()
                                ? opts.mevBuildFactor().getAsInt()
                                : null);
        return new NodeResponse(
                node.id().value(),
                node.owner().value(),
                node.network().name(),
                node.clientPair().executionLayer().name(),
                node.clientPair().consensusLayer().name(),
                kind,
                endpoint,
                reason,
                optsDto,
                obs.flatMap(NodeController::toElSyncDto).orElse(null),
                obs.flatMap(NodeController::toClSyncDto).orElse(null),
                obs.flatMap(
                                o ->
                                        o.peers().isPresent()
                                                ? Optional.of(o.peers().getAsInt())
                                                : Optional.<Integer>empty())
                        .orElse(null),
                obs.map(LastObservation::observedAt).orElse(null));
    }

    private static Optional<NodeResponse.ElSync> toElSyncDto(LastObservation obs) {
        if (obs.elSync().isEmpty()) {
            return Optional.empty();
        }
        ExecutionSyncStatus s = obs.elSync().get();
        Double bps =
                obs.elBlocksPerSecond().isPresent() ? obs.elBlocksPerSecond().getAsDouble() : null;
        return Optional.of(
                switch (s) {
                    case ExecutionSyncStatus.Synced sy ->
                            new NodeResponse.ElSync("SYNCED", null, null, 100.0d, bps, null);
                    case ExecutionSyncStatus.Syncing sy ->
                            new NodeResponse.ElSync(
                                    "SYNCING",
                                    sy.currentBlock(),
                                    sy.highestBlock(),
                                    s.percentage().isPresent()
                                            ? s.percentage().getAsDouble()
                                            : null,
                                    bps,
                                    eta(
                                            sy.highestBlock() - sy.currentBlock(),
                                            bps,
                                            obs.observedAt()));
                    case ExecutionSyncStatus.NotSyncing sy ->
                            new NodeResponse.ElSync("NOT_SYNCING", null, null, null, null, null);
                });
    }

    private static Optional<NodeResponse.ClSync> toClSyncDto(LastObservation obs) {
        if (obs.clSync().isEmpty()) {
            return Optional.empty();
        }
        ConsensusSyncStatus s = obs.clSync().get();
        Double sps =
                obs.clSlotsPerSecond().isPresent() ? obs.clSlotsPerSecond().getAsDouble() : null;
        return Optional.of(
                switch (s) {
                    case ConsensusSyncStatus.Synced sy ->
                            new NodeResponse.ClSync("SYNCED", null, null, 100.0d, sps, null);
                    case ConsensusSyncStatus.Syncing sy ->
                            new NodeResponse.ClSync(
                                    "SYNCING",
                                    sy.headSlot(),
                                    sy.syncDistance(),
                                    s.percentage().isPresent()
                                            ? s.percentage().getAsDouble()
                                            : null,
                                    sps,
                                    eta(sy.syncDistance(), sps, obs.observedAt()));
                    case ConsensusSyncStatus.NotSyncing sy ->
                            new NodeResponse.ClSync("NOT_SYNCING", null, null, null, null, null);
                });
    }

    private static Instant eta(long remaining, Double rate, Instant observedAt) {
        if (rate == null || rate <= 0.0d || remaining <= 0L) {
            return null;
        }
        long etaSeconds = (long) Math.ceil(remaining / rate);
        return observedAt.plusSeconds(etaSeconds);
    }

    record NodeAcceptedResponse(UUID id, String status) {}
}

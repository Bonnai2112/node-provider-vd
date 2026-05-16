package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeFailed;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeProvisioningStarted;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeReady;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeRequested;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Node {

    private final NodeId id;
    private final OwnerId owner;
    private final Network network;
    private final ClientPair clientPair;
    private final NodeOptions options;
    private final List<NodeDomainEvent> pendingEvents = new ArrayList<>();

    private NodeStatus status;
    private DeploymentRef deploymentRef;
    private LastObservation lastObservation;

    private Node(
            NodeId id, OwnerId owner, Network network, ClientPair clientPair, NodeOptions options) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.network = Objects.requireNonNull(network, "network");
        this.clientPair = Objects.requireNonNull(clientPair, "clientPair");
        this.options = Objects.requireNonNull(options, "options");
        this.status = new NodeStatus.Requested();
    }

    public static Node request(
            NodeId id, OwnerId owner, Network network, ClientPair clientPair, NodeOptions options) {
        Node node = new Node(id, owner, network, clientPair, options);
        node.pendingEvents.add(new NodeRequested(id, owner, network, clientPair, Instant.now()));
        return node;
    }

    public static Node restore(
            NodeId id,
            OwnerId owner,
            Network network,
            ClientPair clientPair,
            NodeOptions options,
            NodeStatus status,
            DeploymentRef deploymentRef) {
        Node node = new Node(id, owner, network, clientPair, options);
        node.status = Objects.requireNonNull(status, "status");
        node.deploymentRef = deploymentRef;
        return node;
    }

    public static Node restore(
            NodeId id,
            OwnerId owner,
            Network network,
            ClientPair clientPair,
            NodeOptions options,
            NodeStatus status,
            DeploymentRef deploymentRef,
            LastObservation lastObservation) {
        Node node = restore(id, owner, network, clientPair, options, status, deploymentRef);
        node.lastObservation = lastObservation;
        return node;
    }

    public void observe(LastObservation observation) {
        this.lastObservation = Objects.requireNonNull(observation, "observation");
    }

    public Optional<LastObservation> lastObservation() {
        return Optional.ofNullable(lastObservation);
    }

    public void startProvisioning(DeploymentRef ref) {
        Objects.requireNonNull(ref, "deploymentRef");
        if (!(status instanceof NodeStatus.Requested)) {
            throw IllegalNodeTransitionException.from(status, "startProvisioning");
        }
        this.deploymentRef = ref;
        status = new NodeStatus.Provisioning();
        pendingEvents.add(new NodeProvisioningStarted(id, Instant.now()));
    }

    public void markSyncing() {
        if (!(status instanceof NodeStatus.Provisioning)) {
            throw IllegalNodeTransitionException.from(status, "markSyncing");
        }
        status = new NodeStatus.Syncing();
    }

    public void markReady(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!(status instanceof NodeStatus.Syncing) && !(status instanceof NodeStatus.Degraded)) {
            throw IllegalNodeTransitionException.from(status, "markReady");
        }
        status = new NodeStatus.Ready(endpoint);
        pendingEvents.add(new NodeReady(id, endpoint, Instant.now()));
    }

    public void markDegraded(String reason) {
        requireNonBlank(reason, "reason");
        if (!(status instanceof NodeStatus.Ready)) {
            throw IllegalNodeTransitionException.from(status, "markDegraded");
        }
        status = new NodeStatus.Degraded(reason);
    }

    public void markStopped(String reason) {
        requireNonBlank(reason, "reason");
        if (!(status instanceof NodeStatus.Provisioning)
                && !(status instanceof NodeStatus.Syncing)
                && !(status instanceof NodeStatus.Ready)
                && !(status instanceof NodeStatus.Degraded)) {
            throw IllegalNodeTransitionException.from(status, "markStopped");
        }
        status = new NodeStatus.Stopped(reason);
    }

    public void restart() {
        if (!(status instanceof NodeStatus.Stopped)) {
            throw IllegalNodeTransitionException.from(status, "restart");
        }
        status = new NodeStatus.Provisioning();
        pendingEvents.add(new NodeProvisioningStarted(id, Instant.now()));
    }

    public void terminate() {
        if (status instanceof NodeStatus.Terminating
                || status instanceof NodeStatus.Terminated
                || status instanceof NodeStatus.Failed) {
            throw IllegalNodeTransitionException.from(status, "terminate");
        }
        status = new NodeStatus.Terminating();
    }

    public void markTerminated() {
        if (!(status instanceof NodeStatus.Terminating)) {
            throw IllegalNodeTransitionException.from(status, "markTerminated");
        }
        status = new NodeStatus.Terminated();
    }

    public void fail(String reason) {
        requireNonBlank(reason, "reason");
        if (status instanceof NodeStatus.Terminated || status instanceof NodeStatus.Failed) {
            throw IllegalNodeTransitionException.from(status, "fail");
        }
        status = new NodeStatus.Failed(reason);
        pendingEvents.add(new NodeFailed(id, reason, Instant.now()));
    }

    public NodeId id() {
        return id;
    }

    public OwnerId owner() {
        return owner;
    }

    public Network network() {
        return network;
    }

    public ClientPair clientPair() {
        return clientPair;
    }

    public NodeOptions options() {
        return options;
    }

    public NodeStatus status() {
        return status;
    }

    public DeploymentRef deploymentRef() {
        return deploymentRef;
    }

    public List<NodeDomainEvent> pullEvents() {
        List<NodeDomainEvent> snapshot = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return snapshot;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

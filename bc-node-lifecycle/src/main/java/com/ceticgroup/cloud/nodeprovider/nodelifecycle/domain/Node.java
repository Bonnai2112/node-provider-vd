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

public final class Node {

    private final NodeId id;
    private final OwnerId owner;
    private final Network network;
    private final ClientPair clientPair;
    private final List<NodeDomainEvent> pendingEvents = new ArrayList<>();

    private NodeStatus status;

    private Node(NodeId id, OwnerId owner, Network network, ClientPair clientPair) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.network = Objects.requireNonNull(network, "network");
        this.clientPair = Objects.requireNonNull(clientPair, "clientPair");
        this.status = new NodeStatus.Requested();
    }

    public static Node request(NodeId id, OwnerId owner, Network network, ClientPair clientPair) {
        Node node = new Node(id, owner, network, clientPair);
        node.pendingEvents.add(new NodeRequested(id, owner, network, clientPair, Instant.now()));
        return node;
    }

    public static Node restore(
            NodeId id, OwnerId owner, Network network, ClientPair clientPair, NodeStatus status) {
        Node node = new Node(id, owner, network, clientPair);
        node.status = Objects.requireNonNull(status, "status");
        return node;
    }

    public void startProvisioning() {
        if (!(status instanceof NodeStatus.Requested)) {
            throw IllegalNodeTransitionException.from(status, "startProvisioning");
        }
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

    public NodeStatus status() {
        return status;
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

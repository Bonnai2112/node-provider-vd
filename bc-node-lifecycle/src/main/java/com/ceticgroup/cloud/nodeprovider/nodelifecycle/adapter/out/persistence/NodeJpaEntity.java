package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "nodes")
class NodeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "network", nullable = false, length = 20)
    private String network;

    @Column(name = "el_client", nullable = false, length = 20)
    private String elClient;

    @Column(name = "cl_client", nullable = false, length = 20)
    private String clClient;

    @Column(name = "status_kind", nullable = false, length = 30)
    private String statusKind;

    @Column(name = "endpoint_uri")
    private String endpointUri;

    @Column(name = "status_reason")
    private String statusReason;

    protected NodeJpaEntity() {}

    NodeJpaEntity(
            UUID id,
            UUID ownerId,
            String network,
            String elClient,
            String clClient,
            String statusKind,
            String endpointUri,
            String statusReason) {
        this.id = id;
        this.ownerId = ownerId;
        this.network = network;
        this.elClient = elClient;
        this.clClient = clClient;
        this.statusKind = statusKind;
        this.endpointUri = endpointUri;
        this.statusReason = statusReason;
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getNetwork() {
        return network;
    }

    String getElClient() {
        return elClient;
    }

    String getClClient() {
        return clClient;
    }

    String getStatusKind() {
        return statusKind;
    }

    String getEndpointUri() {
        return endpointUri;
    }

    String getStatusReason() {
        return statusReason;
    }
}

package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deployment_ref", columnDefinition = "jsonb")
    private String deploymentRef;

    @Column(name = "el_sync_kind", length = 20)
    private String elSyncKind;

    @Column(name = "el_sync_current_block")
    private Long elSyncCurrentBlock;

    @Column(name = "el_sync_highest_block")
    private Long elSyncHighestBlock;

    @Column(name = "cl_sync_kind", length = 20)
    private String clSyncKind;

    @Column(name = "cl_sync_head_slot")
    private Long clSyncHeadSlot;

    @Column(name = "cl_sync_distance")
    private Long clSyncDistance;

    @Column(name = "peers")
    private Integer peers;

    @Column(name = "last_observed_at")
    private Instant lastObservedAt;

    @Column(name = "el_blocks_per_second")
    private Double elBlocksPerSecond;

    @Column(name = "cl_slots_per_second")
    private Double clSlotsPerSecond;

    @Column(name = "validator", nullable = false)
    private boolean validator;

    @Column(name = "mev_boost", nullable = false)
    private boolean mevBoost;

    @Column(name = "fee_recipient", nullable = false, length = 42)
    private String feeRecipient;

    @Column(name = "graffiti", length = 64)
    private String graffiti;

    @Column(name = "mev_min_bid", length = 32)
    private String mevMinBid;

    @Column(name = "mev_build_factor")
    private Integer mevBuildFactor;

    protected NodeJpaEntity() {}

    NodeJpaEntity(
            UUID id,
            UUID ownerId,
            String network,
            String elClient,
            String clClient,
            String statusKind,
            String endpointUri,
            String statusReason,
            String deploymentRef,
            String elSyncKind,
            Long elSyncCurrentBlock,
            Long elSyncHighestBlock,
            String clSyncKind,
            Long clSyncHeadSlot,
            Long clSyncDistance,
            Integer peers,
            Instant lastObservedAt,
            Double elBlocksPerSecond,
            Double clSlotsPerSecond,
            boolean validator,
            boolean mevBoost,
            String feeRecipient,
            String graffiti,
            String mevMinBid,
            Integer mevBuildFactor) {
        this.id = id;
        this.ownerId = ownerId;
        this.network = network;
        this.elClient = elClient;
        this.clClient = clClient;
        this.statusKind = statusKind;
        this.endpointUri = endpointUri;
        this.statusReason = statusReason;
        this.deploymentRef = deploymentRef;
        this.elSyncKind = elSyncKind;
        this.elSyncCurrentBlock = elSyncCurrentBlock;
        this.elSyncHighestBlock = elSyncHighestBlock;
        this.clSyncKind = clSyncKind;
        this.clSyncHeadSlot = clSyncHeadSlot;
        this.clSyncDistance = clSyncDistance;
        this.peers = peers;
        this.lastObservedAt = lastObservedAt;
        this.elBlocksPerSecond = elBlocksPerSecond;
        this.clSlotsPerSecond = clSlotsPerSecond;
        this.validator = validator;
        this.mevBoost = mevBoost;
        this.feeRecipient = feeRecipient;
        this.graffiti = graffiti;
        this.mevMinBid = mevMinBid;
        this.mevBuildFactor = mevBuildFactor;
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

    String getDeploymentRef() {
        return deploymentRef;
    }

    String getElSyncKind() {
        return elSyncKind;
    }

    Long getElSyncCurrentBlock() {
        return elSyncCurrentBlock;
    }

    Long getElSyncHighestBlock() {
        return elSyncHighestBlock;
    }

    String getClSyncKind() {
        return clSyncKind;
    }

    Long getClSyncHeadSlot() {
        return clSyncHeadSlot;
    }

    Long getClSyncDistance() {
        return clSyncDistance;
    }

    Integer getPeers() {
        return peers;
    }

    Instant getLastObservedAt() {
        return lastObservedAt;
    }

    Double getElBlocksPerSecond() {
        return elBlocksPerSecond;
    }

    Double getClSlotsPerSecond() {
        return clSlotsPerSecond;
    }

    boolean isValidator() {
        return validator;
    }

    boolean isMevBoost() {
        return mevBoost;
    }

    String getFeeRecipient() {
        return feeRecipient;
    }

    String getGraffiti() {
        return graffiti;
    }

    String getMevMinBid() {
        return mevMinBid;
    }

    Integer getMevBuildFactor() {
        return mevBuildFactor;
    }
}

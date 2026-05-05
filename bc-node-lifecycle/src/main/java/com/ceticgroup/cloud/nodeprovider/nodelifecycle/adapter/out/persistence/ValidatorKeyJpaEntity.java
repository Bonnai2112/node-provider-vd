package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validator_keys")
class ValidatorKeyJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "pubkey", nullable = false, length = 98)
    private String pubkey;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected ValidatorKeyJpaEntity() {}

    ValidatorKeyJpaEntity(UUID id, UUID nodeId, String pubkey, Instant importedAt) {
        this.id = id;
        this.nodeId = nodeId;
        this.pubkey = pubkey;
        this.importedAt = importedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getNodeId() {
        return nodeId;
    }

    String getPubkey() {
        return pubkey;
    }

    Instant getImportedAt() {
        return importedAt;
    }
}

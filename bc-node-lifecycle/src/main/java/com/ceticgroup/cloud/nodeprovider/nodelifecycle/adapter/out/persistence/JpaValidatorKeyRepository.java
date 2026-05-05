package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class JpaValidatorKeyRepository implements ValidatorKeyRepository {

    private final SpringDataValidatorKeyJpaRepository jpa;

    JpaValidatorKeyRepository(SpringDataValidatorKeyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void saveAll(List<ValidatorKey> keys) {
        jpa.saveAll(keys.stream().map(JpaValidatorKeyRepository::toEntity).toList());
    }

    @Override
    public List<ValidatorKey> findByNode(NodeId nodeId) {
        return jpa.findByNodeIdOrderByImportedAtAsc(nodeId.value()).stream()
                .map(JpaValidatorKeyRepository::toDomain)
                .toList();
    }

    private static ValidatorKeyJpaEntity toEntity(ValidatorKey key) {
        return new ValidatorKeyJpaEntity(
                key.id(), key.nodeId().value(), key.pubkey(), key.importedAt());
    }

    private static ValidatorKey toDomain(ValidatorKeyJpaEntity entity) {
        return new ValidatorKey(
                entity.getId(),
                new NodeId(entity.getNodeId()),
                entity.getPubkey(),
                entity.getImportedAt());
    }
}

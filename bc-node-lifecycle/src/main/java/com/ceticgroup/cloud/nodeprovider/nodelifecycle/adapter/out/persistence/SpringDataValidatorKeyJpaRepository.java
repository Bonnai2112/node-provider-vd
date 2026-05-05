package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataValidatorKeyJpaRepository extends JpaRepository<ValidatorKeyJpaEntity, UUID> {

    List<ValidatorKeyJpaEntity> findByNodeIdOrderByImportedAtAsc(UUID nodeId);
}

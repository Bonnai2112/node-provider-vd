package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataNodeJpaRepository extends JpaRepository<NodeJpaEntity, UUID> {

    List<NodeJpaEntity> findByOwnerId(UUID ownerId);
}

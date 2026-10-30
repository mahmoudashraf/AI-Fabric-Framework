package com.ai.fabric.platform.backend.aiworkspace.repository;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AIWorkspaceDomainRepository extends JpaRepository<AIWorkspaceDomainEntity, String> {

    List<AIWorkspaceDomainEntity> findByInstallationEntityIdOrderByHostname(String installationEntityId);

    Optional<AIWorkspaceDomainEntity> findByHostname(String hostname);

    Optional<AIWorkspaceDomainEntity> findByHostnameAndStatusIn(
        String hostname,
        Collection<AIWorkspaceDomainStatus> statuses
    );

    List<AIWorkspaceDomainEntity> findAllByOrderByHostname();

    @Query("""
        SELECT d FROM AIWorkspaceDomainEntity d, AIWorkspaceInstallationEntity i
         WHERE d.installationEntityId = i.id AND d.status = :status
           AND i.deliveryMode = :deliveryMode AND i.status <> :disabledStatus
         ORDER BY d.updatedAt ASC, d.id ASC
        """)
    List<AIWorkspaceDomainEntity> findVerificationCandidates(
        @Param("status") AIWorkspaceDomainStatus status,
        @Param("deliveryMode") AIWorkspaceDeliveryMode deliveryMode,
        @Param("disabledStatus") AIWorkspaceInstallationStatus disabledStatus,
        Pageable pageable
    );

    void deleteByInstallationEntityId(String installationEntityId);
}

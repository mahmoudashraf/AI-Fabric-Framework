package com.ai.fabric.platform.backend.aiworkspace.repository;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AIWorkspaceInstallationRepository extends JpaRepository<AIWorkspaceInstallationEntity, String> {

    List<AIWorkspaceInstallationEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<AIWorkspaceInstallationEntity> findByCustomerIdAndInstallationId(String customerId, String installationId);

    Optional<AIWorkspaceInstallationEntity> findByInstallationId(String installationId);

    boolean existsByConsumerEntityId(String consumerEntityId);

    List<AIWorkspaceInstallationEntity> findByConsumerEntityIdAndStatusIn(
        String consumerEntityId,
        Collection<AIWorkspaceInstallationStatus> statuses
    );
}

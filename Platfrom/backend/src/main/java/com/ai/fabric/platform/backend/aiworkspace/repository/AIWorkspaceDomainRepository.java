package com.ai.fabric.platform.backend.aiworkspace.repository;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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

    void deleteByInstallationEntityId(String installationEntityId);
}

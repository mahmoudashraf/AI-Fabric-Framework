package com.ai.fabric.platform.backend.deployment.authority;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuntimeAuthoritySnapshotRepository extends JpaRepository<RuntimeAuthoritySnapshotEntity, String> {
    Optional<RuntimeAuthoritySnapshotEntity> findByReleaseId(String releaseId);
}

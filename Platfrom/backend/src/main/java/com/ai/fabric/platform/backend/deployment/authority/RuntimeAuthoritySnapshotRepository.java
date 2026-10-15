package com.ai.fabric.platform.backend.deployment.authority;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface RuntimeAuthoritySnapshotRepository extends JpaRepository<RuntimeAuthoritySnapshotEntity, String> {
    Optional<RuntimeAuthoritySnapshotEntity> findByReleaseId(String releaseId);
    List<RuntimeAuthoritySnapshotEntity> findByDeploymentIdAndTargetProfileIdAndVersionIdAndFingerprintAndReleaseIdIsNull(
        String deploymentId, String targetProfileId, String versionId, String fingerprint);
}

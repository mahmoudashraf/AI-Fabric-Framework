package com.ai.fabric.platform.backend.documentstorage.repository;

import com.ai.fabric.platform.backend.documentstorage.entity.DocumentStorageSelectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface DocumentStorageSelectionRepository extends JpaRepository<DocumentStorageSelectionEntity, String> {
    Optional<DocumentStorageSelectionEntity> findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId(
        String uploadId, String deploymentId, String targetProfileId, String releaseId);
    List<DocumentStorageSelectionEntity> findByDeploymentIdAndTargetProfileIdAndReleaseIdOrderByUploadIdAsc(
        String deploymentId, String targetProfileId, String releaseId);
}

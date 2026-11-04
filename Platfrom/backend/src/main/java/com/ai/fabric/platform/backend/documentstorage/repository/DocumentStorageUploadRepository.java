package com.ai.fabric.platform.backend.documentstorage.repository;

import com.ai.fabric.platform.backend.documentstorage.entity.DocumentStorageUploadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentStorageUploadRepository extends JpaRepository<DocumentStorageUploadEntity, String> {
    List<DocumentStorageUploadEntity> findByConnectionIdOrderByCreatedAtDesc(String connectionId);
    Optional<DocumentStorageUploadEntity> findByConnectionIdAndOperationKey(String connectionId, String operationKey);
}

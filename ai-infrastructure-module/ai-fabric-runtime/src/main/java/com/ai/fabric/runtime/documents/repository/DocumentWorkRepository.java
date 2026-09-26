package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentWorkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentWorkRepository extends JpaRepository<DocumentWorkEntity, String> {
    List<DocumentWorkEntity> findByManifestIdAndOperationOrderByCreatedAtAsc(String manifestId, DocumentWorkEntity.Operation operation);
    List<DocumentWorkEntity> findByManifestIdAndOperationAndSubmissionAttemptOrderByCreatedAtAsc(
        String manifestId,
        DocumentWorkEntity.Operation operation,
        int submissionAttempt
    );
    List<DocumentWorkEntity> findByManifestIdOrderByCreatedAtAsc(String manifestId);
    Optional<DocumentWorkEntity> findByManifestIdAndOperationAndFrameworkWorkId(
        String manifestId,
        DocumentWorkEntity.Operation operation,
        String frameworkWorkId
    );
    long deleteByManifestId(String manifestId);
}

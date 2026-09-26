package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentManifestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentManifestRepository extends JpaRepository<DocumentManifestEntity, String> {
    Optional<DocumentManifestEntity> findBySourceIdAndSourceVersion(String sourceId, long sourceVersion);
    List<DocumentManifestEntity> findBySourceIdOrderBySourceVersionAsc(String sourceId);
    List<DocumentManifestEntity> findBySourceIdAndStateInOrderBySourceVersionAsc(String sourceId, Collection<DocumentManifestEntity.State> states);
    Optional<DocumentManifestEntity> findBySourceIdAndState(String sourceId, DocumentManifestEntity.State state);
    Optional<DocumentManifestEntity> findFirstBySourceIdAndStateOrderBySourceVersionDesc(String sourceId, DocumentManifestEntity.State state);
    List<DocumentManifestEntity> findByStateIn(Collection<DocumentManifestEntity.State> states);
    List<DocumentManifestEntity> findByTenantIdAndDeploymentIdAndStateAndUpdatedAtBeforeOrderByUpdatedAtAsc(
        String tenantId,
        String deploymentId,
        DocumentManifestEntity.State state,
        Instant cutoff,
        Pageable pageable
    );
    List<DocumentManifestEntity> findByStateAndUpdatedAtBeforeOrderByUpdatedAtAsc(
        DocumentManifestEntity.State state,
        Instant cutoff,
        Pageable pageable
    );
    boolean existsBySourceIdAndSourceVersionAndProviderRevisionFingerprint(String sourceId, long sourceVersion, String revision);
}

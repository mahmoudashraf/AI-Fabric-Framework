package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentManifestChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentManifestChunkRepository extends JpaRepository<DocumentManifestChunkEntity, String> {
    List<DocumentManifestChunkEntity> findByManifestIdOrderByChunkIndexAsc(String manifestId);
    long countByManifestId(String manifestId);
    void deleteByManifestId(String manifestId);
}

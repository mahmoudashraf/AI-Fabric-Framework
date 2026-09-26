package com.ai.fabric.runtime.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "loomai_document_manifest_chunk",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_loomai_document_manifest_chunk_entity",
        columnNames = {"manifest_id", "entity_id"}
    ),
    indexes = @Index(name = "idx_loomai_document_chunk_manifest", columnList = "manifest_id,chunk_index")
)
public class DocumentManifestChunkEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "manifest_id", nullable = false, length = 64)
    private String manifestId;

    @Column(name = "source_document_id", nullable = false, length = 256)
    private String sourceDocumentId;

    @Column(name = "chunk_id", nullable = false, length = 64)
    private String chunkId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "entity_id", nullable = false, length = 512)
    private String entityId;

    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;
}

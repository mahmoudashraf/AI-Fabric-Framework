package com.ai.fabric.runtime.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
    name = "loomai_document_manifest",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_loomai_document_manifest_source_version",
        columnNames = {"source_id", "source_version"}
    ),
    indexes = {
        @Index(name = "idx_loomai_document_manifest_source_state", columnList = "source_id,lifecycle_state"),
        @Index(name = "idx_loomai_document_manifest_boundary", columnList = "tenant_id,deployment_id,lifecycle_state")
    }
)
public class DocumentManifestEntity {

    @Id
    @Column(name = "manifest_id", length = 64)
    private String manifestId;

    @Column(name = "manifest_schema_version", nullable = false)
    private int manifestSchemaVersion;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "provider_revision_fingerprint", nullable = false, length = 64)
    private String providerRevisionFingerprint;

    @Column(name = "source_name", nullable = false, length = 512)
    private String sourceName;

    @Column(name = "entity_type", nullable = false, length = 128)
    private String entityType;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "deployment_id", nullable = false, length = 128)
    private String deploymentId;

    @Column(name = "visibility", nullable = false, length = 64)
    private String visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private State state = State.DRAFT;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "accepted_index_work_count", nullable = false)
    private int acceptedIndexWorkCount;

    @Column(name = "accepted_delete_work_count", nullable = false)
    private int acceptedDeleteWorkCount;

    @Column(name = "index_submission_attempt", nullable = false)
    private int indexSubmissionAttempt;

    @Column(name = "delete_submission_attempt", nullable = false)
    private int deleteSubmissionAttempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delete_purpose", length = 32)
    private DeletePurpose deletePurpose;

    @Column(name = "warnings_json", nullable = false, columnDefinition = "TEXT")
    private String warningsJson = "[]";

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum State {
        DRAFT,
        INDEX_SUBMITTED,
        INDEX_PARTIAL,
        ACTIVE,
        FAILED,
        SUPERSEDED,
        DELETE_SUBMITTED,
        DELETE_PARTIAL,
        DELETE_FAILED,
        DELETED
    }

    public enum DeletePurpose {
        SOURCE_REMOVAL,
        SUPERSEDED_REPLACEMENT,
        FAILED_CANDIDATE_CLEANUP
    }
}

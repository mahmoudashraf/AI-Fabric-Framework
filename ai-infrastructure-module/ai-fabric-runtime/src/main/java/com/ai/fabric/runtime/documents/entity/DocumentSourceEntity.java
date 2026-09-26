package com.ai.fabric.runtime.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
    name = "loomai_document_source",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_loomai_document_source_logical_key",
        columnNames = "logical_source_key"
    )
)
public class DocumentSourceEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "logical_source_key", nullable = false, length = 64)
    private String logicalSourceKey;

    @Column(name = "dataset_id", nullable = false, length = 128)
    private String datasetId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "deployment_id", nullable = false, length = 128)
    private String deploymentId;

    @Column(name = "connector_type", nullable = false, length = 64)
    private String connectorType;

    @Column(name = "connector_binding_ref", length = 128)
    private String connectorBindingRef;

    @Column(name = "object_locator", nullable = false, length = 1024)
    private String objectLocator;

    @Column(name = "object_locator_digest", nullable = false, length = 64)
    private String objectLocatorDigest;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "media_type", length = 128)
    private String mediaType;

    @Column(name = "provider_version_id", length = 512)
    private String providerVersionId;

    @Column(name = "provider_etag", length = 256)
    private String providerEtag;

    @Column(name = "provider_revision_fingerprint", nullable = false, length = 64)
    private String providerRevisionFingerprint;

    @Column(name = "content_fingerprint", length = 64)
    private String contentFingerprint;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Column(name = "provider_last_modified")
    private Instant providerLastModified;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "active_source_version")
    private Long activeSourceVersion;

    @Column(name = "visibility", nullable = false, length = 64)
    private String visibility;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    private String metadataJson = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private Status status = Status.REGISTERED;

    @Column(name = "last_failure_code", length = 128)
    private String lastFailureCode;

    @Column(name = "last_failure_message", length = 512)
    private String lastFailureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public enum Status {
        REGISTERED,
        PREPARING,
        INDEXING,
        ACTIVE,
        REPLACING,
        DELETE_PENDING,
        DELETING,
        DELETED,
        FAILED
    }
}

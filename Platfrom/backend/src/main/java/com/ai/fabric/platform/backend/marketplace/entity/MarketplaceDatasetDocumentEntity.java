package com.ai.fabric.platform.backend.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_marketplace_dataset_documents")
public class MarketplaceDatasetDocumentEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String datasetHandleId;

    @Column(nullable = false)
    private String documentId;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false, length = 128)
    private String contentFingerprint;

    @Column(nullable = false, length = 128)
    private String datasetHash;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    private Instant lastSyncedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDatasetHandleId() {
        return datasetHandleId;
    }

    public void setDatasetHandleId(String datasetHandleId) {
        this.datasetHandleId = datasetHandleId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public void setContentFingerprint(String contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    public String getDatasetHash() {
        return datasetHash;
    }

    public void setDatasetHash(String datasetHash) {
        this.datasetHash = datasetHash;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

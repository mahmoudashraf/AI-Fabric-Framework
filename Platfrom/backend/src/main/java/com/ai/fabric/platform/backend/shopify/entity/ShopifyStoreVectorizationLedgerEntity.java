package com.ai.fabric.platform.backend.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_store_vectorization_ledgers")
public class ShopifyStoreVectorizationLedgerEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String shopDomain;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String sourceCategory;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private String sourceObjectId;

    @Column
    private String sourceRecordVersion;

    @Column(nullable = false)
    private String indexedOutputFingerprint;

    @Column(columnDefinition = "TEXT")
    private String fieldFingerprintsJson;

    @Column
    private Instant firstIndexedAt;

    @Column(nullable = false)
    private Instant lastIndexedAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column
    private String lastIndexRunId;

    @Column
    private String lastIndexedDeploymentHash;

    @Column
    private Instant deletedAt;

    @Column
    private String lastTriggerDecision;

    @Column
    private String lastTriggerReason;

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

    public String getShopDomain() {
        return shopDomain;
    }

    public void setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getSourceObjectId() {
        return sourceObjectId;
    }

    public void setSourceObjectId(String sourceObjectId) {
        this.sourceObjectId = sourceObjectId;
    }

    public String getSourceRecordVersion() {
        return sourceRecordVersion;
    }

    public void setSourceRecordVersion(String sourceRecordVersion) {
        this.sourceRecordVersion = sourceRecordVersion;
    }

    public String getIndexedOutputFingerprint() {
        return indexedOutputFingerprint;
    }

    public void setIndexedOutputFingerprint(String indexedOutputFingerprint) {
        this.indexedOutputFingerprint = indexedOutputFingerprint;
    }

    public String getFieldFingerprintsJson() {
        return fieldFingerprintsJson;
    }

    public void setFieldFingerprintsJson(String fieldFingerprintsJson) {
        this.fieldFingerprintsJson = fieldFingerprintsJson;
    }

    public Instant getFirstIndexedAt() {
        return firstIndexedAt;
    }

    public void setFirstIndexedAt(Instant firstIndexedAt) {
        this.firstIndexedAt = firstIndexedAt;
    }

    public Instant getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(Instant lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getLastIndexRunId() {
        return lastIndexRunId;
    }

    public void setLastIndexRunId(String lastIndexRunId) {
        this.lastIndexRunId = lastIndexRunId;
    }

    public String getLastIndexedDeploymentHash() {
        return lastIndexedDeploymentHash;
    }

    public void setLastIndexedDeploymentHash(String lastIndexedDeploymentHash) {
        this.lastIndexedDeploymentHash = lastIndexedDeploymentHash;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getLastTriggerDecision() {
        return lastTriggerDecision;
    }

    public void setLastTriggerDecision(String lastTriggerDecision) {
        this.lastTriggerDecision = lastTriggerDecision;
    }

    public String getLastTriggerReason() {
        return lastTriggerReason;
    }

    public void setLastTriggerReason(String lastTriggerReason) {
        this.lastTriggerReason = lastTriggerReason;
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

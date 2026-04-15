package com.ai.fabric.platform.backend.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_marketplace_dataset_handles")
public class MarketplaceDatasetHandleEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String pluginId;

    @Column(nullable = false)
    private String pluginVersionId;

    @Column(nullable = false)
    private String datasetId;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String storageScope;

    @Column(nullable = false)
    private String sharingScope;

    @Column(nullable = false)
    private String handleRef;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, length = 128)
    private String datasetHash;

    private Instant lastSyncAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

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

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginVersionId() {
        return pluginVersionId;
    }

    public void setPluginVersionId(String pluginVersionId) {
        this.pluginVersionId = pluginVersionId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getStorageScope() {
        return storageScope;
    }

    public void setStorageScope(String storageScope) {
        this.storageScope = storageScope;
    }

    public String getSharingScope() {
        return sharingScope;
    }

    public void setSharingScope(String sharingScope) {
        this.sharingScope = sharingScope;
    }

    public String getHandleRef() {
        return handleRef;
    }

    public void setHandleRef(String handleRef) {
        this.handleRef = handleRef;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDatasetHash() {
        return datasetHash;
    }

    public void setDatasetHash(String datasetHash) {
        this.datasetHash = datasetHash;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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

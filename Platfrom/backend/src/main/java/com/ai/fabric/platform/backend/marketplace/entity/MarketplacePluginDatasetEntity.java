package com.ai.fabric.platform.backend.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_marketplace_plugin_datasets")
public class MarketplacePluginDatasetEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String pluginId;

    @Column(nullable = false)
    private String pluginVersionId;

    @Column(nullable = false)
    private String datasetId;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private String storageScope;

    @Column(nullable = false)
    private String sharingScope;

    @Column(nullable = false)
    private String ingestionMode;

    @Column(nullable = false)
    private String updateStrategy;

    @Column(length = 128)
    private String vectorizationProfile;

    @Column(length = 256)
    private String handleTemplate;

    @Column(length = 512)
    private String seedDatasetRef;

    @Column(length = 64)
    private String connectorType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String connectorConfigJson;

    @Column(nullable = false, length = 128)
    private String datasetHash;

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

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
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

    public String getIngestionMode() {
        return ingestionMode;
    }

    public void setIngestionMode(String ingestionMode) {
        this.ingestionMode = ingestionMode;
    }

    public String getUpdateStrategy() {
        return updateStrategy;
    }

    public void setUpdateStrategy(String updateStrategy) {
        this.updateStrategy = updateStrategy;
    }

    public String getVectorizationProfile() {
        return vectorizationProfile;
    }

    public void setVectorizationProfile(String vectorizationProfile) {
        this.vectorizationProfile = vectorizationProfile;
    }

    public String getHandleTemplate() {
        return handleTemplate;
    }

    public void setHandleTemplate(String handleTemplate) {
        this.handleTemplate = handleTemplate;
    }

    public String getSeedDatasetRef() {
        return seedDatasetRef;
    }

    public void setSeedDatasetRef(String seedDatasetRef) {
        this.seedDatasetRef = seedDatasetRef;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public String getConnectorConfigJson() {
        return connectorConfigJson;
    }

    public void setConnectorConfigJson(String connectorConfigJson) {
        this.connectorConfigJson = connectorConfigJson;
    }

    public String getDatasetHash() {
        return datasetHash;
    }

    public void setDatasetHash(String datasetHash) {
        this.datasetHash = datasetHash;
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

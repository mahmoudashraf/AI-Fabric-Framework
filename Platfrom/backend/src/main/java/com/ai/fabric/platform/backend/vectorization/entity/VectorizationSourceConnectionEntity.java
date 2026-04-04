package com.ai.fabric.platform.backend.vectorization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_vectorization_source_connections")
public class VectorizationSourceConnectionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String adapterType;

    @Column(nullable = false)
    private String authMode;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String connectionConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String secretReferencesJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String discoverySummaryJson;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(String adapterType) {
        this.adapterType = adapterType;
    }

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConnectionConfigJson() {
        return connectionConfigJson;
    }

    public void setConnectionConfigJson(String connectionConfigJson) {
        this.connectionConfigJson = connectionConfigJson;
    }

    public String getSecretReferencesJson() {
        return secretReferencesJson;
    }

    public void setSecretReferencesJson(String secretReferencesJson) {
        this.secretReferencesJson = secretReferencesJson;
    }

    public String getDiscoverySummaryJson() {
        return discoverySummaryJson;
    }

    public void setDiscoverySummaryJson(String discoverySummaryJson) {
        this.discoverySummaryJson = discoverySummaryJson;
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

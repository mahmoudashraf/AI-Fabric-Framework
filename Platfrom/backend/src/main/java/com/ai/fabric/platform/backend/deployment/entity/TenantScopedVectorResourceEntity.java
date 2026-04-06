package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_tenant_scoped_vector_resources")
public class TenantScopedVectorResourceEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String tenantId;

    private String deploymentId;

    private String deploymentVersionId;

    private String deploymentReleaseId;

    @Column(nullable = false)
    private String vendor;

    @Column(nullable = false)
    private String vectorStrategy;

    @Column(nullable = false)
    private String vectorProvisioningMode;

    @Column(nullable = false)
    private String vectorStoragePosture;

    @Column(nullable = false)
    private String resourceStatus;

    @Column(nullable = false)
    private String scopeType;

    @Column(nullable = false)
    private String registryKey;

    private String rootResourceLabel;

    private String rootResourceValue;

    private String scopePrefix;

    private String tenantHandle;

    private String scopePattern;

    private String lifecycleOwner;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detailsJson;

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

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getDeploymentVersionId() {
        return deploymentVersionId;
    }

    public void setDeploymentVersionId(String deploymentVersionId) {
        this.deploymentVersionId = deploymentVersionId;
    }

    public String getDeploymentReleaseId() {
        return deploymentReleaseId;
    }

    public void setDeploymentReleaseId(String deploymentReleaseId) {
        this.deploymentReleaseId = deploymentReleaseId;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getVectorStrategy() {
        return vectorStrategy;
    }

    public void setVectorStrategy(String vectorStrategy) {
        this.vectorStrategy = vectorStrategy;
    }

    public String getVectorProvisioningMode() {
        return vectorProvisioningMode;
    }

    public void setVectorProvisioningMode(String vectorProvisioningMode) {
        this.vectorProvisioningMode = vectorProvisioningMode;
    }

    public String getVectorStoragePosture() {
        return vectorStoragePosture;
    }

    public void setVectorStoragePosture(String vectorStoragePosture) {
        this.vectorStoragePosture = vectorStoragePosture;
    }

    public String getResourceStatus() {
        return resourceStatus;
    }

    public void setResourceStatus(String resourceStatus) {
        this.resourceStatus = resourceStatus;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRootResourceLabel() {
        return rootResourceLabel;
    }

    public void setRootResourceLabel(String rootResourceLabel) {
        this.rootResourceLabel = rootResourceLabel;
    }

    public String getRootResourceValue() {
        return rootResourceValue;
    }

    public void setRootResourceValue(String rootResourceValue) {
        this.rootResourceValue = rootResourceValue;
    }

    public String getScopePrefix() {
        return scopePrefix;
    }

    public void setScopePrefix(String scopePrefix) {
        this.scopePrefix = scopePrefix;
    }

    public String getTenantHandle() {
        return tenantHandle;
    }

    public void setTenantHandle(String tenantHandle) {
        this.tenantHandle = tenantHandle;
    }

    public String getScopePattern() {
        return scopePattern;
    }

    public void setScopePattern(String scopePattern) {
        this.scopePattern = scopePattern;
    }

    public String getLifecycleOwner() {
        return lifecycleOwner;
    }

    public void setLifecycleOwner(String lifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
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

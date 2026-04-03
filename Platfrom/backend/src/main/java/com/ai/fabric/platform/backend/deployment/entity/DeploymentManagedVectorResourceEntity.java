package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_managed_vector_resources")
public class DeploymentManagedVectorResourceEntity {

    @Id
    private String id;

    @Column(nullable = false)
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
    private String managedMode;

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String resourceName;

    @Column(columnDefinition = "TEXT")
    private String resourceReference;

    @Column(columnDefinition = "TEXT")
    private String endpoint;

    @Column(nullable = false)
    private String resourceStatus;

    private String provisioningState;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String secretReferenceNamesJson;

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

    public String getManagedMode() {
        return managedMode;
    }

    public void setManagedMode(String managedMode) {
        this.managedMode = managedMode;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceReference() {
        return resourceReference;
    }

    public void setResourceReference(String resourceReference) {
        this.resourceReference = resourceReference;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getResourceStatus() {
        return resourceStatus;
    }

    public void setResourceStatus(String resourceStatus) {
        this.resourceStatus = resourceStatus;
    }

    public String getProvisioningState() {
        return provisioningState;
    }

    public void setProvisioningState(String provisioningState) {
        this.provisioningState = provisioningState;
    }

    public String getSecretReferenceNamesJson() {
        return secretReferenceNamesJson;
    }

    public void setSecretReferenceNamesJson(String secretReferenceNamesJson) {
        this.secretReferenceNamesJson = secretReferenceNamesJson;
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

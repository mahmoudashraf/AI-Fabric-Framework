package com.ai.fabric.platform.backend.deployment.entity;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "deployment_provider_resource_handles")
public class DeploymentProviderResourceHandleEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "deployment_id", nullable = false, length = 64)
    private String deploymentId;

    @Column(name = "release_id", length = 64)
    private String releaseId;

    @Column(name = "target_profile_id", nullable = false, length = 64)
    private String targetProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 64)
    private DeploymentProviderType providerType;

    @Column(name = "resource_kind", nullable = false, length = 64)
    private String resourceKind;

    @Column(name = "provider_resource_uuid", nullable = false, length = 255)
    private String providerResourceUuid;

    @Column(name = "provider_project_uuid", length = 255)
    private String providerProjectUuid;

    @Column(name = "provider_environment_uuid", length = 255)
    private String providerEnvironmentUuid;

    @Column(name = "provider_server_uuid", length = 255)
    private String providerServerUuid;

    @Column(name = "fqdn", length = 512)
    private String fqdn;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "last_observed_status", length = 128)
    private String lastObservedStatus;

    @Column(name = "last_observed_at")
    private Instant lastObservedAt;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
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

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public String getTargetProfileId() {
        return targetProfileId;
    }

    public void setTargetProfileId(String targetProfileId) {
        this.targetProfileId = targetProfileId;
    }

    public DeploymentProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(DeploymentProviderType providerType) {
        this.providerType = providerType;
    }

    public String getResourceKind() {
        return resourceKind;
    }

    public void setResourceKind(String resourceKind) {
        this.resourceKind = resourceKind;
    }

    public String getProviderResourceUuid() {
        return providerResourceUuid;
    }

    public void setProviderResourceUuid(String providerResourceUuid) {
        this.providerResourceUuid = providerResourceUuid;
    }

    public String getProviderProjectUuid() {
        return providerProjectUuid;
    }

    public void setProviderProjectUuid(String providerProjectUuid) {
        this.providerProjectUuid = providerProjectUuid;
    }

    public String getProviderEnvironmentUuid() {
        return providerEnvironmentUuid;
    }

    public void setProviderEnvironmentUuid(String providerEnvironmentUuid) {
        this.providerEnvironmentUuid = providerEnvironmentUuid;
    }

    public String getProviderServerUuid() {
        return providerServerUuid;
    }

    public void setProviderServerUuid(String providerServerUuid) {
        this.providerServerUuid = providerServerUuid;
    }

    public String getFqdn() {
        return fqdn;
    }

    public void setFqdn(String fqdn) {
        this.fqdn = fqdn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastObservedStatus() {
        return lastObservedStatus;
    }

    public void setLastObservedStatus(String lastObservedStatus) {
        this.lastObservedStatus = lastObservedStatus;
    }

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }

    public void setLastObservedAt(Instant lastObservedAt) {
        this.lastObservedAt = lastObservedAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
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

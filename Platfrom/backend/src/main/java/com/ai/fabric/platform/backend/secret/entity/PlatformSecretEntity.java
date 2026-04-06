package com.ai.fabric.platform.backend.secret.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_secrets")
public class PlatformSecretEntity {

    @Id
    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "secret_value", nullable = false, columnDefinition = "TEXT")
    private String secretValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 64)
    private PlatformSecretScopeType scopeType;

    @Column(name = "deployment_id", length = 64)
    private String deploymentId;

    @Column(name = "secret_purpose", length = 128)
    private String secretPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", length = 64)
    private PlatformSecretOwnerType ownerType;

    @Column(name = "managed_by_platform", nullable = false)
    private boolean managedByPlatform;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanup_policy", length = 64)
    private PlatformSecretCleanupPolicy cleanupPolicy;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSecretValue() {
        return secretValue;
    }

    public void setSecretValue(String secretValue) {
        this.secretValue = secretValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public PlatformSecretScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(PlatformSecretScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getSecretPurpose() {
        return secretPurpose;
    }

    public void setSecretPurpose(String secretPurpose) {
        this.secretPurpose = secretPurpose;
    }

    public PlatformSecretOwnerType getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(PlatformSecretOwnerType ownerType) {
        this.ownerType = ownerType;
    }

    public boolean isManagedByPlatform() {
        return managedByPlatform;
    }

    public void setManagedByPlatform(boolean managedByPlatform) {
        this.managedByPlatform = managedByPlatform;
    }

    public PlatformSecretCleanupPolicy getCleanupPolicy() {
        return cleanupPolicy;
    }

    public void setCleanupPolicy(PlatformSecretCleanupPolicy cleanupPolicy) {
        this.cleanupPolicy = cleanupPolicy;
    }
}

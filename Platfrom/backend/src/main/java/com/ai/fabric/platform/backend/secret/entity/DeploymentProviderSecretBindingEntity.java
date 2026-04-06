package com.ai.fabric.platform.backend.secret.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_provider_secret_bindings")
public class DeploymentProviderSecretBindingEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "deployment_id", nullable = false, length = 64)
    private String deploymentId;

    @Column(name = "secret_purpose", nullable = false, length = 128)
    private String secretPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_mode", nullable = false, length = 64)
    private DeploymentProviderSecretBindingMode bindingMode;

    @Column(name = "secret_name", length = 255)
    private String secretName;

    @Column(name = "secondary_secret_name", length = 255)
    private String secondarySecretName;

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

    public String getSecretPurpose() {
        return secretPurpose;
    }

    public void setSecretPurpose(String secretPurpose) {
        this.secretPurpose = secretPurpose;
    }

    public DeploymentProviderSecretBindingMode getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(DeploymentProviderSecretBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getSecondarySecretName() {
        return secondarySecretName;
    }

    public void setSecondarySecretName(String secondarySecretName) {
        this.secondarySecretName = secondarySecretName;
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

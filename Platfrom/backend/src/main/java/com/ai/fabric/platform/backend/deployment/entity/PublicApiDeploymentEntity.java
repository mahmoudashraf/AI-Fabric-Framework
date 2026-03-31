package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_public_api_deployments")
public class PublicApiDeploymentEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String externalDeploymentKey;

    @Column(nullable = false, unique = true)
    private String deploymentId;

    @Column(columnDefinition = "TEXT")
    private String callbackMetadataJson;

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getExternalDeploymentKey() {
        return externalDeploymentKey;
    }

    public void setExternalDeploymentKey(String externalDeploymentKey) {
        this.externalDeploymentKey = externalDeploymentKey;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getCallbackMetadataJson() {
        return callbackMetadataJson;
    }

    public void setCallbackMetadataJson(String callbackMetadataJson) {
        this.callbackMetadataJson = callbackMetadataJson;
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

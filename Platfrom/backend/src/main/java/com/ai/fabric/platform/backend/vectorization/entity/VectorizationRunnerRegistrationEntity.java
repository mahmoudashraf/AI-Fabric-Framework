package com.ai.fabric.platform.backend.vectorization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_vectorization_runner_registrations")
public class VectorizationRunnerRegistrationEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String runnerMode;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String tokenHash;

    private String tokenHint;

    private Instant tokenExpiresAt;

    private Instant lastConnectedAt;

    private String runnerInstanceId;

    private String productVersion;

    private String compatibilityVersion;

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

    public String getRunnerMode() {
        return runnerMode;
    }

    public void setRunnerMode(String runnerMode) {
        this.runnerMode = runnerMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenHint() {
        return tokenHint;
    }

    public void setTokenHint(String tokenHint) {
        this.tokenHint = tokenHint;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Instant tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public Instant getLastConnectedAt() {
        return lastConnectedAt;
    }

    public void setLastConnectedAt(Instant lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
    }

    public String getRunnerInstanceId() {
        return runnerInstanceId;
    }

    public void setRunnerInstanceId(String runnerInstanceId) {
        this.runnerInstanceId = runnerInstanceId;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public String getCompatibilityVersion() {
        return compatibilityVersion;
    }

    public void setCompatibilityVersion(String compatibilityVersion) {
        this.compatibilityVersion = compatibilityVersion;
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

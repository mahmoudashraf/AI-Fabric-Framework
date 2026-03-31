package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_releases")
public class DeploymentReleaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String deploymentVersionId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String verificationStatus;

    @Column(nullable = false)
    private String provisioningStatus;

    @Column(nullable = false)
    private String provisioningTarget;

    private String currentStepKey;

    private String currentStepDescription;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String provisioningDetailsJson;

    private String verificationRunId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant appliedAt;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getProvisioningStatus() {
        return provisioningStatus;
    }

    public void setProvisioningStatus(String provisioningStatus) {
        this.provisioningStatus = provisioningStatus;
    }

    public String getProvisioningTarget() {
        return provisioningTarget;
    }

    public void setProvisioningTarget(String provisioningTarget) {
        this.provisioningTarget = provisioningTarget;
    }

    public String getCurrentStepKey() {
        return currentStepKey;
    }

    public void setCurrentStepKey(String currentStepKey) {
        this.currentStepKey = currentStepKey;
    }

    public String getCurrentStepDescription() {
        return currentStepDescription;
    }

    public void setCurrentStepDescription(String currentStepDescription) {
        this.currentStepDescription = currentStepDescription;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getProvisioningDetailsJson() {
        return provisioningDetailsJson;
    }

    public void setProvisioningDetailsJson(String provisioningDetailsJson) {
        this.provisioningDetailsJson = provisioningDetailsJson;
    }

    public String getVerificationRunId() {
        return verificationRunId;
    }

    public void setVerificationRunId(String verificationRunId) {
        this.verificationRunId = verificationRunId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

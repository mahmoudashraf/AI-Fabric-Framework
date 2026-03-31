package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_verification_runs")
public class DeploymentVerificationRunEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String releaseId;

    @Column(nullable = false)
    private String deploymentVersionId;

    @Column(nullable = false)
    private String verificationType;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String summaryMessage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String checksJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant completedAt;

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

    public String getDeploymentVersionId() {
        return deploymentVersionId;
    }

    public void setDeploymentVersionId(String deploymentVersionId) {
        this.deploymentVersionId = deploymentVersionId;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(String verificationType) {
        this.verificationType = verificationType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    public String getChecksJson() {
        return checksJson;
    }

    public void setChecksJson(String checksJson) {
        this.checksJson = checksJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

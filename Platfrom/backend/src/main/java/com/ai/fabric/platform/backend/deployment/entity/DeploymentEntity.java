package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployments")
public class DeploymentEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String environmentName;

    @Column(nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String status;

    private String activeDraftId;

    private String activeVersionId;

    private String runtimeBaseUrl;

    private String connectorBaseUrl;

    @Column(columnDefinition = "TEXT")
    private String sourceRepositoryOverride;

    @Column(columnDefinition = "TEXT")
    private String sourceBranchOverride;

    @Column(nullable = false)
    private boolean approvalRequiredForApply;

    @Column(nullable = false)
    private boolean approvalRequiredForDelete;

    private Instant archivedAt;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public void setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActiveDraftId() {
        return activeDraftId;
    }

    public void setActiveDraftId(String activeDraftId) {
        this.activeDraftId = activeDraftId;
    }

    public String getActiveVersionId() {
        return activeVersionId;
    }

    public void setActiveVersionId(String activeVersionId) {
        this.activeVersionId = activeVersionId;
    }

    public String getRuntimeBaseUrl() {
        return runtimeBaseUrl;
    }

    public void setRuntimeBaseUrl(String runtimeBaseUrl) {
        this.runtimeBaseUrl = runtimeBaseUrl;
    }

    public String getConnectorBaseUrl() {
        return connectorBaseUrl;
    }

    public void setConnectorBaseUrl(String connectorBaseUrl) {
        this.connectorBaseUrl = connectorBaseUrl;
    }

    public String getSourceRepositoryOverride() {
        return sourceRepositoryOverride;
    }

    public void setSourceRepositoryOverride(String sourceRepositoryOverride) {
        this.sourceRepositoryOverride = sourceRepositoryOverride;
    }

    public String getSourceBranchOverride() {
        return sourceBranchOverride;
    }

    public void setSourceBranchOverride(String sourceBranchOverride) {
        this.sourceBranchOverride = sourceBranchOverride;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public boolean isApprovalRequiredForApply() {
        return approvalRequiredForApply;
    }

    public void setApprovalRequiredForApply(boolean approvalRequiredForApply) {
        this.approvalRequiredForApply = approvalRequiredForApply;
    }

    public boolean isApprovalRequiredForDelete() {
        return approvalRequiredForDelete;
    }

    public void setApprovalRequiredForDelete(boolean approvalRequiredForDelete) {
        this.approvalRequiredForDelete = approvalRequiredForDelete;
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

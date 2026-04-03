package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_operation_approvals")
public class DeploymentOperationApprovalEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String operationType;

    private String targetVersionId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String requestedByActorId;

    private String requestedByDisplayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestedReason;

    private String approvedByActorId;

    private String approvedByDisplayName;

    @Column(columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant approvedAt;

    private Instant rejectedAt;

    private Instant expiresAt;

    private Instant consumedAt;

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

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getTargetVersionId() {
        return targetVersionId;
    }

    public void setTargetVersionId(String targetVersionId) {
        this.targetVersionId = targetVersionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestedByActorId() {
        return requestedByActorId;
    }

    public void setRequestedByActorId(String requestedByActorId) {
        this.requestedByActorId = requestedByActorId;
    }

    public String getRequestedByDisplayName() {
        return requestedByDisplayName;
    }

    public void setRequestedByDisplayName(String requestedByDisplayName) {
        this.requestedByDisplayName = requestedByDisplayName;
    }

    public String getRequestedReason() {
        return requestedReason;
    }

    public void setRequestedReason(String requestedReason) {
        this.requestedReason = requestedReason;
    }

    public String getApprovedByActorId() {
        return approvedByActorId;
    }

    public void setApprovedByActorId(String approvedByActorId) {
        this.approvedByActorId = approvedByActorId;
    }

    public String getApprovedByDisplayName() {
        return approvedByDisplayName;
    }

    public void setApprovedByDisplayName(String approvedByDisplayName) {
        this.approvedByDisplayName = approvedByDisplayName;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
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

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}

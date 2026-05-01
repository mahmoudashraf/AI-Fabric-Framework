package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resolver_executions")
public class ResolverExecutionEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String proposalId;
    @Column
    private String dryRunId;
    @Column(nullable = false, unique = true)
    private String idempotencyKey;
    @Column(nullable = false)
    private String actionFamily;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String productBoundary;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String executionResultJson = "{}";
    @Column(nullable = false)
    private String verificationStatus;
    @Column(columnDefinition = "TEXT")
    private String recoveryGuidance;
    @Column(nullable = false)
    private Instant createdAt;
    @Column
    private Instant completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }
    public String getDryRunId() { return dryRunId; }
    public void setDryRunId(String dryRunId) { this.dryRunId = dryRunId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getActionFamily() { return actionFamily; }
    public void setActionFamily(String actionFamily) { this.actionFamily = actionFamily; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProductBoundary() { return productBoundary; }
    public void setProductBoundary(String productBoundary) { this.productBoundary = productBoundary; }
    public String getExecutionResultJson() { return executionResultJson; }
    public void setExecutionResultJson(String executionResultJson) { this.executionResultJson = executionResultJson; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getRecoveryGuidance() { return recoveryGuidance; }
    public void setRecoveryGuidance(String recoveryGuidance) { this.recoveryGuidance = recoveryGuidance; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

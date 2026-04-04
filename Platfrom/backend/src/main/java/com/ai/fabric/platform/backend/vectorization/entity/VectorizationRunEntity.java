package com.ai.fabric.platform.backend.vectorization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_vectorization_runs")
public class VectorizationRunEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String planId;

    @Column(nullable = false)
    private String planRevisionId;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String requestedStatus;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String runnerMode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String entityScopeJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String progressSummaryJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String checkpointSummaryJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String errorSummaryJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String executionOverridesJson;

    private String claimedByRegistrationId;

    private String claimedBySessionId;

    private String runnerInstanceId;

    private String productVersion;

    private String compatibilityVersion;

    private Instant leaseExpiresAt;

    private String requestedByActorId;

    @Column(columnDefinition = "TEXT")
    private String requestNote;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanRevisionId() {
        return planRevisionId;
    }

    public void setPlanRevisionId(String planRevisionId) {
        this.planRevisionId = planRevisionId;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestedStatus() {
        return requestedStatus;
    }

    public void setRequestedStatus(String requestedStatus) {
        this.requestedStatus = requestedStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRunnerMode() {
        return runnerMode;
    }

    public void setRunnerMode(String runnerMode) {
        this.runnerMode = runnerMode;
    }

    public String getEntityScopeJson() {
        return entityScopeJson;
    }

    public void setEntityScopeJson(String entityScopeJson) {
        this.entityScopeJson = entityScopeJson;
    }

    public String getProgressSummaryJson() {
        return progressSummaryJson;
    }

    public void setProgressSummaryJson(String progressSummaryJson) {
        this.progressSummaryJson = progressSummaryJson;
    }

    public String getCheckpointSummaryJson() {
        return checkpointSummaryJson;
    }

    public void setCheckpointSummaryJson(String checkpointSummaryJson) {
        this.checkpointSummaryJson = checkpointSummaryJson;
    }

    public String getErrorSummaryJson() {
        return errorSummaryJson;
    }

    public void setErrorSummaryJson(String errorSummaryJson) {
        this.errorSummaryJson = errorSummaryJson;
    }

    public String getExecutionOverridesJson() {
        return executionOverridesJson;
    }

    public void setExecutionOverridesJson(String executionOverridesJson) {
        this.executionOverridesJson = executionOverridesJson;
    }

    public String getClaimedByRegistrationId() {
        return claimedByRegistrationId;
    }

    public void setClaimedByRegistrationId(String claimedByRegistrationId) {
        this.claimedByRegistrationId = claimedByRegistrationId;
    }

    public String getClaimedBySessionId() {
        return claimedBySessionId;
    }

    public void setClaimedBySessionId(String claimedBySessionId) {
        this.claimedBySessionId = claimedBySessionId;
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

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public String getRequestedByActorId() {
        return requestedByActorId;
    }

    public void setRequestedByActorId(String requestedByActorId) {
        this.requestedByActorId = requestedByActorId;
    }

    public String getRequestNote() {
        return requestNote;
    }

    public void setRequestNote(String requestNote) {
        this.requestNote = requestNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

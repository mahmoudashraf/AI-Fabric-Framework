package com.ai.fabric.platform.backend.vectorization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_vectorization_verification_runs")
public class VectorizationVerificationRunEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String verificationType;

    @Column(nullable = false)
    private String executionMode;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String entityScopeJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    private String linkedVectorizationRunId;

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

    public String getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(String verificationType) {
        this.verificationType = verificationType;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEntityScopeJson() {
        return entityScopeJson;
    }

    public void setEntityScopeJson(String entityScopeJson) {
        this.entityScopeJson = entityScopeJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getLinkedVectorizationRunId() {
        return linkedVectorizationRunId;
    }

    public void setLinkedVectorizationRunId(String linkedVectorizationRunId) {
        this.linkedVectorizationRunId = linkedVectorizationRunId;
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

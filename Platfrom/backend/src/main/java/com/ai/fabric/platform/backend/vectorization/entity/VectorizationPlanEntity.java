package com.ai.fabric.platform.backend.vectorization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_vectorization_plans")
public class VectorizationPlanEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String runnerMode;

    @Column(nullable = false)
    private String syncState;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String syncReasonCodesJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String syncReasonDetailsJson;

    private String sourceConnectionId;

    private String activeRevisionId;

    private String activeIndexedOutputHash;

    private String lastSuccessfulIndexedOutputHash;

    private String lastRunId;

    private String lastSuccessfulRunId;

    @Column(columnDefinition = "TEXT")
    private String manualConfirmationNote;

    private String manualConfirmationActorId;

    private String manualConfirmationHash;

    private Instant manuallyConfirmedAt;

    private Instant deferredReindexAt;

    @Column(columnDefinition = "TEXT")
    private String deferredReindexNote;

    private String deferredReindexHash;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getSyncState() {
        return syncState;
    }

    public void setSyncState(String syncState) {
        this.syncState = syncState;
    }

    public String getSyncReasonCodesJson() {
        return syncReasonCodesJson;
    }

    public void setSyncReasonCodesJson(String syncReasonCodesJson) {
        this.syncReasonCodesJson = syncReasonCodesJson;
    }

    public String getSyncReasonDetailsJson() {
        return syncReasonDetailsJson;
    }

    public void setSyncReasonDetailsJson(String syncReasonDetailsJson) {
        this.syncReasonDetailsJson = syncReasonDetailsJson;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public void setSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
    }

    public String getActiveRevisionId() {
        return activeRevisionId;
    }

    public void setActiveRevisionId(String activeRevisionId) {
        this.activeRevisionId = activeRevisionId;
    }

    public String getActiveIndexedOutputHash() {
        return activeIndexedOutputHash;
    }

    public void setActiveIndexedOutputHash(String activeIndexedOutputHash) {
        this.activeIndexedOutputHash = activeIndexedOutputHash;
    }

    public String getLastSuccessfulIndexedOutputHash() {
        return lastSuccessfulIndexedOutputHash;
    }

    public void setLastSuccessfulIndexedOutputHash(String lastSuccessfulIndexedOutputHash) {
        this.lastSuccessfulIndexedOutputHash = lastSuccessfulIndexedOutputHash;
    }

    public String getLastRunId() {
        return lastRunId;
    }

    public void setLastRunId(String lastRunId) {
        this.lastRunId = lastRunId;
    }

    public String getLastSuccessfulRunId() {
        return lastSuccessfulRunId;
    }

    public void setLastSuccessfulRunId(String lastSuccessfulRunId) {
        this.lastSuccessfulRunId = lastSuccessfulRunId;
    }

    public String getManualConfirmationNote() {
        return manualConfirmationNote;
    }

    public void setManualConfirmationNote(String manualConfirmationNote) {
        this.manualConfirmationNote = manualConfirmationNote;
    }

    public String getManualConfirmationActorId() {
        return manualConfirmationActorId;
    }

    public void setManualConfirmationActorId(String manualConfirmationActorId) {
        this.manualConfirmationActorId = manualConfirmationActorId;
    }

    public Instant getManuallyConfirmedAt() {
        return manuallyConfirmedAt;
    }

    public void setManuallyConfirmedAt(Instant manuallyConfirmedAt) {
        this.manuallyConfirmedAt = manuallyConfirmedAt;
    }

    public Instant getDeferredReindexAt() {
        return deferredReindexAt;
    }

    public void setDeferredReindexAt(Instant deferredReindexAt) {
        this.deferredReindexAt = deferredReindexAt;
    }

    public String getDeferredReindexNote() {
        return deferredReindexNote;
    }

    public void setDeferredReindexNote(String deferredReindexNote) {
        this.deferredReindexNote = deferredReindexNote;
    }

    public String getManualConfirmationHash() {
        return manualConfirmationHash;
    }

    public void setManualConfirmationHash(String manualConfirmationHash) {
        this.manualConfirmationHash = manualConfirmationHash;
    }

    public String getDeferredReindexHash() {
        return deferredReindexHash;
    }

    public void setDeferredReindexHash(String deferredReindexHash) {
        this.deferredReindexHash = deferredReindexHash;
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

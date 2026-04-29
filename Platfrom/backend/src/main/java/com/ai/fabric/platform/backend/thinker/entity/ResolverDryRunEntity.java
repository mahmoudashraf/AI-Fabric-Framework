package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resolver_dry_runs")
public class ResolverDryRunEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String proposalId;
    @Column(nullable = false)
    private String targetAction;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String validatedParametersJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String expectedStateTransition;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String expectedSideEffectsJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String warningsJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String unsupportedFieldsJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String idempotencyPosture;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String rollbackPosture;
    @Column(nullable = false)
    private String evidenceFreshness;
    @Column(nullable = false)
    private String productBoundary;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }
    public String getTargetAction() { return targetAction; }
    public void setTargetAction(String targetAction) { this.targetAction = targetAction; }
    public String getValidatedParametersJson() { return validatedParametersJson; }
    public void setValidatedParametersJson(String validatedParametersJson) { this.validatedParametersJson = validatedParametersJson; }
    public String getExpectedStateTransition() { return expectedStateTransition; }
    public void setExpectedStateTransition(String expectedStateTransition) { this.expectedStateTransition = expectedStateTransition; }
    public String getExpectedSideEffectsJson() { return expectedSideEffectsJson; }
    public void setExpectedSideEffectsJson(String expectedSideEffectsJson) { this.expectedSideEffectsJson = expectedSideEffectsJson; }
    public String getWarningsJson() { return warningsJson; }
    public void setWarningsJson(String warningsJson) { this.warningsJson = warningsJson; }
    public String getUnsupportedFieldsJson() { return unsupportedFieldsJson; }
    public void setUnsupportedFieldsJson(String unsupportedFieldsJson) { this.unsupportedFieldsJson = unsupportedFieldsJson; }
    public String getIdempotencyPosture() { return idempotencyPosture; }
    public void setIdempotencyPosture(String idempotencyPosture) { this.idempotencyPosture = idempotencyPosture; }
    public String getRollbackPosture() { return rollbackPosture; }
    public void setRollbackPosture(String rollbackPosture) { this.rollbackPosture = rollbackPosture; }
    public String getEvidenceFreshness() { return evidenceFreshness; }
    public void setEvidenceFreshness(String evidenceFreshness) { this.evidenceFreshness = evidenceFreshness; }
    public String getProductBoundary() { return productBoundary; }
    public void setProductBoundary(String productBoundary) { this.productBoundary = productBoundary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

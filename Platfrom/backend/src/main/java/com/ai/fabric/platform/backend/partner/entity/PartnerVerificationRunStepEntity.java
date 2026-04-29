package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_verification_run_steps")
public class PartnerVerificationRunStepEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String runId;
    @Column(nullable = false)
    private String stepId;
    @Column(nullable = false)
    private String stepLabel;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String partnerSafeMessage;
    @Column(columnDefinition = "TEXT")
    private String suggestedFix;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private Instant checkedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public String getStepLabel() { return stepLabel; }
    public void setStepLabel(String stepLabel) { this.stepLabel = stepLabel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPartnerSafeMessage() { return partnerSafeMessage; }
    public void setPartnerSafeMessage(String partnerSafeMessage) { this.partnerSafeMessage = partnerSafeMessage; }
    public String getSuggestedFix() { return suggestedFix; }
    public void setSuggestedFix(String suggestedFix) { this.suggestedFix = suggestedFix; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}

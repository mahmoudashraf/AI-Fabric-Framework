package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_support_escalations")
public class PartnerSupportEscalationEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String partnerAccountId;
    @Column
    private String storeAssignmentId;
    @Column(nullable = false)
    private String createdByMemberId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String severity;
    @Column(nullable = false)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String nextAction;
    @Column
    private Instant dueAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(columnDefinition = "TEXT")
    private String reproductionSteps;
    @Column(columnDefinition = "TEXT")
    private String expectedBehavior;
    @Column(columnDefinition = "TEXT")
    private String actualBehavior;
    @Column(columnDefinition = "TEXT")
    private String impact;
    @Column(columnDefinition = "TEXT")
    private String resolutionSummary;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getStoreAssignmentId() { return storeAssignmentId; }
    public void setStoreAssignmentId(String storeAssignmentId) { this.storeAssignmentId = storeAssignmentId; }
    public String getCreatedByMemberId() { return createdByMemberId; }
    public void setCreatedByMemberId(String createdByMemberId) { this.createdByMemberId = createdByMemberId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReproductionSteps() { return reproductionSteps; }
    public void setReproductionSteps(String reproductionSteps) { this.reproductionSteps = reproductionSteps; }
    public String getExpectedBehavior() { return expectedBehavior; }
    public void setExpectedBehavior(String expectedBehavior) { this.expectedBehavior = expectedBehavior; }
    public String getActualBehavior() { return actualBehavior; }
    public void setActualBehavior(String actualBehavior) { this.actualBehavior = actualBehavior; }
    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }
    public String getResolutionSummary() { return resolutionSummary; }
    public void setResolutionSummary(String resolutionSummary) { this.resolutionSummary = resolutionSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_template_applications")
public class PartnerTemplateApplicationEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String partnerAccountId;
    @Column
    private String storeAssignmentId;
    @Column(nullable = false)
    private String templateId;
    @Column(nullable = false)
    private String templateName;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String appliedByMemberId;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String checklistJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String assumptionsJson;
    @Column(nullable = false)
    private Instant appliedAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getStoreAssignmentId() { return storeAssignmentId; }
    public void setStoreAssignmentId(String storeAssignmentId) { this.storeAssignmentId = storeAssignmentId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAppliedByMemberId() { return appliedByMemberId; }
    public void setAppliedByMemberId(String appliedByMemberId) { this.appliedByMemberId = appliedByMemberId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChecklistJson() { return checklistJson; }
    public void setChecklistJson(String checklistJson) { this.checklistJson = checklistJson; }
    public String getAssumptionsJson() { return assumptionsJson; }
    public void setAssumptionsJson(String assumptionsJson) { this.assumptionsJson = assumptionsJson; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

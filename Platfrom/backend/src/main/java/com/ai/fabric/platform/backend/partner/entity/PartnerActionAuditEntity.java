package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_action_audits")
public class PartnerActionAuditEntity {
    @Id
    private String id;
    @Column
    private String partnerAccountId;
    @Column
    private String partnerMemberId;
    @Column(nullable = false)
    private String action;
    @Column
    private String targetType;
    @Column
    private String targetId;
    @Column(nullable = false)
    private String result;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String detailsJson;
    @Column(nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getPartnerMemberId() { return partnerMemberId; }
    public void setPartnerMemberId(String partnerMemberId) { this.partnerMemberId = partnerMemberId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

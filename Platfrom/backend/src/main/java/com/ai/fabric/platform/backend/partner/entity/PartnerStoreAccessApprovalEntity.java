package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_store_access_approvals")
public class PartnerStoreAccessApprovalEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String accessRequestId;
    @Column(nullable = false)
    private String partnerAccountId;
    @Column(nullable = false)
    private String shopDomain;
    @Column
    private String approverName;
    @Column
    private String approverEmail;
    @Column(nullable = false)
    private String approvedScope;
    @Column(nullable = false)
    private String sourceFlow;
    @Column(nullable = false)
    private Instant approvedAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String detailsJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAccessRequestId() { return accessRequestId; }
    public void setAccessRequestId(String accessRequestId) { this.accessRequestId = accessRequestId; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getShopDomain() { return shopDomain; }
    public void setShopDomain(String shopDomain) { this.shopDomain = shopDomain; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getApproverEmail() { return approverEmail; }
    public void setApproverEmail(String approverEmail) { this.approverEmail = approverEmail; }
    public String getApprovedScope() { return approvedScope; }
    public void setApprovedScope(String approvedScope) { this.approvedScope = approvedScope; }
    public String getSourceFlow() { return sourceFlow; }
    public void setSourceFlow(String sourceFlow) { this.sourceFlow = sourceFlow; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
}

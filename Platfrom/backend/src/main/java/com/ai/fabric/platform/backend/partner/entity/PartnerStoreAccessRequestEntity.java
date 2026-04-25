package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_store_access_requests")
public class PartnerStoreAccessRequestEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String partnerAccountId;
    @Column(nullable = false)
    private String implementationRequestId;
    @Column(nullable = false)
    private String requestedByMemberId;
    @Column(nullable = false)
    private String shopDomain;
    @Column(nullable = false)
    private String requestedScope;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false, unique = true)
    private String approvalCode;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String approvalUrl;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column
    private Instant approvedAt;
    @Column
    private Instant revokedAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getImplementationRequestId() { return implementationRequestId; }
    public void setImplementationRequestId(String implementationRequestId) { this.implementationRequestId = implementationRequestId; }
    public String getRequestedByMemberId() { return requestedByMemberId; }
    public void setRequestedByMemberId(String requestedByMemberId) { this.requestedByMemberId = requestedByMemberId; }
    public String getShopDomain() { return shopDomain; }
    public void setShopDomain(String shopDomain) { this.shopDomain = shopDomain; }
    public String getRequestedScope() { return requestedScope; }
    public void setRequestedScope(String requestedScope) { this.requestedScope = requestedScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalCode() { return approvalCode; }
    public void setApprovalCode(String approvalCode) { this.approvalCode = approvalCode; }
    public String getApprovalUrl() { return approvalUrl; }
    public void setApprovalUrl(String approvalUrl) { this.approvalUrl = approvalUrl; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

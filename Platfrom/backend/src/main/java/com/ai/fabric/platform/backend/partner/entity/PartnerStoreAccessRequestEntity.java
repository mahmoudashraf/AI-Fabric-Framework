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
    @Column
    private String storeConnectionId;
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
    @Column
    private String inviteRecipientEmail;
    @Column
    private String inviteStatus;
    @Column
    private String inviteChannel;
    @Column(columnDefinition = "TEXT")
    private String inviteMessage;
    @Column
    private Instant inviteSentAt;
    @Column(nullable = false)
    private int inviteCount;
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
    public String getStoreConnectionId() { return storeConnectionId; }
    public void setStoreConnectionId(String storeConnectionId) { this.storeConnectionId = storeConnectionId; }
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
    public String getInviteRecipientEmail() { return inviteRecipientEmail; }
    public void setInviteRecipientEmail(String inviteRecipientEmail) { this.inviteRecipientEmail = inviteRecipientEmail; }
    public String getInviteStatus() { return inviteStatus; }
    public void setInviteStatus(String inviteStatus) { this.inviteStatus = inviteStatus; }
    public String getInviteChannel() { return inviteChannel; }
    public void setInviteChannel(String inviteChannel) { this.inviteChannel = inviteChannel; }
    public String getInviteMessage() { return inviteMessage; }
    public void setInviteMessage(String inviteMessage) { this.inviteMessage = inviteMessage; }
    public Instant getInviteSentAt() { return inviteSentAt; }
    public void setInviteSentAt(Instant inviteSentAt) { this.inviteSentAt = inviteSentAt; }
    public int getInviteCount() { return inviteCount; }
    public void setInviteCount(int inviteCount) { this.inviteCount = inviteCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

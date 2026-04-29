package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_client_implementation_requests")
public class PartnerClientImplementationRequestEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String partnerAccountId;

    @Column(nullable = false)
    private String createdByMemberId;

    @Column(nullable = false)
    private String clientName;

    @Column
    private String contactEmail;

    @Column
    private String storeConnectionId;

    @Column(nullable = false)
    private String shopDomain;

    @Column
    private String vertical;

    @Column(nullable = false)
    private String requestedTier;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestedSurfacesJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String knownIntegrationsJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private String status;

    @Column(unique = true)
    private String approvalCode;

    @Column(columnDefinition = "TEXT")
    private String approvalUrl;

    @Column
    private Instant approvalExpiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getCreatedByMemberId() { return createdByMemberId; }
    public void setCreatedByMemberId(String createdByMemberId) { this.createdByMemberId = createdByMemberId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getStoreConnectionId() { return storeConnectionId; }
    public void setStoreConnectionId(String storeConnectionId) { this.storeConnectionId = storeConnectionId; }
    public String getShopDomain() { return shopDomain; }
    public void setShopDomain(String shopDomain) { this.shopDomain = shopDomain; }
    public String getVertical() { return vertical; }
    public void setVertical(String vertical) { this.vertical = vertical; }
    public String getRequestedTier() { return requestedTier; }
    public void setRequestedTier(String requestedTier) { this.requestedTier = requestedTier; }
    public String getRequestedSurfacesJson() { return requestedSurfacesJson; }
    public void setRequestedSurfacesJson(String requestedSurfacesJson) { this.requestedSurfacesJson = requestedSurfacesJson; }
    public String getKnownIntegrationsJson() { return knownIntegrationsJson; }
    public void setKnownIntegrationsJson(String knownIntegrationsJson) { this.knownIntegrationsJson = knownIntegrationsJson; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalCode() { return approvalCode; }
    public void setApprovalCode(String approvalCode) { this.approvalCode = approvalCode; }
    public String getApprovalUrl() { return approvalUrl; }
    public void setApprovalUrl(String approvalUrl) { this.approvalUrl = approvalUrl; }
    public Instant getApprovalExpiresAt() { return approvalExpiresAt; }
    public void setApprovalExpiresAt(Instant approvalExpiresAt) { this.approvalExpiresAt = approvalExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

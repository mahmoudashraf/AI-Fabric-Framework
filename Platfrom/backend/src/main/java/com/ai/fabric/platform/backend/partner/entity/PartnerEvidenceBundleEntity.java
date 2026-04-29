package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_evidence_bundles")
public class PartnerEvidenceBundleEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String partnerAccountId;
    @Column
    private String storeAssignmentId;
    @Column
    private String generatedByMemberId;
    @Column
    private String verificationRunId;
    @Column(nullable = false)
    private String bundleName;
    @Column(nullable = false)
    private String bundleKind;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String attachmentsJson;
    @Column(nullable = false)
    private Instant generatedAt;
    @Column
    private Instant expiresAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getStoreAssignmentId() { return storeAssignmentId; }
    public void setStoreAssignmentId(String storeAssignmentId) { this.storeAssignmentId = storeAssignmentId; }
    public String getGeneratedByMemberId() { return generatedByMemberId; }
    public void setGeneratedByMemberId(String generatedByMemberId) { this.generatedByMemberId = generatedByMemberId; }
    public String getVerificationRunId() { return verificationRunId; }
    public void setVerificationRunId(String verificationRunId) { this.verificationRunId = verificationRunId; }
    public String getBundleName() { return bundleName; }
    public void setBundleName(String bundleName) { this.bundleName = bundleName; }
    public String getBundleKind() { return bundleKind; }
    public void setBundleKind(String bundleKind) { this.bundleKind = bundleKind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public void setAttachmentsJson(String attachmentsJson) { this.attachmentsJson = attachmentsJson; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}

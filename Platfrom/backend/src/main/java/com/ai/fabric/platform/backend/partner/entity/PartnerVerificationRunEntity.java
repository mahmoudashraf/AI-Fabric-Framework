package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_verification_runs")
public class PartnerVerificationRunEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String partnerAccountId;
    @Column(nullable = false)
    private String storeAssignmentId;
    @Column(nullable = false)
    private String packId;
    @Column(nullable = false)
    private String packName;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryJson;
    @Column(nullable = false)
    private String startedByMemberId;
    @Column
    private String evidenceBundleId;
    @Column(nullable = false)
    private Instant startedAt;
    @Column
    private Instant completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getStoreAssignmentId() { return storeAssignmentId; }
    public void setStoreAssignmentId(String storeAssignmentId) { this.storeAssignmentId = storeAssignmentId; }
    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }
    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public String getStartedByMemberId() { return startedByMemberId; }
    public void setStartedByMemberId(String startedByMemberId) { this.startedByMemberId = startedByMemberId; }
    public String getEvidenceBundleId() { return evidenceBundleId; }
    public void setEvidenceBundleId(String evidenceBundleId) { this.evidenceBundleId = evidenceBundleId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_store_notes")
public class PartnerStoreNoteEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String partnerAccountId;
    @Column(nullable = false)
    private String storeAssignmentId;
    @Column(nullable = false)
    private String createdByMemberId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyMarkdown;
    @Column(nullable = false)
    private String status;
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
    public String getBodyMarkdown() { return bodyMarkdown; }
    public void setBodyMarkdown(String bodyMarkdown) { this.bodyMarkdown = bodyMarkdown; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

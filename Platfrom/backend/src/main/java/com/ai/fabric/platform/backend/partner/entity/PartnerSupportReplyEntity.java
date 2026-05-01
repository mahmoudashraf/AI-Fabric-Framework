package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_support_replies")
public class PartnerSupportReplyEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String escalationId;
    @Column
    private String authorMemberId;
    @Column(nullable = false)
    private String authorName;
    @Column(nullable = false)
    private String authorRole;
    @Column(nullable = false)
    private String visibility;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyMarkdown;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String attachmentsJson;
    @Column(nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEscalationId() { return escalationId; }
    public void setEscalationId(String escalationId) { this.escalationId = escalationId; }
    public String getAuthorMemberId() { return authorMemberId; }
    public void setAuthorMemberId(String authorMemberId) { this.authorMemberId = authorMemberId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getBodyMarkdown() { return bodyMarkdown; }
    public void setBodyMarkdown(String bodyMarkdown) { this.bodyMarkdown = bodyMarkdown; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public void setAttachmentsJson(String attachmentsJson) { this.attachmentsJson = attachmentsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

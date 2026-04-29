package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "thinker_issue_sessions")
public class ThinkerIssueSessionEntity {
    @Id
    private String id;
    @Column
    private String deploymentId;
    @Column
    private String tenantId;
    @Column
    private String customerId;
    @Column
    private String storeId;
    @Column
    private String shopDomain;
    @Column
    private String consumerId;
    @Column
    private String userSubject;
    @Column
    private String shopperSessionId;
    @Column
    private String locale;
    @Column(nullable = false)
    private String channel;
    @Column(nullable = false)
    private String mode;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String issueCategory;
    @Column(nullable = false)
    private String riskClass;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String expectedEvidenceJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String suggestedActionAllowlistJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String userQuestion;
    @Column(columnDefinition = "TEXT")
    private String userSafeAnswer;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceCitationsJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String plannerTraceJson = "{}";
    @Column
    private String runtimeConversationId;
    @Column
    private String runtimeSessionId;
    @Column(columnDefinition = "TEXT")
    private String terminalReason;
    @Column(nullable = false)
    private Instant startedAt;
    @Column
    private Instant completedAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }
    public String getShopDomain() { return shopDomain; }
    public void setShopDomain(String shopDomain) { this.shopDomain = shopDomain; }
    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }
    public String getUserSubject() { return userSubject; }
    public void setUserSubject(String userSubject) { this.userSubject = userSubject; }
    public String getShopperSessionId() { return shopperSessionId; }
    public void setShopperSessionId(String shopperSessionId) { this.shopperSessionId = shopperSessionId; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIssueCategory() { return issueCategory; }
    public void setIssueCategory(String issueCategory) { this.issueCategory = issueCategory; }
    public String getRiskClass() { return riskClass; }
    public void setRiskClass(String riskClass) { this.riskClass = riskClass; }
    public String getExpectedEvidenceJson() { return expectedEvidenceJson; }
    public void setExpectedEvidenceJson(String expectedEvidenceJson) { this.expectedEvidenceJson = expectedEvidenceJson; }
    public String getSuggestedActionAllowlistJson() { return suggestedActionAllowlistJson; }
    public void setSuggestedActionAllowlistJson(String suggestedActionAllowlistJson) { this.suggestedActionAllowlistJson = suggestedActionAllowlistJson; }
    public String getUserQuestion() { return userQuestion; }
    public void setUserQuestion(String userQuestion) { this.userQuestion = userQuestion; }
    public String getUserSafeAnswer() { return userSafeAnswer; }
    public void setUserSafeAnswer(String userSafeAnswer) { this.userSafeAnswer = userSafeAnswer; }
    public String getSourceCitationsJson() { return sourceCitationsJson; }
    public void setSourceCitationsJson(String sourceCitationsJson) { this.sourceCitationsJson = sourceCitationsJson; }
    public String getPlannerTraceJson() { return plannerTraceJson; }
    public void setPlannerTraceJson(String plannerTraceJson) { this.plannerTraceJson = plannerTraceJson; }
    public String getRuntimeConversationId() { return runtimeConversationId; }
    public void setRuntimeConversationId(String runtimeConversationId) { this.runtimeConversationId = runtimeConversationId; }
    public String getRuntimeSessionId() { return runtimeSessionId; }
    public void setRuntimeSessionId(String runtimeSessionId) { this.runtimeSessionId = runtimeSessionId; }
    public String getTerminalReason() { return terminalReason; }
    public void setTerminalReason(String terminalReason) { this.terminalReason = terminalReason; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

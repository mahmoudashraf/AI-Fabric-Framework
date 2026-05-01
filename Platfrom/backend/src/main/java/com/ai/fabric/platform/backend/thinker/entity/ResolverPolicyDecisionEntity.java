package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resolver_policy_decisions")
public class ResolverPolicyDecisionEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String proposalId;
    @Column(nullable = false)
    private String outcome;
    @Column(nullable = false)
    private String riskLevel;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String requiredScopesJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String missingScopesJson = "[]";
    @Column(nullable = false)
    private String tierRequirement;
    @Column(nullable = false)
    private String actorRequirement;
    @Column(nullable = false)
    private boolean dryRunRequired;
    @Column(nullable = false)
    private boolean confirmationRequired;
    @Column(nullable = false)
    private boolean approvalRequired;
    @Column(columnDefinition = "TEXT")
    private String confirmationPreview;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String operatorReason;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String userReason;
    @Column(nullable = false)
    private Instant decidedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getRequiredScopesJson() { return requiredScopesJson; }
    public void setRequiredScopesJson(String requiredScopesJson) { this.requiredScopesJson = requiredScopesJson; }
    public String getMissingScopesJson() { return missingScopesJson; }
    public void setMissingScopesJson(String missingScopesJson) { this.missingScopesJson = missingScopesJson; }
    public String getTierRequirement() { return tierRequirement; }
    public void setTierRequirement(String tierRequirement) { this.tierRequirement = tierRequirement; }
    public String getActorRequirement() { return actorRequirement; }
    public void setActorRequirement(String actorRequirement) { this.actorRequirement = actorRequirement; }
    public boolean isDryRunRequired() { return dryRunRequired; }
    public void setDryRunRequired(boolean dryRunRequired) { this.dryRunRequired = dryRunRequired; }
    public boolean isConfirmationRequired() { return confirmationRequired; }
    public void setConfirmationRequired(boolean confirmationRequired) { this.confirmationRequired = confirmationRequired; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; }
    public String getConfirmationPreview() { return confirmationPreview; }
    public void setConfirmationPreview(String confirmationPreview) { this.confirmationPreview = confirmationPreview; }
    public String getOperatorReason() { return operatorReason; }
    public void setOperatorReason(String operatorReason) { this.operatorReason = operatorReason; }
    public String getUserReason() { return userReason; }
    public void setUserReason(String userReason) { this.userReason = userReason; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}

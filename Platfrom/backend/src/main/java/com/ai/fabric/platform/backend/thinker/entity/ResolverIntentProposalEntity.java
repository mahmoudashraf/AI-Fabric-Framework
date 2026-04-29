package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resolver_intent_proposals")
public class ResolverIntentProposalEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String sessionId;
    @Column(nullable = false)
    private String actionId;
    @Column(nullable = false)
    private String actionFamily;
    @Column(nullable = false)
    private String targetDomain;
    @Column(nullable = false)
    private String productBoundary;
    @Column(nullable = false)
    private String actorContext;
    @Column
    private String tenantId;
    @Column
    private String storeId;
    @Column
    private String deploymentId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String redactedParametersJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String operatorParametersJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceEvidenceItemIdsJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String proposalText;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String normalizedIntentJson = "{}";
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getActionFamily() { return actionFamily; }
    public void setActionFamily(String actionFamily) { this.actionFamily = actionFamily; }
    public String getTargetDomain() { return targetDomain; }
    public void setTargetDomain(String targetDomain) { this.targetDomain = targetDomain; }
    public String getProductBoundary() { return productBoundary; }
    public void setProductBoundary(String productBoundary) { this.productBoundary = productBoundary; }
    public String getActorContext() { return actorContext; }
    public void setActorContext(String actorContext) { this.actorContext = actorContext; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getRedactedParametersJson() { return redactedParametersJson; }
    public void setRedactedParametersJson(String redactedParametersJson) { this.redactedParametersJson = redactedParametersJson; }
    public String getOperatorParametersJson() { return operatorParametersJson; }
    public void setOperatorParametersJson(String operatorParametersJson) { this.operatorParametersJson = operatorParametersJson; }
    public String getSourceEvidenceItemIdsJson() { return sourceEvidenceItemIdsJson; }
    public void setSourceEvidenceItemIdsJson(String sourceEvidenceItemIdsJson) { this.sourceEvidenceItemIdsJson = sourceEvidenceItemIdsJson; }
    public String getProposalText() { return proposalText; }
    public void setProposalText(String proposalText) { this.proposalText = proposalText; }
    public String getNormalizedIntentJson() { return normalizedIntentJson; }
    public void setNormalizedIntentJson(String normalizedIntentJson) { this.normalizedIntentJson = normalizedIntentJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

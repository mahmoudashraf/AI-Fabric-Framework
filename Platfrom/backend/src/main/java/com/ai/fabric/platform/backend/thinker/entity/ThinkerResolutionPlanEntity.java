package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "thinker_resolution_plans")
public class ThinkerResolutionPlanEntity {
    @Id
    private String id;
    @Column(nullable = false, unique = true)
    private String sessionId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String issueSummary;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String diagnosis;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionsConsideredJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendedNextStep;
    @Column(nullable = false)
    private String recommendation;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String userExplanation;
    @Column
    private String escalationTarget;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidenceItemIdsJson = "[]";
    @Column(nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getIssueSummary() { return issueSummary; }
    public void setIssueSummary(String issueSummary) { this.issueSummary = issueSummary; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getOptionsConsideredJson() { return optionsConsideredJson; }
    public void setOptionsConsideredJson(String optionsConsideredJson) { this.optionsConsideredJson = optionsConsideredJson; }
    public String getRecommendedNextStep() { return recommendedNextStep; }
    public void setRecommendedNextStep(String recommendedNextStep) { this.recommendedNextStep = recommendedNextStep; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getUserExplanation() { return userExplanation; }
    public void setUserExplanation(String userExplanation) { this.userExplanation = userExplanation; }
    public String getEscalationTarget() { return escalationTarget; }
    public void setEscalationTarget(String escalationTarget) { this.escalationTarget = escalationTarget; }
    public String getEvidenceItemIdsJson() { return evidenceItemIdsJson; }
    public void setEvidenceItemIdsJson(String evidenceItemIdsJson) { this.evidenceItemIdsJson = evidenceItemIdsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "thinker_evidence_items")
public class ThinkerEvidenceItemEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String sessionId;
    @Column(nullable = false)
    private String kind;
    @Column(nullable = false)
    private String sourceKind;
    @Column(nullable = false)
    private String sourceIdentifier;
    @Column(nullable = false)
    private String freshness;
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;
    @Column(nullable = false)
    private String redactionState;
    @Column(columnDefinition = "TEXT")
    private String operatorRawReference;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String safeSummary;
    @Column(nullable = false)
    private Instant capturedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSourceKind() { return sourceKind; }
    public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }
    public String getSourceIdentifier() { return sourceIdentifier; }
    public void setSourceIdentifier(String sourceIdentifier) { this.sourceIdentifier = sourceIdentifier; }
    public String getFreshness() { return freshness; }
    public void setFreshness(String freshness) { this.freshness = freshness; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getRedactionState() { return redactionState; }
    public void setRedactionState(String redactionState) { this.redactionState = redactionState; }
    public String getOperatorRawReference() { return operatorRawReference; }
    public void setOperatorRawReference(String operatorRawReference) { this.operatorRawReference = operatorRawReference; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSafeSummary() { return safeSummary; }
    public void setSafeSummary(String safeSummary) { this.safeSummary = safeSummary; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
}

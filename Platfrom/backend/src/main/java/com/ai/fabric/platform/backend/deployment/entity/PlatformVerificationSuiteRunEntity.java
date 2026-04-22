package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_verification_suite_runs")
public class PlatformVerificationSuiteRunEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String suiteKey;

    @Column(nullable = false)
    private String suiteLabel;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean releaseBlocking;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryMessage;

    @Column(nullable = false)
    private String requestedByActorId;

    @Column(nullable = false)
    private String requestedByRole;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSuiteKey() {
        return suiteKey;
    }

    public void setSuiteKey(String suiteKey) {
        this.suiteKey = suiteKey;
    }

    public String getSuiteLabel() {
        return suiteLabel;
    }

    public void setSuiteLabel(String suiteLabel) {
        this.suiteLabel = suiteLabel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isReleaseBlocking() {
        return releaseBlocking;
    }

    public void setReleaseBlocking(boolean releaseBlocking) {
        this.releaseBlocking = releaseBlocking;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    public String getRequestedByActorId() {
        return requestedByActorId;
    }

    public void setRequestedByActorId(String requestedByActorId) {
        this.requestedByActorId = requestedByActorId;
    }

    public String getRequestedByRole() {
        return requestedByRole;
    }

    public void setRequestedByRole(String requestedByRole) {
        this.requestedByRole = requestedByRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

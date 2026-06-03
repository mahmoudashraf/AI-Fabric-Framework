package com.ai.fabric.platform.backend.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_consumers")
public class PlatformConsumerEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, length = 128, unique = true)
    private String consumerId;

    @Column(nullable = false, length = 255)
    private String displayName;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(length = 64)
    private String boundDeploymentId;

    @Column(length = 64)
    private String boundReleaseId;

    @Column(length = 64)
    private String boundTargetProfileId;

    private Instant lastBoundAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBoundDeploymentId() {
        return boundDeploymentId;
    }

    public void setBoundDeploymentId(String boundDeploymentId) {
        this.boundDeploymentId = boundDeploymentId;
    }

    public String getBoundReleaseId() {
        return boundReleaseId;
    }

    public void setBoundReleaseId(String boundReleaseId) {
        this.boundReleaseId = boundReleaseId;
    }

    public String getBoundTargetProfileId() {
        return boundTargetProfileId;
    }

    public void setBoundTargetProfileId(String boundTargetProfileId) {
        this.boundTargetProfileId = boundTargetProfileId;
    }

    public Instant getLastBoundAt() {
        return lastBoundAt;
    }

    public void setLastBoundAt(Instant lastBoundAt) {
        this.lastBoundAt = lastBoundAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

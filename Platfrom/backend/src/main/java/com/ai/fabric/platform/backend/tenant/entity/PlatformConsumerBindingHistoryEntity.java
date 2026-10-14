package com.ai.fabric.platform.backend.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_consumer_binding_history")
public class PlatformConsumerBindingHistoryEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String consumerEntityId;

    @Column(nullable = false, length = 128)
    private String consumerId;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Column(length = 64)
    private String fromDeploymentId;

    @Column(length = 64)
    private String toDeploymentId;

    @Column(length = 64)
    private String fromReleaseId;
    @Column(length = 64)
    private String toReleaseId;
    @Column(length = 64)
    private String fromTargetProfileId;
    @Column(length = 64)
    private String toTargetProfileId;
    private Long fromBindingRevision;
    private Long toBindingRevision;
    @Column(length = 128)
    private String operationKey;
    @Column(length = 64)
    private String requestFingerprint;
    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false, length = 255)
    private String actorId;

    @Column(nullable = false, length = 64)
    private String actorRole;

    @Column(nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConsumerEntityId() {
        return consumerEntityId;
    }

    public void setConsumerEntityId(String consumerEntityId) {
        this.consumerEntityId = consumerEntityId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getFromDeploymentId() {
        return fromDeploymentId;
    }

    public void setFromDeploymentId(String fromDeploymentId) {
        this.fromDeploymentId = fromDeploymentId;
    }

    public String getToDeploymentId() {
        return toDeploymentId;
    }

    public void setToDeploymentId(String toDeploymentId) {
        this.toDeploymentId = toDeploymentId;
    }

    public String getReason() {
        return reason;
    }

    public String getFromReleaseId() { return fromReleaseId; }
    public void setFromReleaseId(String value) { fromReleaseId = value; }
    public String getToReleaseId() { return toReleaseId; }
    public void setToReleaseId(String value) { toReleaseId = value; }
    public String getFromTargetProfileId() { return fromTargetProfileId; }
    public void setFromTargetProfileId(String value) { fromTargetProfileId = value; }
    public String getToTargetProfileId() { return toTargetProfileId; }
    public void setToTargetProfileId(String value) { toTargetProfileId = value; }
    public Long getFromBindingRevision() { return fromBindingRevision; }
    public void setFromBindingRevision(Long value) { fromBindingRevision = value; }
    public Long getToBindingRevision() { return toBindingRevision; }
    public void setToBindingRevision(Long value) { toBindingRevision = value; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String value) { operationKey = value; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { requestFingerprint = value; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { resultJson = value; }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

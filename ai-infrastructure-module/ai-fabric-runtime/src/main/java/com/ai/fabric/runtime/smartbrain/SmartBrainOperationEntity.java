package com.ai.fabric.runtime.smartbrain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "loomai_smart_brain_operation")
public class SmartBrainOperationEntity {

    @Id
    @Column(name = "operation_id", nullable = false, length = 120)
    private String operationId;

    @Column(name = "tenant_id", nullable = false, length = 200)
    private String tenantId;

    @Column(name = "deployment_id", nullable = false, length = 200)
    private String deploymentId;

    @Column(name = "trigger_code", nullable = false, length = 80)
    private String triggerCode;

    @Column(name = "cloud_event_id", nullable = false, length = 200)
    private String cloudEventId;

    @Column(name = "cloud_event_type", nullable = false, length = 200)
    private String cloudEventType;

    @Column(name = "cloud_event_source", nullable = false, length = 500)
    private String cloudEventSource;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "protected_request", nullable = false, columnDefinition = "TEXT")
    private String protectedRequest;

    @Column(name = "invocation_id", length = 120)
    private String invocationId;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "protected_result", columnDefinition = "TEXT")
    private String protectedResult;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "accepted_runtime_url", length = 1000)
    private String acceptedRuntimeUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getTriggerCode() { return triggerCode; }
    public void setTriggerCode(String triggerCode) { this.triggerCode = triggerCode; }
    public String getCloudEventId() { return cloudEventId; }
    public void setCloudEventId(String cloudEventId) { this.cloudEventId = cloudEventId; }
    public String getCloudEventType() { return cloudEventType; }
    public void setCloudEventType(String cloudEventType) { this.cloudEventType = cloudEventType; }
    public String getCloudEventSource() { return cloudEventSource; }
    public void setCloudEventSource(String cloudEventSource) { this.cloudEventSource = cloudEventSource; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getProtectedRequest() { return protectedRequest; }
    public void setProtectedRequest(String protectedRequest) { this.protectedRequest = protectedRequest; }
    public String getInvocationId() { return invocationId; }
    public void setInvocationId(String invocationId) { this.invocationId = invocationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProtectedResult() { return protectedResult; }
    public void setProtectedResult(String protectedResult) { this.protectedResult = protectedResult; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public String getAcceptedRuntimeUrl() { return acceptedRuntimeUrl; }
    public void setAcceptedRuntimeUrl(String acceptedRuntimeUrl) { this.acceptedRuntimeUrl = acceptedRuntimeUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public long getVersion() { return version; }
}

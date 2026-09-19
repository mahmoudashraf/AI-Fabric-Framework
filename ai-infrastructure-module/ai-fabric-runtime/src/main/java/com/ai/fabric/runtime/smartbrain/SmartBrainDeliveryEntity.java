package com.ai.fabric.runtime.smartbrain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "loomai_smart_brain_delivery")
public class SmartBrainDeliveryEntity {

    @Id
    @Column(name = "delivery_id", nullable = false, length = 120)
    private String deliveryId;
    @Column(name = "operation_id", nullable = false, length = 120)
    private String operationId;
    @Column(name = "tenant_id", nullable = false, length = 200)
    private String tenantId;
    @Column(name = "deployment_id", nullable = false, length = 200)
    private String deploymentId;
    @Column(name = "callback_url", nullable = false, length = 1000)
    private String callbackUrl;
    @Column(name = "status", nullable = false, length = 40)
    private String status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_http_status")
    private Integer lastHttpStatus;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public void setLastHttpStatus(Integer lastHttpStatus) { this.lastHttpStatus = lastHttpStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public long getVersion() { return version; }
}

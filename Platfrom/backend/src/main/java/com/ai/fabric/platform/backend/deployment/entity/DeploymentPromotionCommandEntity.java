package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_promotion_commands")
public class DeploymentPromotionCommandEntity {
    @Id private String id;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false) private String consumerEntityId;
    @Column(nullable = false, length = 128) private String operationKey;
    @Column(nullable = false, length = 32) private String commandType;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Column(nullable = false) private Instant createdAt;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String value) { customerId = value; }
    public String getConsumerEntityId() { return consumerEntityId; }
    public void setConsumerEntityId(String value) { consumerEntityId = value; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String value) { operationKey = value; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String value) { commandType = value; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { requestFingerprint = value; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { resultJson = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}

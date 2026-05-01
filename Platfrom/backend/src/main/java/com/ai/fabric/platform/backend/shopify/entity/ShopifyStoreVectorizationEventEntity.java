package com.ai.fabric.platform.backend.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_store_vectorization_events")
public class ShopifyStoreVectorizationEventEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String eventSource;

    @Column(nullable = false)
    private String schemaVersion;

    @Column(nullable = false)
    private String shopDomain;

    @Column
    private String deploymentId;

    @Column(nullable = false)
    private String sourceCategory;

    @Column(nullable = false)
    private String entityType;

    @Column
    private String sourceObjectId;

    @Column(nullable = false)
    private String operation;

    @Column
    private String triggerReason;

    @Column(columnDefinition = "TEXT")
    private String changedFieldsJson;

    @Column
    private String sourceRecordVersion;

    @Column(nullable = false)
    private String aggregateKey;

    @Column(nullable = false, unique = true)
    private String dedupeKey;

    @Column(nullable = false)
    private String correlationId;

    @Column
    private String causationId;

    @Column
    private String shopifyWebhookId;

    @Column
    private String shopifyTopic;

    @Column
    private Integer deliveryAttempt;

    @Column
    private String payloadChecksum;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant availableAt;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(columnDefinition = "TEXT")
    private String headersJson;

    @Column(nullable = false)
    private Instant queuedAt;

    @Column(nullable = false)
    private String status;

    @Column
    private String coalescedRunId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private int attemptCount;

    @Column
    private Instant lastAttemptAt;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column
    private String leaseOwner;

    @Column
    private Instant leaseExpiresAt;

    @Column
    private String failureCode;

    @Column(columnDefinition = "TEXT")
    private String lastErrorSummary;

    @Column
    private Instant deadLetteredAt;

    @Column(nullable = false)
    private Instant retentionExpiresAt;

    @Column
    private Instant completedAt;

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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getShopDomain() {
        return shopDomain;
    }

    public void setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getSourceObjectId() {
        return sourceObjectId;
    }

    public void setSourceObjectId(String sourceObjectId) {
        this.sourceObjectId = sourceObjectId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public String getChangedFieldsJson() {
        return changedFieldsJson;
    }

    public void setChangedFieldsJson(String changedFieldsJson) {
        this.changedFieldsJson = changedFieldsJson;
    }

    public String getSourceRecordVersion() {
        return sourceRecordVersion;
    }

    public void setSourceRecordVersion(String sourceRecordVersion) {
        this.sourceRecordVersion = sourceRecordVersion;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public void setAggregateKey(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    public String getShopifyWebhookId() {
        return shopifyWebhookId;
    }

    public void setShopifyWebhookId(String shopifyWebhookId) {
        this.shopifyWebhookId = shopifyWebhookId;
    }

    public String getShopifyTopic() {
        return shopifyTopic;
    }

    public void setShopifyTopic(String shopifyTopic) {
        this.shopifyTopic = shopifyTopic;
    }

    public Integer getDeliveryAttempt() {
        return deliveryAttempt;
    }

    public void setDeliveryAttempt(Integer deliveryAttempt) {
        this.deliveryAttempt = deliveryAttempt;
    }

    public String getPayloadChecksum() {
        return payloadChecksum;
    }

    public void setPayloadChecksum(String payloadChecksum) {
        this.payloadChecksum = payloadChecksum;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(Instant availableAt) {
        this.availableAt = availableAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(Instant queuedAt) {
        this.queuedAt = queuedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCoalescedRunId() {
        return coalescedRunId;
    }

    public void setCoalescedRunId(String coalescedRunId) {
        this.coalescedRunId = coalescedRunId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getLastErrorSummary() {
        return lastErrorSummary;
    }

    public void setLastErrorSummary(String lastErrorSummary) {
        this.lastErrorSummary = lastErrorSummary;
    }

    public Instant getDeadLetteredAt() {
        return deadLetteredAt;
    }

    public void setDeadLetteredAt(Instant deadLetteredAt) {
        this.deadLetteredAt = deadLetteredAt;
    }

    public Instant getRetentionExpiresAt() {
        return retentionExpiresAt;
    }

    public void setRetentionExpiresAt(Instant retentionExpiresAt) {
        this.retentionExpiresAt = retentionExpiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
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

package com.ai.fabric.platform.backend.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_store_provisioning_jobs")
public class ShopifyStoreProvisioningJobEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String shopDomain;

    @Column
    private String storeConnectionId;

    @Column(nullable = false)
    private String jobType;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String phase;

    @Column
    private String requestedPackageKey;

    @Column
    private String requestedTierKey;

    @Column
    private String requestedRuntimeProfileKey;

    @Column
    private String requestedVectorProfileKey;

    @Column
    private String previousPackageKey;

    @Column
    private String previousRuntimeProfileKey;

    @Column
    private String previousVectorProfileKey;

    @Column
    private String requestedTemplatePluginId;

    @Column
    private String requestedTemplatePluginVersion;

    @Column(columnDefinition = "TEXT")
    private String requestedPluginIdsJson;

    @Column
    private String profileChangeStrategy;

    @Column(nullable = false)
    private boolean vectorReindexRequired;

    @Column
    private String installIntentId;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private int maxAttempts;

    @Column
    private String leaseOwner;

    @Column
    private Instant leaseExpiresAt;

    @Column
    private String lastErrorCode;

    @Column(columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column
    private String bootstrapDeploymentId;

    @Column
    private String verificationRunId;

    @Column(columnDefinition = "TEXT")
    private String nextAction;

    @Column(columnDefinition = "TEXT")
    private String summaryMessage;

    @Column
    private Instant readyAt;

    @Column
    private Instant failedAt;

    @Column
    private Instant cancelledAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getShopDomain() { return shopDomain; }
    public void setShopDomain(String shopDomain) { this.shopDomain = shopDomain; }
    public String getStoreConnectionId() { return storeConnectionId; }
    public void setStoreConnectionId(String storeConnectionId) { this.storeConnectionId = storeConnectionId; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getRequestedPackageKey() { return requestedPackageKey; }
    public void setRequestedPackageKey(String requestedPackageKey) { this.requestedPackageKey = requestedPackageKey; }
    public String getRequestedTierKey() { return requestedTierKey; }
    public void setRequestedTierKey(String requestedTierKey) { this.requestedTierKey = requestedTierKey; }
    public String getRequestedRuntimeProfileKey() { return requestedRuntimeProfileKey; }
    public void setRequestedRuntimeProfileKey(String requestedRuntimeProfileKey) { this.requestedRuntimeProfileKey = requestedRuntimeProfileKey; }
    public String getRequestedVectorProfileKey() { return requestedVectorProfileKey; }
    public void setRequestedVectorProfileKey(String requestedVectorProfileKey) { this.requestedVectorProfileKey = requestedVectorProfileKey; }
    public String getPreviousPackageKey() { return previousPackageKey; }
    public void setPreviousPackageKey(String previousPackageKey) { this.previousPackageKey = previousPackageKey; }
    public String getPreviousRuntimeProfileKey() { return previousRuntimeProfileKey; }
    public void setPreviousRuntimeProfileKey(String previousRuntimeProfileKey) { this.previousRuntimeProfileKey = previousRuntimeProfileKey; }
    public String getPreviousVectorProfileKey() { return previousVectorProfileKey; }
    public void setPreviousVectorProfileKey(String previousVectorProfileKey) { this.previousVectorProfileKey = previousVectorProfileKey; }
    public String getRequestedTemplatePluginId() { return requestedTemplatePluginId; }
    public void setRequestedTemplatePluginId(String requestedTemplatePluginId) { this.requestedTemplatePluginId = requestedTemplatePluginId; }
    public String getRequestedTemplatePluginVersion() { return requestedTemplatePluginVersion; }
    public void setRequestedTemplatePluginVersion(String requestedTemplatePluginVersion) { this.requestedTemplatePluginVersion = requestedTemplatePluginVersion; }
    public String getRequestedPluginIdsJson() { return requestedPluginIdsJson; }
    public void setRequestedPluginIdsJson(String requestedPluginIdsJson) { this.requestedPluginIdsJson = requestedPluginIdsJson; }
    public String getProfileChangeStrategy() { return profileChangeStrategy; }
    public void setProfileChangeStrategy(String profileChangeStrategy) { this.profileChangeStrategy = profileChangeStrategy; }
    public boolean isVectorReindexRequired() { return vectorReindexRequired; }
    public void setVectorReindexRequired(boolean vectorReindexRequired) { this.vectorReindexRequired = vectorReindexRequired; }
    public String getInstallIntentId() { return installIntentId; }
    public void setInstallIntentId(String installIntentId) { this.installIntentId = installIntentId; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public String getBootstrapDeploymentId() { return bootstrapDeploymentId; }
    public void setBootstrapDeploymentId(String bootstrapDeploymentId) { this.bootstrapDeploymentId = bootstrapDeploymentId; }
    public String getVerificationRunId() { return verificationRunId; }
    public void setVerificationRunId(String verificationRunId) { this.verificationRunId = verificationRunId; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public String getSummaryMessage() { return summaryMessage; }
    public void setSummaryMessage(String summaryMessage) { this.summaryMessage = summaryMessage; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }
    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

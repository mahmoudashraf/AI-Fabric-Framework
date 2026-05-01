package com.ai.fabric.platform.backend.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_package_trial_activations")
public class PartnerPackageTrialActivationEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String partnerAccountId;

    @Column(nullable = false)
    private String partnerMemberId;

    @Column(nullable = false)
    private String storeAssignmentId;

    @Column
    private String storeConnectionId;

    @Column(nullable = false)
    private String shopDomain;

    @Column(nullable = false)
    private String packageKey;

    @Column(nullable = false)
    private String tierKey;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Integer trialDays;

    @Column(nullable = false)
    private Instant trialStartsAt;

    @Column(nullable = false)
    private Instant trialEndsAt;

    @Column(nullable = false)
    private Instant activatedAt;

    @Column
    private Instant deactivatedAt;

    @Column
    private String deactivatedByMemberId;

    @Column(columnDefinition = "TEXT")
    private String activationReason;

    @Column(columnDefinition = "TEXT")
    private String deactivationReason;

    @Column
    private String previousTierKey;

    @Column
    private String previousBillingStatus;

    @Column
    private String previousSubscriptionId;

    @Column
    private String previousSubscriptionName;

    @Column
    private String activationProvisioningJobId;

    @Column
    private String deactivationProvisioningJobId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detailsJson = "{}";

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartnerAccountId() { return partnerAccountId; }
    public void setPartnerAccountId(String partnerAccountId) { this.partnerAccountId = partnerAccountId; }
    public String getPartnerMemberId() { return partnerMemberId; }
    public void setPartnerMemberId(String partnerMemberId) { this.partnerMemberId = partnerMemberId; }
    public String getStoreAssignmentId() { return storeAssignmentId; }
    public void setStoreAssignmentId(String storeAssignmentId) { this.storeAssignmentId = storeAssignmentId; }
    public String getStoreConnectionId() { return storeConnectionId; }
    public void setStoreConnectionId(String storeConnectionId) { this.storeConnectionId = storeConnectionId; }
    public String getShopDomain() { return shopDomain; }
    public void setShopDomain(String shopDomain) { this.shopDomain = shopDomain; }
    public String getPackageKey() { return packageKey; }
    public void setPackageKey(String packageKey) { this.packageKey = packageKey; }
    public String getTierKey() { return tierKey; }
    public void setTierKey(String tierKey) { this.tierKey = tierKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTrialDays() { return trialDays; }
    public void setTrialDays(Integer trialDays) { this.trialDays = trialDays; }
    public Instant getTrialStartsAt() { return trialStartsAt; }
    public void setTrialStartsAt(Instant trialStartsAt) { this.trialStartsAt = trialStartsAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public void setTrialEndsAt(Instant trialEndsAt) { this.trialEndsAt = trialEndsAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(Instant deactivatedAt) { this.deactivatedAt = deactivatedAt; }
    public String getDeactivatedByMemberId() { return deactivatedByMemberId; }
    public void setDeactivatedByMemberId(String deactivatedByMemberId) { this.deactivatedByMemberId = deactivatedByMemberId; }
    public String getActivationReason() { return activationReason; }
    public void setActivationReason(String activationReason) { this.activationReason = activationReason; }
    public String getDeactivationReason() { return deactivationReason; }
    public void setDeactivationReason(String deactivationReason) { this.deactivationReason = deactivationReason; }
    public String getPreviousTierKey() { return previousTierKey; }
    public void setPreviousTierKey(String previousTierKey) { this.previousTierKey = previousTierKey; }
    public String getPreviousBillingStatus() { return previousBillingStatus; }
    public void setPreviousBillingStatus(String previousBillingStatus) { this.previousBillingStatus = previousBillingStatus; }
    public String getPreviousSubscriptionId() { return previousSubscriptionId; }
    public void setPreviousSubscriptionId(String previousSubscriptionId) { this.previousSubscriptionId = previousSubscriptionId; }
    public String getPreviousSubscriptionName() { return previousSubscriptionName; }
    public void setPreviousSubscriptionName(String previousSubscriptionName) { this.previousSubscriptionName = previousSubscriptionName; }
    public String getActivationProvisioningJobId() { return activationProvisioningJobId; }
    public void setActivationProvisioningJobId(String activationProvisioningJobId) { this.activationProvisioningJobId = activationProvisioningJobId; }
    public String getDeactivationProvisioningJobId() { return deactivationProvisioningJobId; }
    public void setDeactivationProvisioningJobId(String deactivationProvisioningJobId) { this.deactivationProvisioningJobId = deactivationProvisioningJobId; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

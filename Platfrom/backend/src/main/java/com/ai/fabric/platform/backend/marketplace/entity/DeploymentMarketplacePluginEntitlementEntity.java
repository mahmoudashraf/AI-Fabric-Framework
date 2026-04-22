package com.ai.fabric.platform.backend.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "platform_deployment_marketplace_entitlements")
public class DeploymentMarketplacePluginEntitlementEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String installId;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String pluginId;

    @Column(nullable = false)
    private String pluginVersionId;

    @Column(nullable = false, length = 64)
    private String pricingModel;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 16)
    private String currency;

    @Column(length = 32)
    private String billingInterval;

    @Column(nullable = false, length = 64)
    private String status;

    private Instant graceEndsAt;

    private Instant accessEndsAt;

    @Column(columnDefinition = "TEXT")
    private String note;

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

    public String getInstallId() {
        return installId;
    }

    public void setInstallId(String installId) {
        this.installId = installId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginVersionId() {
        return pluginVersionId;
    }

    public void setPluginVersionId(String pluginVersionId) {
        this.pluginVersionId = pluginVersionId;
    }

    public String getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(String pricingModel) {
        this.pricingModel = pricingModel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBillingInterval() {
        return billingInterval;
    }

    public void setBillingInterval(String billingInterval) {
        this.billingInterval = billingInterval;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getGraceEndsAt() {
        return graceEndsAt;
    }

    public void setGraceEndsAt(Instant graceEndsAt) {
        this.graceEndsAt = graceEndsAt;
    }

    public Instant getAccessEndsAt() {
        return accessEndsAt;
    }

    public void setAccessEndsAt(Instant accessEndsAt) {
        this.accessEndsAt = accessEndsAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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

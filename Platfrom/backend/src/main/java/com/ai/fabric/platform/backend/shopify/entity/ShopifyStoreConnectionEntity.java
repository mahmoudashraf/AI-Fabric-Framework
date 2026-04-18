package com.ai.fabric.platform.backend.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_store_connections")
public class ShopifyStoreConnectionEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String shopDomain;

    @Column
    private String displayName;

    @Column(nullable = false)
    private String productServiceId;

    @Column
    private String customerId;

    @Column
    private String deploymentId;

    @Column
    private String consumerId;

    @Column(nullable = false)
    private String installStatus;

    @Column(nullable = false)
    private String syncStatus;

    @Column(nullable = false)
    private String sourceReadinessStatus;

    @Column(nullable = false)
    private String widgetStatus;

    @Column(nullable = false)
    private String onboardingStatus;

    @Column(nullable = false)
    private boolean productsEnabled;

    @Column(nullable = false)
    private boolean collectionsEnabled;

    @Column(nullable = false)
    private boolean pagesEnabled;

    @Column(nullable = false)
    private boolean policiesEnabled;

    @Column
    private Instant lastSourcePreflightAt;

    @Column
    private Instant lastSyncAt;

    @Column
    private Instant lastWebhookAt;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

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

    public String getShopDomain() {
        return shopDomain;
    }

    public void setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProductServiceId() {
        return productServiceId;
    }

    public void setProductServiceId(String productServiceId) {
        this.productServiceId = productServiceId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getInstallStatus() {
        return installStatus;
    }

    public void setInstallStatus(String installStatus) {
        this.installStatus = installStatus;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getSourceReadinessStatus() {
        return sourceReadinessStatus;
    }

    public void setSourceReadinessStatus(String sourceReadinessStatus) {
        this.sourceReadinessStatus = sourceReadinessStatus;
    }

    public String getWidgetStatus() {
        return widgetStatus;
    }

    public void setWidgetStatus(String widgetStatus) {
        this.widgetStatus = widgetStatus;
    }

    public String getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public boolean isProductsEnabled() {
        return productsEnabled;
    }

    public void setProductsEnabled(boolean productsEnabled) {
        this.productsEnabled = productsEnabled;
    }

    public boolean isCollectionsEnabled() {
        return collectionsEnabled;
    }

    public void setCollectionsEnabled(boolean collectionsEnabled) {
        this.collectionsEnabled = collectionsEnabled;
    }

    public boolean isPagesEnabled() {
        return pagesEnabled;
    }

    public void setPagesEnabled(boolean pagesEnabled) {
        this.pagesEnabled = pagesEnabled;
    }

    public boolean isPoliciesEnabled() {
        return policiesEnabled;
    }

    public void setPoliciesEnabled(boolean policiesEnabled) {
        this.policiesEnabled = policiesEnabled;
    }

    public Instant getLastSourcePreflightAt() {
        return lastSourcePreflightAt;
    }

    public void setLastSourcePreflightAt(Instant lastSourcePreflightAt) {
        this.lastSourcePreflightAt = lastSourcePreflightAt;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public Instant getLastWebhookAt() {
        return lastWebhookAt;
    }

    public void setLastWebhookAt(Instant lastWebhookAt) {
        this.lastWebhookAt = lastWebhookAt;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
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

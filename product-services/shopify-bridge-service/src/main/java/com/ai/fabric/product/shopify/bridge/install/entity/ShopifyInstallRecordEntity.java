package com.ai.fabric.product.shopify.bridge.install.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_install_record")
public class ShopifyInstallRecordEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(name = "shop_domain", nullable = false, length = 255, unique = true)
    private String shopDomain;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(name = "shop_url", length = 512)
    private String shopUrl;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "app_bridge_host", length = 1024)
    private String appBridgeHost;

    @Column(name = "access_token_secret_ref", length = 255)
    private String accessTokenSecretRef;

    @Column(name = "refresh_token_secret_ref", length = 255)
    private String refreshTokenSecretRef;

    @Column(name = "scopes_text", columnDefinition = "text")
    private String scopesText;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

    @Column(name = "last_uninstalled_at")
    private Instant lastUninstalledAt;

    @Column(name = "app_scopes_update_webhook_ready", nullable = false)
    private boolean appScopesUpdateWebhookReady;

    @Column(name = "app_scopes_update_webhook_checked_at")
    private Instant appScopesUpdateWebhookCheckedAt;

    @Column(name = "billing_tier_key", length = 64)
    private String billingTierKey;

    @Column(name = "billing_status", length = 64)
    private String billingStatus;

    @Column(name = "active_subscriptions_json", columnDefinition = "text")
    private String activeSubscriptionsJson;

    @Column(name = "billing_checked_at")
    private Instant billingCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShopUrl() {
        return shopUrl;
    }

    public void setShopUrl(String shopUrl) {
        this.shopUrl = shopUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAppBridgeHost() {
        return appBridgeHost;
    }

    public void setAppBridgeHost(String appBridgeHost) {
        this.appBridgeHost = appBridgeHost;
    }

    public String getAccessTokenSecretRef() {
        return accessTokenSecretRef;
    }

    public void setAccessTokenSecretRef(String accessTokenSecretRef) {
        this.accessTokenSecretRef = accessTokenSecretRef;
    }

    public String getScopesText() {
        return scopesText;
    }

    public void setScopesText(String scopesText) {
        this.scopesText = scopesText;
    }

    public String getRefreshTokenSecretRef() {
        return refreshTokenSecretRef;
    }

    public void setRefreshTokenSecretRef(String refreshTokenSecretRef) {
        this.refreshTokenSecretRef = refreshTokenSecretRef;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public Instant getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public void setRefreshTokenExpiresAt(Instant refreshTokenExpiresAt) {
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(Instant installedAt) {
        this.installedAt = installedAt;
    }

    public Instant getLastAuthenticatedAt() {
        return lastAuthenticatedAt;
    }

    public void setLastAuthenticatedAt(Instant lastAuthenticatedAt) {
        this.lastAuthenticatedAt = lastAuthenticatedAt;
    }

    public Instant getLastUninstalledAt() {
        return lastUninstalledAt;
    }

    public void setLastUninstalledAt(Instant lastUninstalledAt) {
        this.lastUninstalledAt = lastUninstalledAt;
    }

    public boolean isAppScopesUpdateWebhookReady() {
        return appScopesUpdateWebhookReady;
    }

    public void setAppScopesUpdateWebhookReady(boolean appScopesUpdateWebhookReady) {
        this.appScopesUpdateWebhookReady = appScopesUpdateWebhookReady;
    }

    public Instant getAppScopesUpdateWebhookCheckedAt() {
        return appScopesUpdateWebhookCheckedAt;
    }

    public void setAppScopesUpdateWebhookCheckedAt(Instant appScopesUpdateWebhookCheckedAt) {
        this.appScopesUpdateWebhookCheckedAt = appScopesUpdateWebhookCheckedAt;
    }

    public String getBillingTierKey() {
        return billingTierKey;
    }

    public void setBillingTierKey(String billingTierKey) {
        this.billingTierKey = billingTierKey;
    }

    public String getBillingStatus() {
        return billingStatus;
    }

    public void setBillingStatus(String billingStatus) {
        this.billingStatus = billingStatus;
    }

    public String getActiveSubscriptionsJson() {
        return activeSubscriptionsJson;
    }

    public void setActiveSubscriptionsJson(String activeSubscriptionsJson) {
        this.activeSubscriptionsJson = activeSubscriptionsJson;
    }

    public Instant getBillingCheckedAt() {
        return billingCheckedAt;
    }

    public void setBillingCheckedAt(Instant billingCheckedAt) {
        this.billingCheckedAt = billingCheckedAt;
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

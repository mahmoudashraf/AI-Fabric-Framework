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

    @Column(name = "scopes_text", columnDefinition = "text")
    private String scopesText;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

    @Column(name = "last_uninstalled_at")
    private Instant lastUninstalledAt;

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

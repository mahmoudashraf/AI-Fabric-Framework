package com.ai.fabric.product.shopify.bridge.customeraccount.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_customer_account_sessions")
public class ShopifyCustomerAccountSessionEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(name = "shop_domain", nullable = false, length = 255)
    private String shopDomain;

    @Column(name = "shopper_session_id_hash", nullable = false, length = 128)
    private String shopperSessionIdHash;

    @Column(name = "token_endpoint", length = 1024)
    private String tokenEndpoint;

    @Column(name = "access_token_ciphertext", nullable = false, columnDefinition = "text")
    private String accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext", columnDefinition = "text")
    private String refreshTokenCiphertext;

    @Column(name = "id_token_ciphertext", columnDefinition = "text")
    private String idTokenCiphertext;

    @Column(name = "token_type", length = 64)
    private String tokenType;

    @Column(name = "scopes_text", columnDefinition = "text")
    private String scopesText;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "session_expires_at", nullable = false)
    private Instant sessionExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

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

    public String getShopperSessionIdHash() {
        return shopperSessionIdHash;
    }

    public void setShopperSessionIdHash(String shopperSessionIdHash) {
        this.shopperSessionIdHash = shopperSessionIdHash;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getAccessTokenCiphertext() {
        return accessTokenCiphertext;
    }

    public void setAccessTokenCiphertext(String accessTokenCiphertext) {
        this.accessTokenCiphertext = accessTokenCiphertext;
    }

    public String getRefreshTokenCiphertext() {
        return refreshTokenCiphertext;
    }

    public void setRefreshTokenCiphertext(String refreshTokenCiphertext) {
        this.refreshTokenCiphertext = refreshTokenCiphertext;
    }

    public String getIdTokenCiphertext() {
        return idTokenCiphertext;
    }

    public void setIdTokenCiphertext(String idTokenCiphertext) {
        this.idTokenCiphertext = idTokenCiphertext;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getScopesText() {
        return scopesText;
    }

    public void setScopesText(String scopesText) {
        this.scopesText = scopesText;
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

    public Instant getSessionExpiresAt() {
        return sessionExpiresAt;
    }

    public void setSessionExpiresAt(Instant sessionExpiresAt) {
        this.sessionExpiresAt = sessionExpiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
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

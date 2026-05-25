package com.ai.fabric.product.shopify.bridge.customeraccount.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_customer_account_auth_claims")
public class ShopifyCustomerAccountAuthClaimEntity {

    @Id
    @Column(nullable = false, length = 80)
    private String id;

    @Column(name = "shop_domain", nullable = false, length = 255)
    private String shopDomain;

    @Column(name = "source_shopper_session_id_hash", nullable = false, length = 128)
    private String sourceShopperSessionIdHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public String getSourceShopperSessionIdHash() {
        return sourceShopperSessionIdHash;
    }

    public void setSourceShopperSessionIdHash(String sourceShopperSessionIdHash) {
        this.sourceShopperSessionIdHash = sourceShopperSessionIdHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

package com.ai.fabric.platform.backend.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_store_vectorization_policies")
public class ShopifyStoreVectorizationPolicyEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String shopDomain;

    @Column(nullable = false)
    private long policyVersion;

    @Column(nullable = false)
    private boolean autoIndexingDefault;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourcePoliciesJson;

    @Column
    private String updatedBy;

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

    public long getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(long policyVersion) {
        this.policyVersion = policyVersion;
    }

    public boolean isAutoIndexingDefault() {
        return autoIndexingDefault;
    }

    public void setAutoIndexingDefault(boolean autoIndexingDefault) {
        this.autoIndexingDefault = autoIndexingDefault;
    }

    public String getSourcePoliciesJson() {
        return sourcePoliciesJson;
    }

    public void setSourcePoliciesJson(String sourcePoliciesJson) {
        this.sourcePoliciesJson = sourcePoliciesJson;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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

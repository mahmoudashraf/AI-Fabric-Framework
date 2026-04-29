package com.ai.fabric.product.shopify.bridge.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "shopify_bridge_query_insights_daily")
public class ShopifyBridgeQueryInsightDailyEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String shopDomain;

    @Column(nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private String surfaceId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String queryKey;

    @Column(nullable = false)
    private String sampleQuery;

    @Column(nullable = false)
    private long queryCount;

    @Column(nullable = false)
    private Instant lastQueriedAt;

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

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(LocalDate usageDate) {
        this.usageDate = usageDate;
    }

    public String getSurfaceId() {
        return surfaceId;
    }

    public void setSurfaceId(String surfaceId) {
        this.surfaceId = surfaceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getQueryKey() {
        return queryKey;
    }

    public void setQueryKey(String queryKey) {
        this.queryKey = queryKey;
    }

    public String getSampleQuery() {
        return sampleQuery;
    }

    public void setSampleQuery(String sampleQuery) {
        this.sampleQuery = sampleQuery;
    }

    public long getQueryCount() {
        return queryCount;
    }

    public void setQueryCount(long queryCount) {
        this.queryCount = queryCount;
    }

    public Instant getLastQueriedAt() {
        return lastQueriedAt;
    }

    public void setLastQueriedAt(Instant lastQueriedAt) {
        this.lastQueriedAt = lastQueriedAt;
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

package com.ai.fabric.platform.backend.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_marketplace_plugins")
public class MarketplacePluginEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String pluginType;

    @Column(nullable = false)
    private String publisherSlug;

    @Column(nullable = false)
    private String publisherDisplayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String shortDescription;

    @Column(nullable = false)
    private String status;

    private String ownerCustomerId;

    @Column(nullable = false)
    private String visibility = "PUBLIC";

    @Column(columnDefinition = "TEXT")
    private String draftManifestJson;

    @Column(nullable = false)
    private long draftRevision;

    private String catalogueFamily;

    @Column(nullable = false)
    private String catalogueAudience = "CUSTOMER";

    private String adapterContract;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public String getOwnerCustomerId() { return ownerCustomerId; }
    public void setOwnerCustomerId(String value) { ownerCustomerId = value; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String value) { visibility = value; }
    public String getDraftManifestJson() { return draftManifestJson; }
    public void setDraftManifestJson(String value) { draftManifestJson = value; }
    public long getDraftRevision() { return draftRevision; }
    public void setDraftRevision(long value) { draftRevision = value; }
    public String getCatalogueFamily() { return catalogueFamily; }
    public void setCatalogueFamily(String value) { catalogueFamily = value; }
    public String getCatalogueAudience() { return catalogueAudience; }
    public void setCatalogueAudience(String value) { catalogueAudience = value; }
    public String getAdapterContract() { return adapterContract; }
    public void setAdapterContract(String value) { adapterContract = value; }

    public void setId(String id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPluginType() {
        return pluginType;
    }

    public void setPluginType(String pluginType) {
        this.pluginType = pluginType;
    }

    public String getPublisherSlug() {
        return publisherSlug;
    }

    public void setPublisherSlug(String publisherSlug) {
        this.publisherSlug = publisherSlug;
    }

    public String getPublisherDisplayName() {
        return publisherDisplayName;
    }

    public void setPublisherDisplayName(String publisherDisplayName) {
        this.publisherDisplayName = publisherDisplayName;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

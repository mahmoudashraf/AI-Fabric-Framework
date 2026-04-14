package com.ai.fabric.platform.backend.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_marketplace_plugin_versions")
public class MarketplacePluginVersionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String pluginId;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String releaseChannel;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String manifestJson;

    @Column(length = 64)
    private String submittedByPublisherId;

    @Column(length = 64)
    private String submittedByActorId;

    @Column(length = 64)
    private String reviewedByActorId;

    private Instant reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(length = 128)
    private String bundleSha256;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant publishedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReleaseChannel() {
        return releaseChannel;
    }

    public void setReleaseChannel(String releaseChannel) {
        this.releaseChannel = releaseChannel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSubmittedByPublisherId() {
        return submittedByPublisherId;
    }

    public void setSubmittedByPublisherId(String submittedByPublisherId) {
        this.submittedByPublisherId = submittedByPublisherId;
    }

    public String getSubmittedByActorId() {
        return submittedByActorId;
    }

    public void setSubmittedByActorId(String submittedByActorId) {
        this.submittedByActorId = submittedByActorId;
    }

    public String getReviewedByActorId() {
        return reviewedByActorId;
    }

    public void setReviewedByActorId(String reviewedByActorId) {
        this.reviewedByActorId = reviewedByActorId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public String getBundleSha256() {
        return bundleSha256;
    }

    public void setBundleSha256(String bundleSha256) {
        this.bundleSha256 = bundleSha256;
    }
}

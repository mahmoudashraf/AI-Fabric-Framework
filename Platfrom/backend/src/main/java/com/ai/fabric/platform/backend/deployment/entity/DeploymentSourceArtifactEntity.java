package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "deployment_source_artifacts")
public class DeploymentSourceArtifactEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "artifact_type", nullable = false, length = 64)
    private String artifactType;

    @Column(name = "image_repository", length = 512)
    private String imageRepository;

    @Column(name = "image_tag", length = 255)
    private String imageTag;

    @Column(name = "image_digest", length = 255)
    private String imageDigest;

    @Column(name = "git_commit_sha", length = 128)
    private String gitCommitSha;

    @Column(name = "build_run_id", length = 255)
    private String buildRunId;

    @Column(name = "sbom_ref", length = 512)
    private String sbomRef;

    @Column(name = "promotion_channel", length = 64)
    private String promotionChannel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getImageRepository() {
        return imageRepository;
    }

    public void setImageRepository(String imageRepository) {
        this.imageRepository = imageRepository;
    }

    public String getImageTag() {
        return imageTag;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    public String getImageDigest() {
        return imageDigest;
    }

    public void setImageDigest(String imageDigest) {
        this.imageDigest = imageDigest;
    }

    public String getGitCommitSha() {
        return gitCommitSha;
    }

    public void setGitCommitSha(String gitCommitSha) {
        this.gitCommitSha = gitCommitSha;
    }

    public String getBuildRunId() {
        return buildRunId;
    }

    public void setBuildRunId(String buildRunId) {
        this.buildRunId = buildRunId;
    }

    public String getSbomRef() {
        return sbomRef;
    }

    public void setSbomRef(String sbomRef) {
        this.sbomRef = sbomRef;
    }

    public String getPromotionChannel() {
        return promotionChannel;
    }

    public void setPromotionChannel(String promotionChannel) {
        this.promotionChannel = promotionChannel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(Instant promotedAt) {
        this.promotedAt = promotedAt;
    }
}

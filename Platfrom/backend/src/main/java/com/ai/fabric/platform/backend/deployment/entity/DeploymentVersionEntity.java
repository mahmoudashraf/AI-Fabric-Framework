package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_versions")
public class DeploymentVersionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String sourceDraftId;

    @Column(nullable = false)
    private String versionLabel;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String configHash;

    @Column(nullable = false)
    private boolean reindexRequired;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String actionsConfigJson;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String entityConfigJson;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String routingConfigJson;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String providerConfigJson;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String securityConfigJson;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String actionsArtifactYaml;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String entityArtifactYaml;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String routingArtifactYaml;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String manifestJson;

    @Column(nullable = false)
    private Instant publishedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getSourceDraftId() {
        return sourceDraftId;
    }

    public void setSourceDraftId(String sourceDraftId) {
        this.sourceDraftId = sourceDraftId;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfigHash() {
        return configHash;
    }

    public void setConfigHash(String configHash) {
        this.configHash = configHash;
    }

    public boolean isReindexRequired() {
        return reindexRequired;
    }

    public void setReindexRequired(boolean reindexRequired) {
        this.reindexRequired = reindexRequired;
    }

    public String getActionsConfigJson() {
        return actionsConfigJson;
    }

    public void setActionsConfigJson(String actionsConfigJson) {
        this.actionsConfigJson = actionsConfigJson;
    }

    public String getEntityConfigJson() {
        return entityConfigJson;
    }

    public void setEntityConfigJson(String entityConfigJson) {
        this.entityConfigJson = entityConfigJson;
    }

    public String getRoutingConfigJson() {
        return routingConfigJson;
    }

    public void setRoutingConfigJson(String routingConfigJson) {
        this.routingConfigJson = routingConfigJson;
    }

    public String getProviderConfigJson() {
        return providerConfigJson;
    }

    public void setProviderConfigJson(String providerConfigJson) {
        this.providerConfigJson = providerConfigJson;
    }

    public String getSecurityConfigJson() {
        return securityConfigJson;
    }

    public void setSecurityConfigJson(String securityConfigJson) {
        this.securityConfigJson = securityConfigJson;
    }

    public String getActionsArtifactYaml() {
        return actionsArtifactYaml;
    }

    public void setActionsArtifactYaml(String actionsArtifactYaml) {
        this.actionsArtifactYaml = actionsArtifactYaml;
    }

    public String getEntityArtifactYaml() {
        return entityArtifactYaml;
    }

    public void setEntityArtifactYaml(String entityArtifactYaml) {
        this.entityArtifactYaml = entityArtifactYaml;
    }

    public String getRoutingArtifactYaml() {
        return routingArtifactYaml;
    }

    public void setRoutingArtifactYaml(String routingArtifactYaml) {
        this.routingArtifactYaml = routingArtifactYaml;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}


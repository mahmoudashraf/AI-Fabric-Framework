package com.ai.fabric.platform.backend.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_companion_package_profiles")
public class ShopifyCompanionPackageProfileEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String profileKey;

    @Column(nullable = false)
    private String packageKey;

    @Column(nullable = false)
    private String tierKey;

    @Column(nullable = false)
    private String runtimeProfileKey;

    @Column(nullable = false)
    private String vectorProfileKey;

    @Column(nullable = false)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String costPosture;

    @Column(nullable = false)
    private String templatePluginId;

    @Column
    private String templatePluginVersion;

    @Column(nullable = false)
    private String deploymentTemplateId;

    @Column(nullable = false)
    private String inferencePluginId;

    @Column(nullable = false)
    private String vectorStrategy;

    @Column(nullable = false)
    private String vectorProvisioningMode;

    @Column(nullable = false)
    private String vectorStoragePosture;

    @Column(nullable = false)
    private String verificationPackId;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProfileKey() { return profileKey; }
    public void setProfileKey(String profileKey) { this.profileKey = profileKey; }
    public String getPackageKey() { return packageKey; }
    public void setPackageKey(String packageKey) { this.packageKey = packageKey; }
    public String getTierKey() { return tierKey; }
    public void setTierKey(String tierKey) { this.tierKey = tierKey; }
    public String getRuntimeProfileKey() { return runtimeProfileKey; }
    public void setRuntimeProfileKey(String runtimeProfileKey) { this.runtimeProfileKey = runtimeProfileKey; }
    public String getVectorProfileKey() { return vectorProfileKey; }
    public void setVectorProfileKey(String vectorProfileKey) { this.vectorProfileKey = vectorProfileKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCostPosture() { return costPosture; }
    public void setCostPosture(String costPosture) { this.costPosture = costPosture; }
    public String getTemplatePluginId() { return templatePluginId; }
    public void setTemplatePluginId(String templatePluginId) { this.templatePluginId = templatePluginId; }
    public String getTemplatePluginVersion() { return templatePluginVersion; }
    public void setTemplatePluginVersion(String templatePluginVersion) { this.templatePluginVersion = templatePluginVersion; }
    public String getDeploymentTemplateId() { return deploymentTemplateId; }
    public void setDeploymentTemplateId(String deploymentTemplateId) { this.deploymentTemplateId = deploymentTemplateId; }
    public String getInferencePluginId() { return inferencePluginId; }
    public void setInferencePluginId(String inferencePluginId) { this.inferencePluginId = inferencePluginId; }
    public String getVectorStrategy() { return vectorStrategy; }
    public void setVectorStrategy(String vectorStrategy) { this.vectorStrategy = vectorStrategy; }
    public String getVectorProvisioningMode() { return vectorProvisioningMode; }
    public void setVectorProvisioningMode(String vectorProvisioningMode) { this.vectorProvisioningMode = vectorProvisioningMode; }
    public String getVectorStoragePosture() { return vectorStoragePosture; }
    public void setVectorStoragePosture(String vectorStoragePosture) { this.vectorStoragePosture = vectorStoragePosture; }
    public String getVerificationPackId() { return verificationPackId; }
    public void setVerificationPackId(String verificationPackId) { this.verificationPackId = verificationPackId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

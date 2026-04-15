package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_drafts")
public class DeploymentDraftEntity {

    public static final String DEFAULT_KNOWLEDGE_SOURCE_CONFIG_JSON =
        "{\"contractVersion\":\"KNOWLEDGE_SOURCE_CONFIG_V1\",\"sources\":[]}";
    public static final String DEFAULT_SHELL_CONFIG_JSON =
        "{\"contractVersion\":\"SHELL_CONFIG_V1\",\"modules\":[],\"cards\":[]}";
    public static final String DEFAULT_AUTOMATION_CONFIG_JSON =
        "{\"contractVersion\":\"AUTOMATION_CONFIG_V1\",\"triggers\":[],\"actions\":[],\"workflows\":[],\"schedules\":[]}";
    public static final String DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON =
        "{\"contractVersion\":\"MARKETPLACE_DATASET_CONFIG_V1\",\"datasets\":[]}";

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private Integer revisionNumber;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String actionsConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String entityConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String routingConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String providerConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String securityConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String promptConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String knowledgeSourceConfigJson = DEFAULT_KNOWLEDGE_SOURCE_CONFIG_JSON;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String shellConfigJson = DEFAULT_SHELL_CONFIG_JSON;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String automationConfigJson = DEFAULT_AUTOMATION_CONFIG_JSON;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String marketplaceDatasetConfigJson = DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON;

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

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(Integer revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getPromptConfigJson() {
        return promptConfigJson;
    }

    public void setPromptConfigJson(String promptConfigJson) {
        this.promptConfigJson = promptConfigJson;
    }

    public String getKnowledgeSourceConfigJson() {
        return knowledgeSourceConfigJson == null
            ? DEFAULT_KNOWLEDGE_SOURCE_CONFIG_JSON
            : knowledgeSourceConfigJson;
    }

    public void setKnowledgeSourceConfigJson(String knowledgeSourceConfigJson) {
        this.knowledgeSourceConfigJson = knowledgeSourceConfigJson;
    }

    public String getShellConfigJson() {
        return shellConfigJson == null
            ? DEFAULT_SHELL_CONFIG_JSON
            : shellConfigJson;
    }

    public void setShellConfigJson(String shellConfigJson) {
        this.shellConfigJson = shellConfigJson;
    }

    public String getAutomationConfigJson() {
        return automationConfigJson == null
            ? DEFAULT_AUTOMATION_CONFIG_JSON
            : automationConfigJson;
    }

    public void setAutomationConfigJson(String automationConfigJson) {
        this.automationConfigJson = automationConfigJson;
    }

    public String getMarketplaceDatasetConfigJson() {
        return marketplaceDatasetConfigJson == null
            ? DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON
            : marketplaceDatasetConfigJson;
    }

    public void setMarketplaceDatasetConfigJson(String marketplaceDatasetConfigJson) {
        this.marketplaceDatasetConfigJson = marketplaceDatasetConfigJson;
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

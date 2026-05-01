package com.ai.fabric.platform.backend.deployment.entity;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "deployment_target_profiles")
public class DeploymentTargetProfileEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 64)
    private DeploymentProviderType providerType;

    @Column(name = "environment_name", nullable = false, length = 64)
    private String environmentName;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "default_for_runtime", nullable = false)
    private boolean defaultForRuntime;

    @Column(name = "default_for_restartable_services", nullable = false)
    private boolean defaultForRestartableServices;

    @Column(name = "platform_services_allowed", nullable = false)
    private boolean platformServicesAllowed;

    @Column(name = "source_strategy", nullable = false, length = 64)
    private String sourceStrategy;

    @Column(name = "credential_ref_id", length = 64)
    private String credentialRefId;

    @Column(name = "provider_config_json", nullable = false, columnDefinition = "TEXT")
    private String providerConfigJson;

    @Column(name = "network_policy_json", nullable = false, columnDefinition = "TEXT")
    private String networkPolicyJson;

    @Column(name = "resource_defaults_json", nullable = false, columnDefinition = "TEXT")
    private String resourceDefaultsJson;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DeploymentProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(DeploymentProviderType providerType) {
        this.providerType = providerType;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public void setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDefaultForRuntime() {
        return defaultForRuntime;
    }

    public void setDefaultForRuntime(boolean defaultForRuntime) {
        this.defaultForRuntime = defaultForRuntime;
    }

    public boolean isDefaultForRestartableServices() {
        return defaultForRestartableServices;
    }

    public void setDefaultForRestartableServices(boolean defaultForRestartableServices) {
        this.defaultForRestartableServices = defaultForRestartableServices;
    }

    public boolean isPlatformServicesAllowed() {
        return platformServicesAllowed;
    }

    public void setPlatformServicesAllowed(boolean platformServicesAllowed) {
        this.platformServicesAllowed = platformServicesAllowed;
    }

    public String getSourceStrategy() {
        return sourceStrategy;
    }

    public void setSourceStrategy(String sourceStrategy) {
        this.sourceStrategy = sourceStrategy;
    }

    public String getCredentialRefId() {
        return credentialRefId;
    }

    public void setCredentialRefId(String credentialRefId) {
        this.credentialRefId = credentialRefId;
    }

    public String getProviderConfigJson() {
        return providerConfigJson;
    }

    public void setProviderConfigJson(String providerConfigJson) {
        this.providerConfigJson = providerConfigJson;
    }

    public String getNetworkPolicyJson() {
        return networkPolicyJson;
    }

    public void setNetworkPolicyJson(String networkPolicyJson) {
        this.networkPolicyJson = networkPolicyJson;
    }

    public String getResourceDefaultsJson() {
        return resourceDefaultsJson;
    }

    public void setResourceDefaultsJson(String resourceDefaultsJson) {
        this.resourceDefaultsJson = resourceDefaultsJson;
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

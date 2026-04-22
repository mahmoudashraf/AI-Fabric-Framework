package com.ai.fabric.platform.backend.productservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_managed_product_services")
public class PlatformManagedProductServiceEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 128)
    private String serviceRef;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String productFamily;

    @Column(nullable = false)
    private String serviceKind;

    @Column(nullable = false)
    private String deploymentMode;

    @Column(nullable = false)
    private String tenantMode;

    @Column
    private String environmentScope;

    @Column
    private String deploymentId;

    @Column
    private String railwayProjectId;

    @Column
    private String railwayEnvironmentId;

    @Column
    private String railwayServiceId;

    @Column
    private Integer desiredReplicas;

    @Column
    private Integer actualReplicas;

    @Column
    private Integer minReplicas;

    @Column
    private Integer maxReplicas;

    @Column(columnDefinition = "TEXT")
    private String baseUrl;

    @Column(columnDefinition = "TEXT")
    private String privateNetworkUrl;

    @Column
    private String healthPath;

    @Column(columnDefinition = "TEXT")
    private String serviceRoot;

    @Column(columnDefinition = "TEXT")
    private String dockerfilePath;

    @Column
    private String secretName;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

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

    public String getServiceRef() {
        return serviceRef;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProductFamily() {
        return productFamily;
    }

    public void setProductFamily(String productFamily) {
        this.productFamily = productFamily;
    }

    public String getServiceKind() {
        return serviceKind;
    }

    public void setServiceKind(String serviceKind) {
        this.serviceKind = serviceKind;
    }

    public String getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public String getTenantMode() {
        return tenantMode;
    }

    public void setTenantMode(String tenantMode) {
        this.tenantMode = tenantMode;
    }

    public String getEnvironmentScope() {
        return environmentScope;
    }

    public void setEnvironmentScope(String environmentScope) {
        this.environmentScope = environmentScope;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getRailwayProjectId() {
        return railwayProjectId;
    }

    public void setRailwayProjectId(String railwayProjectId) {
        this.railwayProjectId = railwayProjectId;
    }

    public String getRailwayEnvironmentId() {
        return railwayEnvironmentId;
    }

    public void setRailwayEnvironmentId(String railwayEnvironmentId) {
        this.railwayEnvironmentId = railwayEnvironmentId;
    }

    public String getRailwayServiceId() {
        return railwayServiceId;
    }

    public void setRailwayServiceId(String railwayServiceId) {
        this.railwayServiceId = railwayServiceId;
    }

    public Integer getDesiredReplicas() {
        return desiredReplicas;
    }

    public void setDesiredReplicas(Integer desiredReplicas) {
        this.desiredReplicas = desiredReplicas;
    }

    public Integer getActualReplicas() {
        return actualReplicas;
    }

    public void setActualReplicas(Integer actualReplicas) {
        this.actualReplicas = actualReplicas;
    }

    public Integer getMinReplicas() {
        return minReplicas;
    }

    public void setMinReplicas(Integer minReplicas) {
        this.minReplicas = minReplicas;
    }

    public Integer getMaxReplicas() {
        return maxReplicas;
    }

    public void setMaxReplicas(Integer maxReplicas) {
        this.maxReplicas = maxReplicas;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPrivateNetworkUrl() {
        return privateNetworkUrl;
    }

    public void setPrivateNetworkUrl(String privateNetworkUrl) {
        this.privateNetworkUrl = privateNetworkUrl;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public String getServiceRoot() {
        return serviceRoot;
    }

    public void setServiceRoot(String serviceRoot) {
        this.serviceRoot = serviceRoot;
    }

    public String getDockerfilePath() {
        return dockerfilePath;
    }

    public void setDockerfilePath(String dockerfilePath) {
        this.dockerfilePath = dockerfilePath;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
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


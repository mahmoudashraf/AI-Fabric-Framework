package com.ai.fabric.platform.backend.thinker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "thinker_deployment_controls")
public class ThinkerDeploymentControlEntity {
    @Id
    private String deploymentId;
    @Column(nullable = false)
    private boolean thinkerEnabled;
    @Column(nullable = false)
    private boolean resolverPreviewEnabled;
    @Column(nullable = false)
    private boolean governedExecutionEnabled;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String disabledActionFamiliesJson = "[]";
    @Column
    private String updatedBy;
    @Column(nullable = false)
    private Instant updatedAt;

    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public boolean isThinkerEnabled() { return thinkerEnabled; }
    public void setThinkerEnabled(boolean thinkerEnabled) { this.thinkerEnabled = thinkerEnabled; }
    public boolean isResolverPreviewEnabled() { return resolverPreviewEnabled; }
    public void setResolverPreviewEnabled(boolean resolverPreviewEnabled) { this.resolverPreviewEnabled = resolverPreviewEnabled; }
    public boolean isGovernedExecutionEnabled() { return governedExecutionEnabled; }
    public void setGovernedExecutionEnabled(boolean governedExecutionEnabled) { this.governedExecutionEnabled = governedExecutionEnabled; }
    public String getDisabledActionFamiliesJson() { return disabledActionFamiliesJson; }
    public void setDisabledActionFamiliesJson(String disabledActionFamiliesJson) { this.disabledActionFamiliesJson = disabledActionFamiliesJson; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

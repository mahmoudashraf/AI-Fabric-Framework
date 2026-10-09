package com.ai.fabric.platform.backend.aiworkspace.entity;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "ai_workspace_installations")
public class AIWorkspaceInstallationEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String installationId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String consumerEntityId;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AIWorkspaceInstallationStatus status;

    @Column(nullable = false)
    private String experiencePackCode;

    @Column(nullable = false)
    private String experiencePackVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AIWorkspaceConnectionMode connectionMode;

    @Column(nullable = false)
    private String connectionProfileCode;

    @Column(nullable = false)
    private String connectionProfileVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String connectionConfigurationJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String allowedOriginsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String configurationJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant activatedAt;

    private Instant disabledAt;

    @Version
    @Column(nullable = false)
    private long rowVersion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getConsumerEntityId() { return consumerEntityId; }
    public void setConsumerEntityId(String consumerEntityId) { this.consumerEntityId = consumerEntityId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public AIWorkspaceInstallationStatus getStatus() { return status; }
    public void setStatus(AIWorkspaceInstallationStatus status) { this.status = status; }
    public String getExperiencePackCode() { return experiencePackCode; }
    public void setExperiencePackCode(String experiencePackCode) { this.experiencePackCode = experiencePackCode; }
    public String getExperiencePackVersion() { return experiencePackVersion; }
    public void setExperiencePackVersion(String experiencePackVersion) { this.experiencePackVersion = experiencePackVersion; }
    public AIWorkspaceConnectionMode getConnectionMode() { return connectionMode; }
    public void setConnectionMode(AIWorkspaceConnectionMode connectionMode) { this.connectionMode = connectionMode; }
    public String getConnectionProfileCode() { return connectionProfileCode; }
    public void setConnectionProfileCode(String connectionProfileCode) { this.connectionProfileCode = connectionProfileCode; }
    public String getConnectionProfileVersion() { return connectionProfileVersion; }
    public void setConnectionProfileVersion(String connectionProfileVersion) { this.connectionProfileVersion = connectionProfileVersion; }
    public String getConnectionConfigurationJson() { return connectionConfigurationJson; }
    public void setConnectionConfigurationJson(String connectionConfigurationJson) { this.connectionConfigurationJson = connectionConfigurationJson; }
    public String getAllowedOriginsJson() { return allowedOriginsJson; }
    public void setAllowedOriginsJson(String allowedOriginsJson) { this.allowedOriginsJson = allowedOriginsJson; }
    public String getConfigurationJson() { return configurationJson; }
    public void setConfigurationJson(String configurationJson) { this.configurationJson = configurationJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public Instant getDisabledAt() { return disabledAt; }
    public void setDisabledAt(Instant disabledAt) { this.disabledAt = disabledAt; }
    public long getRowVersion() { return rowVersion; }
}

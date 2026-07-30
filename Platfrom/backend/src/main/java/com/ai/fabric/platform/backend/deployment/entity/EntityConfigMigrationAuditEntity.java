package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_entity_config_migration_audits")
public class EntityConfigMigrationAuditEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String draftId;

    @Column(nullable = false)
    private String sourceContractVersion;

    @Column(nullable = false)
    private String targetContractVersion;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String beforeHash;

    private String afterHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String beforeConfigJson;

    @Column(columnDefinition = "TEXT")
    private String afterConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reportJson;

    @Column(nullable = false)
    private Instant createdAt;

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

    public String getDraftId() {
        return draftId;
    }

    public void setDraftId(String draftId) {
        this.draftId = draftId;
    }

    public String getSourceContractVersion() {
        return sourceContractVersion;
    }

    public void setSourceContractVersion(String sourceContractVersion) {
        this.sourceContractVersion = sourceContractVersion;
    }

    public String getTargetContractVersion() {
        return targetContractVersion;
    }

    public void setTargetContractVersion(String targetContractVersion) {
        this.targetContractVersion = targetContractVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBeforeHash() {
        return beforeHash;
    }

    public void setBeforeHash(String beforeHash) {
        this.beforeHash = beforeHash;
    }

    public String getAfterHash() {
        return afterHash;
    }

    public void setAfterHash(String afterHash) {
        this.afterHash = afterHash;
    }

    public String getBeforeConfigJson() {
        return beforeConfigJson;
    }

    public void setBeforeConfigJson(String beforeConfigJson) {
        this.beforeConfigJson = beforeConfigJson;
    }

    public String getAfterConfigJson() {
        return afterConfigJson;
    }

    public void setAfterConfigJson(String afterConfigJson) {
        this.afterConfigJson = afterConfigJson;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

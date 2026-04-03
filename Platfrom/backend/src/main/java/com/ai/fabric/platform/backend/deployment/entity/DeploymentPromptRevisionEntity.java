package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_prompt_revisions")
public class DeploymentPromptRevisionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String sourceDraftId;

    @Column(nullable = false)
    private String revisionLabel;

    @Column(columnDefinition = "TEXT")
    private String revisionSummary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String promptConfigJson;

    @Column(nullable = false)
    private String createdByActorId;

    @Column
    private String createdByDisplayName;

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

    public String getSourceDraftId() {
        return sourceDraftId;
    }

    public void setSourceDraftId(String sourceDraftId) {
        this.sourceDraftId = sourceDraftId;
    }

    public String getRevisionLabel() {
        return revisionLabel;
    }

    public void setRevisionLabel(String revisionLabel) {
        this.revisionLabel = revisionLabel;
    }

    public String getRevisionSummary() {
        return revisionSummary;
    }

    public void setRevisionSummary(String revisionSummary) {
        this.revisionSummary = revisionSummary;
    }

    public String getPromptConfigJson() {
        return promptConfigJson;
    }

    public void setPromptConfigJson(String promptConfigJson) {
        this.promptConfigJson = promptConfigJson;
    }

    public String getCreatedByActorId() {
        return createdByActorId;
    }

    public void setCreatedByActorId(String createdByActorId) {
        this.createdByActorId = createdByActorId;
    }

    public String getCreatedByDisplayName() {
        return createdByDisplayName;
    }

    public void setCreatedByDisplayName(String createdByDisplayName) {
        this.createdByDisplayName = createdByDisplayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

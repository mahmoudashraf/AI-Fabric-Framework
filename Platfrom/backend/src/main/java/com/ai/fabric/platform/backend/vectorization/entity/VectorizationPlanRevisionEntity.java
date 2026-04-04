package com.ai.fabric.platform.backend.vectorization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_vectorization_plan_revisions")
public class VectorizationPlanRevisionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String planId;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private Integer revisionNumber;

    @Column(nullable = false)
    private String status;

    private String sourceConnectionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String entityScopeJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mappingConfigJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String executionConfigJson;

    private String indexedOutputHash;

    private String createdByActorId;

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

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
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

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public void setSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
    }

    public String getEntityScopeJson() {
        return entityScopeJson;
    }

    public void setEntityScopeJson(String entityScopeJson) {
        this.entityScopeJson = entityScopeJson;
    }

    public String getMappingConfigJson() {
        return mappingConfigJson;
    }

    public void setMappingConfigJson(String mappingConfigJson) {
        this.mappingConfigJson = mappingConfigJson;
    }

    public String getExecutionConfigJson() {
        return executionConfigJson;
    }

    public void setExecutionConfigJson(String executionConfigJson) {
        this.executionConfigJson = executionConfigJson;
    }

    public String getIndexedOutputHash() {
        return indexedOutputHash;
    }

    public void setIndexedOutputHash(String indexedOutputHash) {
        this.indexedOutputHash = indexedOutputHash;
    }

    public String getCreatedByActorId() {
        return createdByActorId;
    }

    public void setCreatedByActorId(String createdByActorId) {
        this.createdByActorId = createdByActorId;
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

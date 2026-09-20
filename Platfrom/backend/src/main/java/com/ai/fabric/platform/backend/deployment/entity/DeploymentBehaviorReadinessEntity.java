package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_behavior_readiness")
public class DeploymentBehaviorReadinessEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String behaviorType;

    @Column(nullable = false)
    private String templatePluginId;

    @Column(nullable = false)
    private String templatePluginVersionId;

    @Column(nullable = false)
    private String templatePluginVersion;

    @Column(nullable = false)
    private String compositionHash;

    @Column(nullable = false, unique = true)
    private String materialHash;

    @Column(nullable = false)
    private String frameworkVersion;

    @Column(nullable = false)
    private String sourceArtifactId;

    @Column(nullable = false)
    private String sourceCommit;

    @Column(nullable = false)
    private String imageDigest;

    @Column(nullable = false)
    private String sourceCapabilityManifestHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String verificationPackIdsJson;

    @Column(nullable = false)
    private String maturity;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String hostedProofsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String approvalEvidenceJson;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String deploymentVersionId;

    @Column(nullable = false)
    private String releaseId;

    @Column(nullable = false)
    private Instant evaluatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private String approvedByActorId;

    private Instant approvedAt;

    @Column(columnDefinition = "TEXT")
    private String approvalNote;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBehaviorType() { return behaviorType; }
    public void setBehaviorType(String behaviorType) { this.behaviorType = behaviorType; }
    public String getTemplatePluginId() { return templatePluginId; }
    public void setTemplatePluginId(String templatePluginId) { this.templatePluginId = templatePluginId; }
    public String getTemplatePluginVersionId() { return templatePluginVersionId; }
    public void setTemplatePluginVersionId(String templatePluginVersionId) { this.templatePluginVersionId = templatePluginVersionId; }
    public String getTemplatePluginVersion() { return templatePluginVersion; }
    public void setTemplatePluginVersion(String templatePluginVersion) { this.templatePluginVersion = templatePluginVersion; }
    public String getCompositionHash() { return compositionHash; }
    public void setCompositionHash(String compositionHash) { this.compositionHash = compositionHash; }
    public String getMaterialHash() { return materialHash; }
    public void setMaterialHash(String materialHash) { this.materialHash = materialHash; }
    public String getFrameworkVersion() { return frameworkVersion; }
    public void setFrameworkVersion(String frameworkVersion) { this.frameworkVersion = frameworkVersion; }
    public String getSourceArtifactId() { return sourceArtifactId; }
    public void setSourceArtifactId(String sourceArtifactId) { this.sourceArtifactId = sourceArtifactId; }
    public String getSourceCommit() { return sourceCommit; }
    public void setSourceCommit(String sourceCommit) { this.sourceCommit = sourceCommit; }
    public String getImageDigest() { return imageDigest; }
    public void setImageDigest(String imageDigest) { this.imageDigest = imageDigest; }
    public String getSourceCapabilityManifestHash() { return sourceCapabilityManifestHash; }
    public void setSourceCapabilityManifestHash(String sourceCapabilityManifestHash) { this.sourceCapabilityManifestHash = sourceCapabilityManifestHash; }
    public String getVerificationPackIdsJson() { return verificationPackIdsJson; }
    public void setVerificationPackIdsJson(String verificationPackIdsJson) { this.verificationPackIdsJson = verificationPackIdsJson; }
    public String getMaturity() { return maturity; }
    public void setMaturity(String maturity) { this.maturity = maturity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHostedProofsJson() { return hostedProofsJson; }
    public void setHostedProofsJson(String hostedProofsJson) { this.hostedProofsJson = hostedProofsJson; }
    public String getApprovalEvidenceJson() { return approvalEvidenceJson; }
    public void setApprovalEvidenceJson(String approvalEvidenceJson) { this.approvalEvidenceJson = approvalEvidenceJson; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getDeploymentVersionId() { return deploymentVersionId; }
    public void setDeploymentVersionId(String deploymentVersionId) { this.deploymentVersionId = deploymentVersionId; }
    public String getReleaseId() { return releaseId; }
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getApprovedByActorId() { return approvedByActorId; }
    public void setApprovedByActorId(String approvedByActorId) { this.approvedByActorId = approvedByActorId; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalNote() { return approvalNote; }
    public void setApprovalNote(String approvalNote) { this.approvalNote = approvalNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

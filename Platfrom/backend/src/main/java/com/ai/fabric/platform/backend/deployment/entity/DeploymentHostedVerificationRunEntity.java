package com.ai.fabric.platform.backend.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_deployment_hosted_verification_runs")
public class DeploymentHostedVerificationRunEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String releaseId;

    @Column(nullable = false)
    private String deploymentVersionId;

    @Column(nullable = false)
    private String verificationProfile;

    @Column(nullable = false)
    private String runnerType;

    @Column(nullable = false)
    private String scriptPath;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean verifyWrite;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryMessage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String logOutput;

    @Column
    private Integer exitCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

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

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public String getDeploymentVersionId() {
        return deploymentVersionId;
    }

    public void setDeploymentVersionId(String deploymentVersionId) {
        this.deploymentVersionId = deploymentVersionId;
    }

    public String getVerificationProfile() {
        return verificationProfile;
    }

    public void setVerificationProfile(String verificationProfile) {
        this.verificationProfile = verificationProfile;
    }

    public String getRunnerType() {
        return runnerType;
    }

    public void setRunnerType(String runnerType) {
        this.runnerType = runnerType;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isVerifyWrite() {
        return verifyWrite;
    }

    public void setVerifyWrite(boolean verifyWrite) {
        this.verifyWrite = verifyWrite;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    public String getLogOutput() {
        return logOutput;
    }

    public void setLogOutput(String logOutput) {
        this.logOutput = logOutput;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

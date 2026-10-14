package com.ai.fabric.platform.backend.aiworkspace.entity;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "ai_workspace_domains")
public class AIWorkspaceDomainEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String installationEntityId;

    @Column(nullable = false, unique = true)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AIWorkspaceDomainType domainType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AIWorkspaceDomainStatus status;

    @Column(nullable = false)
    private String verificationToken;

    @Column(columnDefinition = "TEXT")
    private String lastVerificationError;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant verifiedAt;

    @Version
    @Column(nullable = false)
    private long rowVersion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstallationEntityId() { return installationEntityId; }
    public void setInstallationEntityId(String installationEntityId) { this.installationEntityId = installationEntityId; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public AIWorkspaceDomainType getDomainType() { return domainType; }
    public void setDomainType(AIWorkspaceDomainType domainType) { this.domainType = domainType; }
    public AIWorkspaceDomainStatus getStatus() { return status; }
    public void setStatus(AIWorkspaceDomainStatus status) { this.status = status; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public String getLastVerificationError() { return lastVerificationError; }
    public void setLastVerificationError(String lastVerificationError) { this.lastVerificationError = lastVerificationError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public long getRowVersion() { return rowVersion; }
}

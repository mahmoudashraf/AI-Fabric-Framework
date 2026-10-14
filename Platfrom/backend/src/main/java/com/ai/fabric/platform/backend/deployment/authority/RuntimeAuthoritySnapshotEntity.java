package com.ai.fabric.platform.backend.deployment.authority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "platform_runtime_authority_snapshots")
@Getter
@Setter
public class RuntimeAuthoritySnapshotEntity {
    @Id @Column(length = 64, updatable = false) private String id;
    @Column(nullable = false, length = 64, updatable = false) private String deploymentId;
    @Column(nullable = false, length = 64, updatable = false) private String targetProfileId;
    @Column(nullable = false, length = 64, updatable = false) private String versionId;
    @Column(length = 64, updatable = false) private String releaseId;
    @Column(nullable = false, length = 64, updatable = false) private String fingerprint;
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false) private String bindingJson;
    @Column(nullable = false, updatable = false) private Instant createdAt;
}

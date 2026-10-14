package com.ai.fabric.platform.backend.deployment.authority;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "platform_runtime_target_authorities")
@Getter
@Setter
@JsonIgnoreType
public class RuntimeAuthorityEntity {
    @Id @Column(length = 64, updatable = false) private String id;
    @Column(nullable = false, length = 64, updatable = false) private String deploymentId;
    @Column(nullable = false, length = 64, updatable = false) private String targetProfileId;
    @Column(nullable = false, length = 64, updatable = false) private String customerId;
    @Column(nullable = false, length = 64, updatable = false) private String tenantId;
    @Column(nullable = false) private long generation;
    @Column(nullable = false, length = 16) private String state;
    @Column(nullable = false, columnDefinition = "TEXT") private String secretRevisionsJson;
    // Internal drift detection only. Never part of snapshots, projections, fingerprints or audit.
    @JsonIgnore
    @Column(nullable = false, columnDefinition = "TEXT") private String secretIntegrityJson;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}

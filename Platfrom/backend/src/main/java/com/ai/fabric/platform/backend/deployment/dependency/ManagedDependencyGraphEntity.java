package com.ai.fabric.platform.backend.deployment.dependency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "deployment_managed_dependency_graphs")
@Getter
@Setter
public class ManagedDependencyGraphEntity {
    @Id private String id;
    @Column(nullable = false) private String deploymentId;
    @Column(nullable = false) private String targetProfileId;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false) private String tenantId;
    @Column(nullable = false) private String versionId;
    @Column(name = "release_id", length = 64) private String releaseId;
    @Column(nullable = false) private String specRef;
    @Column(nullable = false) private String specFingerprint;
    @Column(nullable = false) private String bindingFingerprint;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String step;
    @Column private String failureCode;
    @Column private String reservationRef;
    @Column private String workerToken;
    @Column(nullable = false) private long revision;
    @Column(nullable = false) private int attempt;
    @Column(nullable = false, columnDefinition = "TEXT") private String specJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String stateJson;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}

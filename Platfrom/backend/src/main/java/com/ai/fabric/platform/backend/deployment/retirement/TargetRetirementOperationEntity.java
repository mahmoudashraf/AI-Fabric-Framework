package com.ai.fabric.platform.backend.deployment.retirement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Entity
@Table(name = "platform_target_retirement_operations")
@Getter
@Setter
public class TargetRetirementOperationEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 64) private String customerId;
    @Column(nullable = false, length = 64) private String deploymentId;
    @Column(nullable = false, length = 64) private String targetProfileId;
    @Column(nullable = false, length = 128) private String operationKey;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false, length = 255) private String requestedByActorId;
    @Column(nullable = false, length = 64) private String requestedByRole;
    @Column(nullable = false, columnDefinition = "TEXT") private String requestJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64) private State state;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64) private Step step;
    @Column(nullable = false) private long fenceEpoch;
    @Column(nullable = false) private Instant drainDeadline;
    @Column(nullable = false) private int attempt;
    private Instant nextRetryAt;
    @Column(length = 64) private String errorCode;
    @Column(length = 64) private String workerToken;
    private Instant leaseExpiresAt;
    @Column(length = 255) private String inFlightEffect;
    @Column(nullable = false) private boolean destructiveStarted;
    @Column(nullable = false) private Instant createdAt;
    private Instant startedAt;
    private Instant stepStartedAt;
    private Instant completedAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long rowVersion;
}

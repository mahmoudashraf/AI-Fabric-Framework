package com.ai.fabric.platform.backend.deployment.retirement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "platform_target_retirement_gates")
@Getter
@Setter
public class TargetRetirementGateEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 64) private String deploymentId;
    @Column(nullable = false, length = 64) private String targetProfileId;
    @Column(nullable = false, length = 64) private String state;
    @Column(nullable = false) private long epoch;
    @Column(length = 64) private String operationId;
    @Column(nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long rowVersion;
}

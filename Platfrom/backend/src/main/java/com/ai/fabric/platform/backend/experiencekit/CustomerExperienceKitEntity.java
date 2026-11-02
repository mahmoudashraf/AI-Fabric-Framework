package com.ai.fabric.platform.backend.experiencekit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "platform_customer_experience_kits")
public class CustomerExperienceKitEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 64) private String customerId;
    @Column(nullable = false, length = 64) private String kitCode;
    @Column(nullable = false, length = 32) private String kitVersion;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 64) private String deploymentId;
    @Column(nullable = false, length = 128) private String consumerId;
    @Column(length = 64) private String installationEntityId;
    @Column(length = 64) private String embeddedInstallationEntityId;
    @Column(nullable = false, length = 16) private String deliveryMode;
    @Column(nullable = false, length = 64) private String offerId;
    @Column(nullable = false, length = 64) private String targetProfileId;
    @Column(nullable = false) private boolean humanSupportEnabled;
    @Column(nullable = false, length = 128) private String operationKey;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long revision;
}

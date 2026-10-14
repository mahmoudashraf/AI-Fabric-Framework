package com.ai.fabric.platform.backend.customerselfservice.entity;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "platform_customer_placement_evidence")
public class CustomerPlacementEvidenceEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 64) private String targetProfileId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private TargetIntent intent;
    @Column(nullable = false, length = 64) private String profileFingerprint;
    @Column(nullable = false, length = 255) private String serverUuid;
    @Column(nullable = false, length = 255) private String serverAddress;
    @Column(nullable = false, length = 255) private String destinationUuid;
    @Column(nullable = false) private Instant checkedAt;
    @Column(nullable = false) private Instant expiresAt;
}

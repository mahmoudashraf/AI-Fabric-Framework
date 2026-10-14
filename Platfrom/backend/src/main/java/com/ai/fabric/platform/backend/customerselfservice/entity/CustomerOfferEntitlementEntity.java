package com.ai.fabric.platform.backend.customerselfservice.entity;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "platform_customer_offer_entitlements")
public class CustomerOfferEntitlementEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 64, updatable = false) private String customerId;
    @Column(nullable = false, length = 64, updatable = false) private String offerId;
    @Column(nullable = false, length = 64) private String ownerUserId;
    @Column(nullable = false, length = 255) private String invitationReference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EntitlementStatus status;
    @Column(nullable = false) private long revision;
    @Version @Column(nullable = false) private long rowVersion;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}

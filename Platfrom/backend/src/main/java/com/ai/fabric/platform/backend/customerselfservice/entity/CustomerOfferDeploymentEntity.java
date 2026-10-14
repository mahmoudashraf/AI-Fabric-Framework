package com.ai.fabric.platform.backend.customerselfservice.entity;

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
@Table(name = "platform_customer_offer_deployments")
public class CustomerOfferDeploymentEntity {
    @Id @Column(length = 64) private String deploymentId;
    @Column(nullable = false, length = 64) private String customerId;
    @Column(nullable = false, length = 64) private String tenantId;
    @Column(nullable = false, length = 64) private String offerId;
    @Column(nullable = false, length = 64) private String offerFingerprint;
    @Column(nullable = false) private long authorizationEntitlementRevision;
    @Column(nullable = false, length = 128) private String operationKey;
    @Column(nullable = false) private Instant createdAt;
}

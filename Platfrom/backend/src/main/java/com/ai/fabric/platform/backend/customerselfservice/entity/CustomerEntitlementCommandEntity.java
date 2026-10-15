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
@Table(name = "platform_customer_entitlement_commands")
public class CustomerEntitlementCommandEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 64) private String entitlementId;
    @Column(nullable = false, length = 128) private String operationKey;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false, length = 32) private String commandType;
    @Column(nullable = false, length = 255) private String actorId;
    @Column(nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Column(nullable = false) private Instant createdAt;
}

package com.ai.fabric.platform.backend.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "platform_customer_identity_links")
@Getter
@Setter
public class PlatformCustomerIdentityLinkEntity {
    @Id @Column(length = 64, updatable = false) private String id;
    @Column(nullable = false, length = 512, updatable = false) private String issuer;
    @Column(nullable = false, length = 255, updatable = false) private String subject;
    @Column(name = "platform_user_id", nullable = false, length = 64, updatable = false) private String platformUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;
}

package com.ai.fabric.platform.backend.security.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "platform_customer_staff_access", uniqueConstraints =
    @UniqueConstraint(name = "uq_customer_staff_access_member", columnNames = {"customer_id", "user_id"}))
@Getter
@Setter
public class CustomerStaffAccessEntity {
    @Id @Column(length = 64, updatable = false) private String id;
    @Column(name = "customer_id", nullable = false, length = 64, updatable = false) private String customerId;
    @Column(name = "user_id", nullable = false, length = 64, updatable = false) private String userId;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "granted_by", nullable = false, length = 255) private String grantedBy;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt;
    @Column(name = "revoked_by", length = 255) private String revokedBy;
    @Column(name = "revoked_at") private Instant revokedAt;
}

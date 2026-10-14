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
@Table(name = "platform_customer_offers")
public class CustomerOfferEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false) private String definitionJson;
    @Column(nullable = false, length = 64, updatable = false) private String fingerprint;
    @Column(nullable = false, length = 255, updatable = false) private String reviewReference;
    @Column(nullable = false, length = 255, updatable = false) private String reviewedBy;
    @Column(nullable = false, updatable = false) private Instant reviewedAt;
}

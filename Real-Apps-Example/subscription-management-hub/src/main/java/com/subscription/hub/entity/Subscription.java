package com.subscription.hub.entity;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIContext;
import com.ai.infrastructure.indexing.IndexingStrategy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@AICapable(
    entityType = "subscription",
    autoEmbedding = false,
    indexable = true,
    indexingStrategy = com.ai.infrastructure.indexing.IndexingStrategy.ASYNC
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @AIContext(contextKey = "status")
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    
    @AIContext(contextKey = "startDate", dataType = "datetime")
    @Column(nullable = false)
    private LocalDateTime startDate;
    
    @AIContext(contextKey = "endDate", dataType = "datetime")
    private LocalDateTime endDate;
    
    @AIContext(contextKey = "billingCycle")
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;  // MONTHLY, ANNUAL
    
    @AIContext(contextKey = "churnRisk", dataType = "decimal")
    private Double churnRiskScore;  // 0.0-1.0 from Behavior Analysis
    
    @AIContext(contextKey = "lastActivityDate", dataType = "datetime")
    private LocalDateTime lastActivityDate;
    
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_address_id")
    private Address billingAddress;
    
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    private Address shippingAddress;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;
    
    public enum SubscriptionStatus {
        ACTIVE, CANCELLED, PAST_DUE, EXPIRED
    }
    
    public enum BillingCycle {
        MONTHLY, ANNUAL
    }
}

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
    indexingStrategy = IndexingStrategy.ASYNC
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
    
    @Column(nullable = false)
    private UUID planId;
    
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
    
    // Note: plan relationship removed to avoid duplicate column mapping with planId
    // Use planId to fetch plan via repository if needed
    
    // Manual getters/setters for Lombok compatibility
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public BillingCycle getBillingCycle() { return billingCycle; }
    public void setBillingCycle(BillingCycle billingCycle) { this.billingCycle = billingCycle; }
    public Double getChurnRiskScore() { return churnRiskScore; }
    public void setChurnRiskScore(Double churnRiskScore) { this.churnRiskScore = churnRiskScore; }
    public LocalDateTime getLastActivityDate() { return lastActivityDate; }
    public void setLastActivityDate(LocalDateTime lastActivityDate) { this.lastActivityDate = lastActivityDate; }
    public Address getBillingAddress() { return billingAddress; }
    public void setBillingAddress(Address billingAddress) { this.billingAddress = billingAddress; }
    public Address getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }
    
    public enum SubscriptionStatus {
        ACTIVE, CANCELLED, PAST_DUE, EXPIRED
    }
    
    public enum BillingCycle {
        MONTHLY, ANNUAL
    }
}

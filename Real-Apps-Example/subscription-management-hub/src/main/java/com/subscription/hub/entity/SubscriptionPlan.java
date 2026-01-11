package com.subscription.hub.entity;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIContext;
import com.ai.infrastructure.annotation.AISearchable;
import com.ai.infrastructure.indexing.IndexingStrategy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
@AICapable(
    entityType = "subscription-plan",
    autoEmbedding = true,
    indexable = true,
    enableRecommendations = true,
    indexingStrategy = IndexingStrategy.ASYNC
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @AISearchable(weight = 2.0)
    @Column(nullable = false, unique = true)
    private String name;  // "Pro Plan", "Enterprise Plan"
    
    @AISearchable(weight = 1.5)
    @Column(columnDefinition = "TEXT")
    private String description;  // Full plan description
    
    @AIContext(contextKey = "price", dataType = "decimal")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;
    
    @AIContext(contextKey = "annualPrice", dataType = "decimal")
    @Column(precision = 10, scale = 2)
    private BigDecimal annualPrice;
    
    @AIContext(contextKey = "tier")
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlanTier tier;  // BASIC, PRO, ENTERPRISE
    
    @AIContext(contextKey = "features")
    @ElementCollection
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "feature")
    private List<String> features;  // ["Unlimited storage", "Priority support", ...]
    
    @AIContext(contextKey = "maxUsers")
    private Integer maxUsers;
    
    @AIContext(contextKey = "storageGB")
    private Integer storageGB;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    // Manual getters/setters for Lombok compatibility
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public BigDecimal getAnnualPrice() { return annualPrice; }
    public void setAnnualPrice(BigDecimal annualPrice) { this.annualPrice = annualPrice; }
    public PlanTier getTier() { return tier; }
    public void setTier(PlanTier tier) { this.tier = tier; }
    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public Integer getStorageGB() { return storageGB; }
    public void setStorageGB(Integer storageGB) { this.storageGB = storageGB; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public enum PlanTier {
        BASIC, PRO, ENTERPRISE
    }
}

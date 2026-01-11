package com.subscription.simple.entity;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIContext;
import com.ai.infrastructure.annotation.AISearchable;
import com.ai.infrastructure.indexing.IndexingStrategy;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plans")
@AICapable(
    entityType = "plan",
    autoEmbedding = true,
    indexable = true,
    enableRecommendations = true,
    indexingStrategy = IndexingStrategy.ASYNC
)
@Data
public class Plan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @AISearchable(weight = 2.0)
    @Column(nullable = false)
    private String name;
    
    @AISearchable(weight = 1.5)
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @AIContext(contextKey = "price", dataType = "decimal")
    @Column(nullable = false)
    private BigDecimal price;
    
    @AIContext(contextKey = "tier")
    @Column(nullable = false)
    private String tier;
    
    @Column(nullable = false)
    private Boolean active = true;
}

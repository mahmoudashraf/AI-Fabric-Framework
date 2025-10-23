package com.ai.infrastructure.entity;

import lombok.Builder;
import lombok.Data;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;

/**
 * AI Searchable Entity
 * 
 * Represents an entity that has been processed for AI search capabilities.
 * Contains embeddings and metadata for semantic search.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@Entity
@Table(name = "ai_searchable_entities")
public class AISearchableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "entity_id", nullable = false)
    private String entityId;
    
    @Column(name = "searchable_content", columnDefinition = "TEXT")
    private String searchableContent;
    
    @ElementCollection
    @CollectionTable(name = "ai_embeddings", joinColumns = @JoinColumn(name = "entity_id"))
    @Column(name = "embedding_value")
    private List<Double> embeddings;
    
    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;
    
    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;
    
    // Timestamps
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
}

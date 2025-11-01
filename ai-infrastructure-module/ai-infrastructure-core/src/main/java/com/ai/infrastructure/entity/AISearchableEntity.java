package com.ai.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI Searchable Entity
 * 
 * Represents an entity that has been processed for AI search capabilities.
 * Contains metadata and references to vectors stored in external vector database.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    
    /**
     * Vector ID reference in the external vector database
     */
    @Column(name = "vector_id", length = 255)
    private String vectorId;
    
    /**
     * Timestamp when the vector was last updated in the vector database
     */
    @Column(name = "vector_updated_at")
    private LocalDateTime vectorUpdatedAt;
    
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

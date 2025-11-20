package com.ai.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(
    name = "ai_searchable_entities",
    indexes = {
        @Index(name = "idx_ai_searchable_vector_id", columnList = "vector_id"),
        @Index(name = "idx_ai_searchable_vector_updated", columnList = "vector_updated_at"),
        @Index(name = "idx_ai_searchable_entity_type", columnList = "entity_type"),
        @Index(name = "idx_ai_searchable_created_at", columnList = "created_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_ai_searchable_entity_type_id",
            columnNames = {"entity_type", "entity_id"}
        )
    }
)
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
    
    @JdbcTypeCode(SqlTypes.JSON)
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

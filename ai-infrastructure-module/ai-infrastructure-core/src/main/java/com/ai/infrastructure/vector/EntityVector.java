package com.ai.infrastructure.vector;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Entity Vector
 * 
 * Represents a vector associated with a specific entity.
 * Used for batch operations and entity-specific vector management.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityVector {
    
    /**
     * The type of entity (e.g., "product", "user", "order")
     */
    private String entityType;
    
    /**
     * The unique identifier of the entity
     */
    private String entityId;
    
    /**
     * The searchable content associated with the entity
     */
    private String content;
    
    /**
     * The embedding vector (typically 1536 dimensions for OpenAI)
     */
    private List<Double> vector;
    
    /**
     * Additional metadata for the entity
     */
    private Map<String, Object> metadata;
    
    /**
     * Check if this entity vector is valid
     * 
     * @return true if all required fields are present
     */
    public boolean isValid() {
        return entityType != null && !entityType.trim().isEmpty() &&
               entityId != null && !entityId.trim().isEmpty() &&
               vector != null && !vector.isEmpty();
    }
    
    /**
     * Get the vector dimensions
     * 
     * @return Number of dimensions in the vector
     */
    public int getDimensions() {
        return vector != null ? vector.size() : 0;
    }
    
    /**
     * Get a unique identifier for this entity vector
     * 
     * @return Unique identifier in format "entityType:entityId"
     */
    public String getUniqueId() {
        return entityType + ":" + entityId;
    }
}
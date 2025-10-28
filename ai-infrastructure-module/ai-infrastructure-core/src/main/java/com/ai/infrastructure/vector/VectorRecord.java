package com.ai.infrastructure.vector;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Vector Record
 * 
 * Represents a vector with its metadata and timestamps.
 * Used for storing and retrieving vectors from the vector database.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorRecord {
    
    /**
     * Unique identifier for the vector
     */
    private String id;
    
    /**
     * The embedding vector (typically 1536 dimensions for OpenAI)
     */
    private List<Double> vector;
    
    /**
     * Metadata associated with the vector for filtering and context
     */
    private Map<String, Object> metadata;
    
    /**
     * When the vector was created
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * When the vector was last updated
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    /**
     * Get the vector dimensions
     * 
     * @return Number of dimensions in the vector
     */
    public int getDimensions() {
        return vector != null ? vector.size() : 0;
    }
    
    /**
     * Check if the vector is valid (not null and has dimensions)
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return id != null && !id.trim().isEmpty() && 
               vector != null && !vector.isEmpty();
    }
    
    /**
     * Get metadata value by key with type casting
     * 
     * @param key The metadata key
     * @param type The expected type
     * @param <T> The type parameter
     * @return The metadata value cast to the specified type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        
        Object value = metadata.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        return null;
    }
    
    /**
     * Get metadata value as string
     * 
     * @param key The metadata key
     * @return The metadata value as string, or null if not found
     */
    public String getMetadataString(String key) {
        Object value = metadata != null ? metadata.get(key) : null;
        return value != null ? value.toString() : null;
    }
    
    /**
     * Update the updatedAt timestamp
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
package com.ai.infrastructure.vector;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Vector Search Result
 * 
 * Represents a search result from vector similarity search.
 * Contains the vector record and similarity score.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult {
    
    /**
     * The vector record that matched the search
     */
    private VectorRecord record;
    
    /**
     * Similarity score (0.0 to 1.0, where 1.0 is identical)
     */
    private double similarity;
    
    /**
     * Distance score (lower is more similar)
     */
    private double distance;
    
    /**
     * Additional search metadata (e.g., search time, algorithm used)
     */
    private Map<String, Object> searchMetadata;
    
    /**
     * Get the vector ID for convenience
     * 
     * @return The vector ID
     */
    public String getId() {
        return record != null ? record.getId() : null;
    }
    
    /**
     * Get the vector for convenience
     * 
     * @return The embedding vector
     */
    public java.util.List<Double> getVector() {
        return record != null ? record.getVector() : null;
    }
    
    /**
     * Get the metadata for convenience
     * 
     * @return The vector metadata
     */
    public Map<String, Object> getMetadata() {
        return record != null ? record.getMetadata() : null;
    }
    
    /**
     * Check if this is a high-quality match
     * 
     * @param threshold The similarity threshold
     * @return true if similarity is above threshold
     */
    public boolean isHighQualityMatch(double threshold) {
        return similarity >= threshold;
    }
    
    /**
     * Get similarity as percentage string
     * 
     * @return Similarity as percentage (e.g., "87.5%")
     */
    public String getSimilarityPercentage() {
        return String.format("%.1f%%", similarity * 100);
    }
    
    /**
     * Create a search result from vector record and similarity
     * 
     * @param record The vector record
     * @param similarity The similarity score
     * @return New VectorSearchResult
     */
    public static VectorSearchResult of(VectorRecord record, double similarity) {
        return VectorSearchResult.builder()
            .record(record)
            .similarity(similarity)
            .distance(1.0 - similarity)  // Convert similarity to distance
            .build();
    }
    
    /**
     * Create a search result with distance score
     * 
     * @param record The vector record
     * @param distance The distance score
     * @return New VectorSearchResult
     */
    public static VectorSearchResult ofDistance(VectorRecord record, double distance) {
        return VectorSearchResult.builder()
            .record(record)
            .similarity(Math.max(0.0, 1.0 - distance))  // Convert distance to similarity
            .distance(distance)
            .build();
    }
}
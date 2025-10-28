package com.ai.infrastructure.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-Memory Vector Database
 * 
 * Fast, in-memory vector database implementation for development and testing.
 * Provides efficient vector similarity search with cosine similarity.
 * 
 * Features:
 * - Thread-safe operations
 * - Fast cosine similarity search
 * - Metadata filtering
 * - Batch operations
 * - Performance metrics
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorDatabase implements VectorDatabase {
    
    private final Map<String, VectorRecord> vectors = new ConcurrentHashMap<>();
    private final AtomicLong searchCount = new AtomicLong(0);
    private final AtomicLong totalSearchTime = new AtomicLong(0);
    
    @Override
    public void store(String id, List<Double> vector, Map<String, Object> metadata) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Vector ID cannot be null or empty");
        }
        
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }
        
        VectorRecord record = VectorRecord.builder()
            .id(id)
            .vector(new ArrayList<>(vector))  // Defensive copy
            .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
            .build();
        
        vectors.put(id, record);
        
        log.debug("Stored vector {} with {} dimensions", id, vector.size());
    }
    
    @Override
    public void batchStore(List<VectorRecord> vectorRecords) {
        if (vectorRecords == null || vectorRecords.isEmpty()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        for (VectorRecord record : vectorRecords) {
            if (record.isValid()) {
                vectors.put(record.getId(), record);
            } else {
                log.warn("Skipping invalid vector record: {}", record.getId());
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.debug("Batch stored {} vectors in {}ms", vectorRecords.size(), duration);
    }
    
    @Override
    public List<VectorSearchResult> search(List<Double> queryVector, int limit, double threshold) {
        return searchWithFilter(queryVector, null, limit, threshold);
    }
    
    @Override
    public List<VectorSearchResult> searchWithFilter(List<Double> queryVector, 
                                                   Map<String, Object> filter, 
                                                   int limit, 
                                                   double threshold) {
        if (queryVector == null || queryVector.isEmpty()) {
            throw new IllegalArgumentException("Query vector cannot be null or empty");
        }
        
        long startTime = System.currentTimeMillis();
        searchCount.incrementAndGet();
        
        List<VectorSearchResult> results = vectors.values().stream()
            .filter(record -> matchesFilter(record, filter))
            .map(record -> {
                double similarity = calculateCosineSimilarity(queryVector, record.getVector());
                return VectorSearchResult.of(record, similarity);
            })
            .filter(result -> result.getSimilarity() >= threshold)
            .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
            .limit(limit)
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        totalSearchTime.addAndGet(duration);
        
        log.debug("Found {} results in {}ms (threshold: {}, limit: {})", 
                 results.size(), duration, threshold, limit);
        
        return results;
    }
    
    @Override
    public Optional<VectorRecord> get(String id) {
        VectorRecord record = vectors.get(id);
        return Optional.ofNullable(record);
    }
    
    @Override
    public boolean delete(String id) {
        VectorRecord removed = vectors.remove(id);
        boolean deleted = removed != null;
        
        if (deleted) {
            log.debug("Deleted vector {}", id);
        }
        
        return deleted;
    }
    
    @Override
    public int batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        
        int deletedCount = 0;
        for (String id : ids) {
            if (vectors.remove(id) != null) {
                deletedCount++;
            }
        }
        
        log.debug("Batch deleted {} vectors", deletedCount);
        return deletedCount;
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("type", getType());
        stats.put("vectorCount", vectors.size());
        stats.put("searchCount", searchCount.get());
        stats.put("totalSearchTimeMs", totalSearchTime.get());
        
        if (searchCount.get() > 0) {
            stats.put("averageSearchTimeMs", totalSearchTime.get() / (double) searchCount.get());
        } else {
            stats.put("averageSearchTimeMs", 0.0);
        }
        
        // Calculate memory usage estimate
        long memoryUsage = vectors.values().stream()
            .mapToLong(record -> estimateRecordSize(record))
            .sum();
        stats.put("estimatedMemoryUsageBytes", memoryUsage);
        stats.put("estimatedMemoryUsageMB", memoryUsage / (1024.0 * 1024.0));
        
        // Dimension statistics
        OptionalInt dimensions = vectors.values().stream()
            .mapToInt(VectorRecord::getDimensions)
            .findFirst();
        if (dimensions.isPresent()) {
            stats.put("vectorDimensions", dimensions.getAsInt());
        }
        
        return stats;
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Simple health check - try to access the vectors map
            int size = vectors.size();
            log.debug("Health check passed - {} vectors stored", size);
            return true;
        } catch (Exception e) {
            log.error("Health check failed", e);
            return false;
        }
    }
    
    @Override
    public void clear() {
        int count = vectors.size();
        vectors.clear();
        searchCount.set(0);
        totalSearchTime.set(0);
        
        log.info("Cleared {} vectors from in-memory database", count);
    }
    
    @Override
    public String getType() {
        return "in-memory";
    }
    
    /**
     * Calculate cosine similarity between two vectors
     * 
     * @param vectorA First vector
     * @param vectorB Second vector
     * @return Cosine similarity (0.0 to 1.0)
     */
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("Vectors must have the same dimensions");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.size(); i++) {
            double a = vectorA.get(i);
            double b = vectorB.get(i);
            
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        
        // Ensure similarity is between 0 and 1
        return Math.max(0.0, Math.min(1.0, similarity));
    }
    
    /**
     * Check if a vector record matches the given filter
     * 
     * @param record The vector record to check
     * @param filter The filter conditions (null means no filter)
     * @return true if the record matches the filter
     */
    private boolean matchesFilter(VectorRecord record, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        
        Map<String, Object> metadata = record.getMetadata();
        if (metadata == null) {
            return false;
        }
        
        for (Map.Entry<String, Object> filterEntry : filter.entrySet()) {
            String key = filterEntry.getKey();
            Object expectedValue = filterEntry.getValue();
            Object actualValue = metadata.get(key);
            
            if (!Objects.equals(expectedValue, actualValue)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Estimate memory usage of a vector record
     * 
     * @param record The vector record
     * @return Estimated memory usage in bytes
     */
    private long estimateRecordSize(VectorRecord record) {
        long size = 0;
        
        // ID string
        if (record.getId() != null) {
            size += record.getId().length() * 2; // UTF-16
        }
        
        // Vector (Double objects)
        if (record.getVector() != null) {
            size += record.getVector().size() * 8; // 8 bytes per Double
        }
        
        // Metadata (rough estimate)
        if (record.getMetadata() != null) {
            size += record.getMetadata().size() * 50; // Rough estimate per entry
        }
        
        // Object overhead
        size += 64; // Rough estimate for object overhead
        
        return size;
    }
}
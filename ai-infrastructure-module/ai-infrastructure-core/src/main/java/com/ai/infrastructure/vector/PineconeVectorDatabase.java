package com.ai.infrastructure.vector;

import com.ai.infrastructure.config.VectorDatabaseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Pinecone Vector Database Implementation
 * 
 * Cloud-based vector database implementation using Pinecone service.
 * Provides scalable, managed vector storage and similarity search.
 * 
 * Features:
 * - Cloud-managed service (no infrastructure)
 * - Handles millions of vectors
 * - Advanced indexing algorithms
 * - Metadata filtering
 * - Automatic scaling
 * 
 * Note: This is a mock implementation for demonstration.
 * In production, integrate with actual Pinecone SDK.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "pinecone")
public class PineconeVectorDatabase implements VectorDatabase {
    
    private final VectorDatabaseConfig config;
    
    // Mock storage for demonstration (in production, use Pinecone SDK)
    private final Map<String, VectorRecord> vectors = new HashMap<>();
    private final AtomicLong searchCount = new AtomicLong(0);
    private final AtomicLong totalSearchTime = new AtomicLong(0);
    
    @Override
    public void store(String id, List<Double> vector, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector in Pinecone: {}", id);
            
            // In production, this would call Pinecone API:
            // pineconeClient.upsert(indexName, Arrays.asList(
            //     new UpsertRequest(id, vector, metadata)
            // ));
            
            // Mock implementation
            VectorRecord record = VectorRecord.builder()
                .id(id)
                .vector(new ArrayList<>(vector))
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .createdAt(java.time.LocalDateTime.now())
                .build();
            
            vectors.put(id, record);
            
            log.debug("Successfully stored vector in Pinecone: {}", id);
            
        } catch (Exception e) {
            log.error("Error storing vector in Pinecone: {}", id, e);
            throw new RuntimeException("Failed to store vector in Pinecone", e);
        }
    }
    
    @Override
    public void batchStore(List<VectorRecord> vectorRecords) {
        try {
            log.debug("Batch storing {} vectors in Pinecone", vectorRecords.size());
            
            // In production, this would use Pinecone batch upsert:
            // List<UpsertRequest> requests = vectorRecords.stream()
            //     .map(record -> new UpsertRequest(record.getId(), record.getVector(), record.getMetadata()))
            //     .collect(Collectors.toList());
            // pineconeClient.upsert(indexName, requests);
            
            // Mock implementation
            for (VectorRecord record : vectorRecords) {
                if (record.isValid()) {
                    store(record.getId(), record.getVector(), record.getMetadata());
                } else {
                    log.warn("Skipping invalid vector record: {}", record.getId());
                }
            }
            
            log.debug("Batch stored {} vectors in Pinecone", vectorRecords.size());
            
        } catch (Exception e) {
            log.error("Error batch storing vectors in Pinecone", e);
            throw new RuntimeException("Failed to batch store vectors in Pinecone", e);
        }
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
        try {
            long startTime = System.currentTimeMillis();
            searchCount.incrementAndGet();
            
            log.debug("Searching vectors in Pinecone with filter: {}", filter);
            
            // In production, this would call Pinecone query API:
            // QueryRequest request = QueryRequest.builder()
            //     .vector(queryVector)
            //     .topK(limit)
            //     .filter(filter)
            //     .includeMetadata(true)
            //     .build();
            // QueryResponse response = pineconeClient.query(indexName, request);
            
            // Mock implementation with similarity calculation
            List<VectorSearchResult> results = vectors.values().stream()
                .filter(record -> matchesFilter(record, filter))
                .map(record -> {
                    double similarity = calculateCosineSimilarity(queryVector, record.getVector());
                    return VectorSearchResult.builder()
                        .record(record)
                        .similarity(similarity)
                        .distance(1.0 - similarity)
                        .searchMetadata(Map.of("pineconeScore", similarity))
                        .build();
                })
                .filter(result -> result.getSimilarity() >= threshold)
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .limit(limit)
                .collect(Collectors.toList());
            
            long processingTime = System.currentTimeMillis() - startTime;
            totalSearchTime.addAndGet(processingTime);
            
            log.debug("Pinecone search completed: {} results in {}ms", results.size(), processingTime);
            
            return results;
            
        } catch (Exception e) {
            log.error("Error searching vectors in Pinecone", e);
            throw new RuntimeException("Failed to search vectors in Pinecone", e);
        }
    }
    
    @Override
    public Optional<VectorRecord> get(String id) {
        try {
            log.debug("Getting vector from Pinecone: {}", id);
            
            // In production, this would call Pinecone fetch API:
            // FetchResponse response = pineconeClient.fetch(indexName, Arrays.asList(id));
            // return response.getVectors().get(id);
            
            // Mock implementation
            VectorRecord record = vectors.get(id);
            return Optional.ofNullable(record);
            
        } catch (Exception e) {
            log.error("Error getting vector from Pinecone: {}", id, e);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean delete(String id) {
        try {
            log.debug("Deleting vector from Pinecone: {}", id);
            
            // In production, this would call Pinecone delete API:
            // pineconeClient.delete(indexName, Arrays.asList(id));
            
            // Mock implementation
            VectorRecord removed = vectors.remove(id);
            boolean deleted = removed != null;
            
            if (deleted) {
                log.debug("Successfully deleted vector from Pinecone: {}", id);
            } else {
                log.warn("Vector not found in Pinecone: {}", id);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("Error deleting vector from Pinecone: {}", id, e);
            return false;
        }
    }
    
    @Override
    public int batchDelete(List<String> ids) {
        try {
            log.debug("Batch deleting {} vectors from Pinecone", ids.size());
            
            // In production, this would use Pinecone batch delete:
            // pineconeClient.delete(indexName, ids);
            
            // Mock implementation
            int deletedCount = 0;
            for (String id : ids) {
                if (delete(id)) {
                    deletedCount++;
                }
            }
            
            log.debug("Batch deleted {} vectors from Pinecone", deletedCount);
            return deletedCount;
            
        } catch (Exception e) {
            log.error("Error batch deleting vectors from Pinecone", e);
            return 0;
        }
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // In production, this would call Pinecone describe_index_stats API:
            // IndexStats indexStats = pineconeClient.describeIndexStats(indexName);
            
            // Mock implementation
            stats.put("type", getType());
            stats.put("vectorCount", vectors.size());
            stats.put("searchCount", searchCount.get());
            stats.put("totalSearchTimeMs", totalSearchTime.get());
            
            if (searchCount.get() > 0) {
                stats.put("averageSearchTimeMs", totalSearchTime.get() / (double) searchCount.get());
            } else {
                stats.put("averageSearchTimeMs", 0.0);
            }
            
            // Pinecone-specific stats
            stats.put("indexName", config.getPinecone().getIndexName());
            stats.put("dimensions", config.getPinecone().getDimensions());
            stats.put("metric", config.getPinecone().getMetric());
            stats.put("pods", config.getPinecone().getPods());
            
            // Calculate memory usage estimate
            long memoryUsage = vectors.values().stream()
                .mapToLong(this::estimateRecordSize)
                .sum();
            stats.put("estimatedMemoryUsageBytes", memoryUsage);
            stats.put("estimatedMemoryUsageMB", memoryUsage / (1024.0 * 1024.0));
            
        } catch (Exception e) {
            log.error("Error getting Pinecone statistics", e);
            stats.put("error", "Failed to get statistics");
        }
        
        return stats;
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // In production, this would ping Pinecone service:
            // return pineconeClient.describeIndex(indexName) != null;
            
            // Mock implementation
            log.debug("Pinecone health check passed");
            return true;
            
        } catch (Exception e) {
            log.error("Pinecone health check failed", e);
            return false;
        }
    }
    
    @Override
    public void clear() {
        try {
            log.info("Clearing all vectors from Pinecone");
            
            // In production, this would delete and recreate the index:
            // pineconeClient.deleteIndex(indexName);
            // pineconeClient.createIndex(indexName, dimensions, metric);
            
            // Mock implementation
            int count = vectors.size();
            vectors.clear();
            searchCount.set(0);
            totalSearchTime.set(0);
            
            log.info("Successfully cleared {} vectors from Pinecone", count);
            
        } catch (Exception e) {
            log.error("Error clearing vectors from Pinecone", e);
            throw new RuntimeException("Failed to clear vectors from Pinecone", e);
        }
    }
    
    @Override
    public String getType() {
        return "pinecone";
    }
    
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
    
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            return 0.0;
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
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
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
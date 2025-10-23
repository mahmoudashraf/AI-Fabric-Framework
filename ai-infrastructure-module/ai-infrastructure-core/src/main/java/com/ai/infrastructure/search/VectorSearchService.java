package com.ai.infrastructure.search;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Advanced vector search service with optimization and caching
 * 
 * This service provides sophisticated vector search capabilities including
 * similarity search, ranking, filtering, and performance optimization.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {
    
    private final AIProviderConfig config;
    
    // In-memory vector store for demo purposes
    private final Map<String, List<Map<String, Object>>> vectorStore = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final AtomicLong totalSearches = new AtomicLong(0);
    private final AtomicLong totalSearchTime = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    /**
     * Perform advanced vector search with caching and optimization
     * 
     * @param queryVector the query vector
     * @param request the search request
     * @return search results with advanced ranking
     */
    @Cacheable(value = "vectorSearch", key = "#queryVector.hashCode() + '_' + #request.hashCode()")
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        try {
            log.debug("Performing advanced vector search for query: {}", request.getQuery());
            
            long startTime = System.currentTimeMillis();
            totalSearches.incrementAndGet();
            
            // Get entities to search
            String entityType = request.getEntityType();
            List<Map<String, Object>> entities = vectorStore.getOrDefault(entityType, new ArrayList<>());
            
            if (entities.isEmpty()) {
                log.debug("No entities found for type: {}", entityType);
                return createEmptyResponse(request, startTime);
            }
            
            // Perform advanced similarity search
            List<Map<String, Object>> scoredEntities = performAdvancedSearch(queryVector, entities, request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            totalSearchTime.addAndGet(processingTime);
            
            log.debug("Found {} results in {}ms", scoredEntities.size(), processingTime);
            
            return AISearchResponse.builder()
                .results(scoredEntities)
                .totalResults(scoredEntities.size())
                .maxScore(scoredEntities.isEmpty() ? 0.0 : (Double) scoredEntities.get(0).get("similarity"))
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .query(request.getQuery())
                .model(config.getOpenaiEmbeddingModel())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing advanced vector search", e);
            throw new AIServiceException("Failed to perform advanced vector search", e);
        }
    }
    
    /**
     * Perform hybrid search combining vector similarity and text matching
     * 
     * @param queryVector the query vector
     * @param queryText the original query text
     * @param request the search request
     * @return hybrid search results
     */
    public AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
        try {
            log.debug("Performing hybrid search for query: {}", queryText);
            
            long startTime = System.currentTimeMillis();
            
            // Get entities to search
            String entityType = request.getEntityType();
            List<Map<String, Object>> entities = vectorStore.getOrDefault(entityType, new ArrayList<>());
            
            if (entities.isEmpty()) {
                return createEmptyResponse(request, startTime);
            }
            
            // Perform vector similarity search
            List<Map<String, Object>> vectorResults = performAdvancedSearch(queryVector, entities, request);
            
            // Perform text matching search
            List<Map<String, Object>> textResults = performTextSearch(queryText, entities, request);
            
            // Combine and rank results
            List<Map<String, Object>> combinedResults = combineSearchResults(vectorResults, textResults, request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AISearchResponse.builder()
                .results(combinedResults)
                .totalResults(combinedResults.size())
                .maxScore(combinedResults.isEmpty() ? 0.0 : (Double) combinedResults.get(0).get("similarity"))
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .query(request.getQuery())
                .model(config.getOpenaiEmbeddingModel())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing hybrid search", e);
            throw new AIServiceException("Failed to perform hybrid search", e);
        }
    }
    
    /**
     * Perform semantic search with context awareness
     * 
     * @param queryVector the query vector
     * @param context the search context
     * @param request the search request
     * @return context-aware search results
     */
    public AISearchResponse contextualSearch(List<Double> queryVector, String context, AISearchRequest request) {
        try {
            log.debug("Performing contextual search with context: {}", context);
            
            long startTime = System.currentTimeMillis();
            
            // Get entities to search
            String entityType = request.getEntityType();
            List<Map<String, Object>> entities = vectorStore.getOrDefault(entityType, new ArrayList<>());
            
            if (entities.isEmpty()) {
                return createEmptyResponse(request, startTime);
            }
            
            // Perform context-aware search
            List<Map<String, Object>> scoredEntities = performContextualSearch(queryVector, context, entities, request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AISearchResponse.builder()
                .results(scoredEntities)
                .totalResults(scoredEntities.size())
                .maxScore(scoredEntities.isEmpty() ? 0.0 : (Double) scoredEntities.get(0).get("similarity"))
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .query(request.getQuery())
                .model(config.getOpenaiEmbeddingModel())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing contextual search", e);
            throw new AIServiceException("Failed to perform contextual search", e);
        }
    }
    
    /**
     * Store vector with metadata for advanced search
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content
     * @param embedding the vector embedding
     * @param metadata additional metadata
     */
    public void storeVector(String entityType, String entityId, String content, 
                           List<Double> embedding, Map<String, Object> metadata) {
        try {
            log.debug("Storing vector for entity {} of type {}", entityId, entityType);
            
            Map<String, Object> vector = new HashMap<>();
            vector.put("id", entityId);
            vector.put("content", content);
            vector.put("embedding", embedding);
            vector.put("entityType", entityType);
            vector.put("metadata", metadata != null ? metadata : new HashMap<>());
            vector.put("storedAt", System.currentTimeMillis());
            
            // Add searchable text fields
            vector.put("searchableText", content.toLowerCase());
            vector.put("keywords", extractKeywords(content));
            
            vectorStore.computeIfAbsent(entityType, k -> new ArrayList<>()).add(vector);
            
            log.debug("Successfully stored vector for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error storing vector", e);
            throw new AIServiceException("Failed to store vector", e);
        }
    }
    
    /**
     * Get search statistics and performance metrics
     * 
     * @return map of search statistics
     */
    public Map<String, Object> getSearchStatistics() {
        long totalSearchesCount = totalSearches.get();
        long totalSearchTimeMs = totalSearchTime.get();
        double avgSearchTime = totalSearchesCount > 0 ? (double) totalSearchTimeMs / totalSearchesCount : 0.0;
        
        return Map.of(
            "totalSearches", totalSearchesCount,
            "totalSearchTimeMs", totalSearchTimeMs,
            "averageSearchTimeMs", avgSearchTime,
            "cacheHits", cacheHits.get(),
            "cacheMisses", cacheMisses.get(),
            "totalVectors", vectorStore.values().stream().mapToInt(List::size).sum(),
            "entityTypes", vectorStore.keySet()
        );
    }
    
    /**
     * Perform advanced similarity search with ranking
     */
    private List<Map<String, Object>> performAdvancedSearch(List<Double> queryVector, 
                                                           List<Map<String, Object>> entities, 
                                                           AISearchRequest request) {
        return entities.stream()
            .map(entity -> {
                List<Double> entityVector = (List<Double>) entity.get("embedding");
                double similarity = calculateCosineSimilarity(queryVector, entityVector);
                
                // Apply additional ranking factors
                double rankingScore = calculateRankingScore(entity, similarity, request);
                
                Map<String, Object> scoredEntity = new HashMap<>(entity);
                scoredEntity.put("similarity", similarity);
                scoredEntity.put("rankingScore", rankingScore);
                scoredEntity.put("score", rankingScore); // Use ranking score as final score
                return scoredEntity;
            })
            .filter(entity -> (Double) entity.get("similarity") >= request.getThreshold())
            .sorted((a, b) -> Double.compare((Double) b.get("rankingScore"), (Double) a.get("rankingScore")))
            .limit(request.getLimit())
            .collect(Collectors.toList());
    }
    
    /**
     * Perform text-based search
     */
    private List<Map<String, Object>> performTextSearch(String queryText, 
                                                       List<Map<String, Object>> entities, 
                                                       AISearchRequest request) {
        String lowerQuery = queryText.toLowerCase();
        
        return entities.stream()
            .map(entity -> {
                String searchableText = (String) entity.get("searchableText");
                double textScore = calculateTextScore(lowerQuery, searchableText);
                
                Map<String, Object> scoredEntity = new HashMap<>(entity);
                scoredEntity.put("textScore", textScore);
                scoredEntity.put("score", textScore);
                return scoredEntity;
            })
            .filter(entity -> (Double) entity.get("textScore") > 0.1) // Minimum text relevance
            .sorted((a, b) -> Double.compare((Double) b.get("textScore"), (Double) a.get("textScore")))
            .limit(request.getLimit())
            .collect(Collectors.toList());
    }
    
    /**
     * Combine vector and text search results
     */
    private List<Map<String, Object>> combineSearchResults(List<Map<String, Object>> vectorResults,
                                                          List<Map<String, Object>> textResults,
                                                          AISearchRequest request) {
        Map<String, Map<String, Object>> combinedMap = new HashMap<>();
        
        // Add vector results
        for (Map<String, Object> result : vectorResults) {
            String id = (String) result.get("id");
            combinedMap.put(id, result);
        }
        
        // Add or update with text results
        for (Map<String, Object> result : textResults) {
            String id = (String) result.get("id");
            if (combinedMap.containsKey(id)) {
                // Combine scores
                Map<String, Object> existing = combinedMap.get(id);
                double vectorScore = (Double) existing.getOrDefault("similarity", 0.0);
                double textScore = (Double) result.getOrDefault("textScore", 0.0);
                double combinedScore = (vectorScore + textScore) / 2.0;
                existing.put("combinedScore", combinedScore);
                existing.put("score", combinedScore);
            } else {
                combinedMap.put(id, result);
            }
        }
        
        return combinedMap.values().stream()
            .sorted((a, b) -> Double.compare(
                (Double) b.getOrDefault("combinedScore", b.getOrDefault("score", 0.0)),
                (Double) a.getOrDefault("combinedScore", a.getOrDefault("score", 0.0))
            ))
            .limit(request.getLimit())
            .collect(Collectors.toList());
    }
    
    /**
     * Perform context-aware search
     */
    private List<Map<String, Object>> performContextualSearch(List<Double> queryVector, 
                                                             String context,
                                                             List<Map<String, Object>> entities, 
                                                             AISearchRequest request) {
        return entities.stream()
            .map(entity -> {
                List<Double> entityVector = (List<Double>) entity.get("embedding");
                double similarity = calculateCosineSimilarity(queryVector, entityVector);
                
                // Apply context weighting
                double contextWeight = calculateContextWeight(context, entity);
                double contextualScore = similarity * contextWeight;
                
                Map<String, Object> scoredEntity = new HashMap<>(entity);
                scoredEntity.put("similarity", similarity);
                scoredEntity.put("contextWeight", contextWeight);
                scoredEntity.put("contextualScore", contextualScore);
                scoredEntity.put("score", contextualScore);
                return scoredEntity;
            })
            .filter(entity -> (Double) entity.get("similarity") >= request.getThreshold())
            .sorted((a, b) -> Double.compare((Double) b.get("contextualScore"), (Double) a.get("contextualScore")))
            .limit(request.getLimit())
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate ranking score with additional factors
     */
    private double calculateRankingScore(Map<String, Object> entity, double similarity, AISearchRequest request) {
        double score = similarity;
        
        // Boost score based on recency
        Long storedAt = (Long) entity.get("storedAt");
        if (storedAt != null) {
            long age = System.currentTimeMillis() - storedAt;
            double recencyBoost = Math.exp(-age / (7 * 24 * 60 * 60 * 1000.0)); // Decay over 7 days
            score += recencyBoost * 0.1;
        }
        
        // Boost score based on metadata relevance
        Map<String, Object> metadata = (Map<String, Object>) entity.get("metadata");
        if (metadata != null && metadata.containsKey("priority")) {
            Integer priority = (Integer) metadata.get("priority");
            score += priority * 0.05;
        }
        
        return Math.min(score, 1.0); // Cap at 1.0
    }
    
    /**
     * Calculate text matching score
     */
    private double calculateTextScore(String query, String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        
        String[] queryWords = query.split("\\s+");
        String[] textWords = text.split("\\s+");
        
        int matches = 0;
        for (String queryWord : queryWords) {
            for (String textWord : textWords) {
                if (textWord.contains(queryWord)) {
                    matches++;
                    break;
                }
            }
        }
        
        return (double) matches / queryWords.length;
    }
    
    /**
     * Calculate context weight
     */
    private double calculateContextWeight(String context, Map<String, Object> entity) {
        if (context == null || context.isEmpty()) {
            return 1.0;
        }
        
        String content = (String) entity.get("content");
        if (content == null) {
            return 1.0;
        }
        
        // Simple context matching
        String lowerContext = context.toLowerCase();
        String lowerContent = content.toLowerCase();
        
        int contextWords = lowerContext.split("\\s+").length;
        int matches = 0;
        
        for (String word : lowerContext.split("\\s+")) {
            if (lowerContent.contains(word)) {
                matches++;
            }
        }
        
        return 0.5 + (0.5 * matches / contextWords);
    }
    
    /**
     * Extract keywords from content
     */
    private List<String> extractKeywords(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(content.toLowerCase().split("\\s+"))
            .filter(word -> word.length() > 3)
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
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
    
    /**
     * Create empty search response
     */
    private AISearchResponse createEmptyResponse(AISearchRequest request, long startTime) {
        return AISearchResponse.builder()
            .results(Collections.emptyList())
            .totalResults(0)
            .maxScore(0.0)
            .processingTimeMs(System.currentTimeMillis() - startTime)
            .requestId(UUID.randomUUID().toString())
            .query(request.getQuery())
            .model(config.getOpenaiEmbeddingModel())
            .build();
    }
}

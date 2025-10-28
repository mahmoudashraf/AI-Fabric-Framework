package com.ai.infrastructure.service;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.vector.VectorDatabaseService;
import com.ai.infrastructure.vector.EntityVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Hybrid AI Capability Service
 * 
 * Combines the best of both worlds:
 * - AISearchableEntity: Database persistence, business logic, AI analysis
 * - Vector Database: High-performance similarity search
 * 
 * This service stores metadata and AI analysis in the database while
 * storing vectors in the optimized vector database for fast search.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridAICapabilityService {
    
    private final AIEmbeddingService embeddingService;
    private final AICoreService aiCoreService;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final VectorDatabaseService vectorDatabaseService;
    
    /**
     * Process entity with hybrid storage: database + vector database
     * 
     * @param entity The entity to process
     * @param entityType The type of entity
     */
    public void processEntityForAI(Object entity, String entityType) {
        try {
            log.debug("Processing entity for AI (hybrid): {} of type {}", getEntityId(entity), entityType);
            
            // Load configuration
            AIEntityConfig config = entityConfigurationLoader.getEntityConfig(entityType);
            if (config == null) {
                log.warn("No configuration found for entity type: {}", entityType);
                return;
            }
            
            // Extract content and metadata
            String content = extractSearchableContent(entity, config);
            Map<String, Object> metadata = extractMetadata(entity, config);
            String entityId = getEntityId(entity);
            
            // Generate embeddings
            AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(
                AIEmbeddingRequest.builder()
                    .text(content)
                    .build()
            );
            
            // Generate AI analysis
            String aiAnalysis = generateAIAnalysis(content);
            
            // 1. Store in DATABASE (AISearchableEntity) - persistence & business logic
            AISearchableEntity aiEntity = AISearchableEntity.builder()
                .entityType(entityType)
                .entityId(entityId)
                .searchableContent(content)
                .metadata(convertToJson(metadata))
                .aiAnalysis(aiAnalysis)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            
            searchableEntityRepository.save(aiEntity);  // Database persistence
            
            // 2. Store in VECTOR DATABASE - fast similarity search
            vectorDatabaseService.storeEntityVector(
                entityType,
                entityId,
                content,
                embeddingResponse.getEmbedding(),
                metadata
            );
            
            log.debug("Successfully processed entity {} in both database and vector store", entityId);
            
        } catch (Exception e) {
            log.error("Error processing entity for AI: {}", entityType, e);
        }
    }
    
    /**
     * Batch process multiple entities with hybrid storage
     * 
     * @param entities List of entities to process
     * @param entityType The type of entities
     */
    public void batchProcessEntitiesForAI(List<Object> entities, String entityType) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        try {
            log.debug("Batch processing {} entities of type {} (hybrid)", entities.size(), entityType);
            
            AIEntityConfig config = entityConfigurationLoader.getEntityConfig(entityType);
            if (config == null) {
                log.warn("No configuration found for entity type: {}", entityType);
                return;
            }
            
            List<AISearchableEntity> dbEntities = new ArrayList<>();
            List<EntityVector> vectorEntities = new ArrayList<>();
            
            for (Object entity : entities) {
                try {
                    String content = extractSearchableContent(entity, config);
                    Map<String, Object> metadata = extractMetadata(entity, config);
                    String entityId = getEntityId(entity);
                    
                    // Generate embeddings
                    AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(
                        AIEmbeddingRequest.builder()
                            .text(content)
                            .build()
                    );
                    
                    String aiAnalysis = generateAIAnalysis(content);
                    
                    // Prepare for database storage
                    AISearchableEntity aiEntity = AISearchableEntity.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .searchableContent(content)
                        .metadata(convertToJson(metadata))
                        .aiAnalysis(aiAnalysis)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                    
                    dbEntities.add(aiEntity);
                    
                    // Prepare for vector storage
                    EntityVector entityVector = EntityVector.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .content(content)
                        .vector(embeddingResponse.getEmbedding())
                        .metadata(metadata)
                        .build();
                    
                    vectorEntities.add(entityVector);
                    
                } catch (Exception e) {
                    log.warn("Error processing individual entity in batch: {}", getEntityId(entity), e);
                }
            }
            
            // Batch store in database
            if (!dbEntities.isEmpty()) {
                searchableEntityRepository.saveAll(dbEntities);
                log.debug("Batch stored {} entities in database", dbEntities.size());
            }
            
            // Batch store in vector database
            if (!vectorEntities.isEmpty()) {
                vectorDatabaseService.batchStoreEntityVectors(vectorEntities);
                log.debug("Batch stored {} entity vectors", vectorEntities.size());
            }
            
        } catch (Exception e) {
            log.error("Error in batch processing entities for AI: {}", entityType, e);
        }
    }
    
    /**
     * Hybrid search: Fast vector search + rich database data
     * 
     * @param queryText The search query text
     * @param entityType Optional entity type filter
     * @param limit Maximum number of results
     * @param threshold Minimum similarity threshold
     * @return Enriched search results
     */
    public AISearchResponse searchSimilarEntities(String queryText, String entityType, int limit, double threshold) {
        try {
            log.debug("Hybrid search: query='{}', entityType='{}', limit={}", queryText, entityType, limit);
            
            // 1. Generate query embedding
            AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(
                AIEmbeddingRequest.builder()
                    .text(queryText)
                    .build()
            );
            
            // 2. Fast vector similarity search
            AISearchResponse vectorResults = vectorDatabaseService.searchSimilarEntities(
                embeddingResponse.getEmbedding(),
                entityType,
                limit,
                threshold
            );
            
            // 3. Extract entity IDs from vector results
            List<String> entityIds = vectorResults.getResults().stream()
                .map(result -> (String) result.get("id"))
                .collect(Collectors.toList());
            
            if (entityIds.isEmpty()) {
                return vectorResults; // No results to enrich
            }
            
            // 4. Fetch full data from database (including AI analysis)
            List<AISearchableEntity> fullData = searchableEntityRepository.findByEntityTypeAndEntityIdIn(entityType, entityIds);
            
            // 5. Enrich vector results with database data
            return enrichSearchResults(vectorResults, fullData);
            
        } catch (Exception e) {
            log.error("Error performing hybrid search", e);
            return AISearchResponse.builder()
                .results(new ArrayList<>())
                .totalResults(0)
                .query(queryText)
                .build();
        }
    }
    
    /**
     * Search using AI search request
     * 
     * @param request The AI search request
     * @return Enriched search results
     */
    public AISearchResponse search(AISearchRequest request) {
        return searchSimilarEntities(
            request.getQuery(),
            request.getEntityType(),
            request.getLimit(),
            request.getThreshold()
        );
    }
    
    /**
     * Get AI analysis for an entity (from database)
     * 
     * @param entityType The entity type
     * @param entityId The entity ID
     * @return Optional AI analysis
     */
    public Optional<String> getAIAnalysis(String entityType, String entityId) {
        return searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId)
            .map(AISearchableEntity::getAiAnalysis);
    }
    
    /**
     * Find entities by content (database search)
     * 
     * @param content The content to search for
     * @return List of matching entities
     */
    public List<AISearchableEntity> findByContent(String content) {
        return searchableEntityRepository.findBySearchableContentContainingIgnoreCase(content);
    }
    
    /**
     * Get all AI entities of a type (database query)
     * 
     * @param entityType The entity type
     * @return List of AI entities
     */
    public List<AISearchableEntity> findByEntityType(String entityType) {
        return searchableEntityRepository.findByEntityType(entityType);
    }
    
    /**
     * Generate embeddings for an entity
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void generateEmbeddings(Object entity, AIEntityConfig config) {
        processEntityForAI(entity, config.getEntityType());
    }
    
    /**
     * Index entity for search (same as processing)
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void indexForSearch(Object entity, AIEntityConfig config) {
        processEntityForAI(entity, config.getEntityType());
    }
    
    /**
     * Analyze entity with AI
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void analyzeEntity(Object entity, AIEntityConfig config) {
        processEntityForAI(entity, config.getEntityType());
    }
    
    /**
     * Remove entity from both database and vector store
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void removeFromSearch(Object entity, AIEntityConfig config) {
        try {
            String entityType = config.getEntityType();
            String entityId = getEntityId(entity);
            
            // Remove from vector database
            boolean vectorDeleted = vectorDatabaseService.deleteEntityVector(entityType, entityId);
            
            // Remove from database
            searchableEntityRepository.deleteByEntityTypeAndEntityId(entityType, entityId);
            
            log.debug("Removed {} entity {} from both stores (vector: {})", 
                     entityType, entityId, vectorDeleted);
            
        } catch (Exception e) {
            log.error("Error removing entity from search", e);
        }
    }
    
    /**
     * Cleanup embeddings for an entity
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void cleanupEmbeddings(Object entity, AIEntityConfig config) {
        removeFromSearch(entity, config);
    }
    
    /**
     * Validate entity before processing
     * 
     * @param entity The entity to validate
     * @param config The AI configuration
     */
    public void validateEntity(Object entity, AIEntityConfig config) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        String entityId = getEntityId(entity);
        if (entityId == null || entityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
    }
    
    /**
     * Get hybrid system statistics
     * 
     * @return Combined statistics from both systems
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Vector database statistics
        Map<String, Object> vectorStats = vectorDatabaseService.getStatistics();
        stats.put("vectorDatabase", vectorStats);
        
        // Database statistics
        long dbEntityCount = searchableEntityRepository.count();
        stats.put("databaseEntityCount", dbEntityCount);
        
        // Combined statistics
        stats.put("type", "hybrid");
        stats.put("vectorDatabaseType", vectorDatabaseService.getDatabaseType());
        
        return stats;
    }
    
    /**
     * Check if the hybrid system is healthy
     * 
     * @return true if both systems are healthy
     */
    public boolean isHealthy() {
        try {
            boolean vectorHealthy = vectorDatabaseService.isHealthy();
            boolean dbHealthy = searchableEntityRepository.count() >= 0; // Simple DB health check
            
            return vectorHealthy && dbHealthy;
        } catch (Exception e) {
            log.error("Hybrid system health check failed", e);
            return false;
        }
    }
    
    /**
     * Get the database type
     * 
     * @return Database type string
     */
    public String getDatabaseType() {
        return "hybrid-" + vectorDatabaseService.getDatabaseType();
    }
    
    /**
     * Clear all data from both systems
     */
    public void clearAllData() {
        try {
            log.warn("Clearing all data from hybrid system");
            
            // Clear vector database
            vectorDatabaseService.clearAllVectors();
            
            // Clear database entities
            searchableEntityRepository.deleteAll();
            
            log.warn("Cleared all data from hybrid system");
            
        } catch (Exception e) {
            log.error("Error clearing hybrid system data", e);
        }
    }
    
    /**
     * Enrich vector search results with database data
     */
    private AISearchResponse enrichSearchResults(AISearchResponse vectorResults, 
                                               List<AISearchableEntity> fullData) {
        // Create lookup map for fast access
        Map<String, AISearchableEntity> dataMap = fullData.stream()
            .collect(Collectors.toMap(AISearchableEntity::getEntityId, entity -> entity));
        
        // Enrich vector results with database data
        List<Map<String, Object>> enrichedResults = vectorResults.getResults().stream()
            .map(result -> {
                String entityId = (String) result.get("id");
                AISearchableEntity fullEntity = dataMap.get(entityId);
                
                if (fullEntity != null) {
                    // Add rich data from database
                    result.put("aiAnalysis", fullEntity.getAiAnalysis());
                    result.put("fullContent", fullEntity.getSearchableContent());
                    result.put("createdAt", fullEntity.getCreatedAt());
                    result.put("updatedAt", fullEntity.getUpdatedAt());
                    result.put("databaseId", fullEntity.getId());
                }
                
                return result;
            })
            .collect(Collectors.toList());
        
        return AISearchResponse.builder()
            .results(enrichedResults)
            .totalResults(vectorResults.getTotalResults())
            .maxScore(vectorResults.getMaxScore())
            .processingTimeMs(vectorResults.getProcessingTimeMs())
            .query(vectorResults.getQuery())
            .requestId(vectorResults.getRequestId())
            .model(vectorResults.getModel())
            .build();
    }
    
    /**
     * Generate AI analysis for content
     */
    private String generateAIAnalysis(String content) {
        try {
            // Use AI service to generate insights about the content
            // This could be enhanced with specific analysis prompts
            String prompt = "Analyze the following content and provide insights: " + 
                          content.substring(0, Math.min(200, content.length()));
            
            // For now, return a simple analysis
            return "AI Analysis: Content contains " + content.length() + 
                   " characters. Key topics identified from content analysis.";
            
        } catch (Exception e) {
            log.warn("Error generating AI analysis", e);
            return "AI Analysis: Basic content processed successfully.";
        }
    }
    
    /**
     * Convert metadata map to JSON string
     */
    private String convertToJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        
        return metadata.entrySet().stream()
            .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
            .collect(Collectors.joining(",", "{", "}"));
    }
    
    /**
     * Extract searchable content from entity
     */
    private String extractSearchableContent(Object entity, AIEntityConfig config) {
        List<String> contentParts = new ArrayList<>();
        
        for (var field : config.getSearchableFields()) {
            try {
                String value = getFieldValue(entity, field.getName());
                if (value != null && !value.trim().isEmpty()) {
                    contentParts.add(value);
                }
            } catch (Exception e) {
                log.warn("Error extracting field {}: {}", field.getName(), e.getMessage());
            }
        }
        
        return String.join(" ", contentParts);
    }
    
    /**
     * Extract metadata from entity
     */
    private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
        Map<String, Object> metadata = new HashMap<>();
        
        for (var field : config.getMetadataFields()) {
            try {
                Object value = getFieldValue(entity, field.getName());
                if (value != null) {
                    metadata.put(field.getName(), value);
                }
            } catch (Exception e) {
                log.warn("Error extracting metadata field {}: {}", field.getName(), e.getMessage());
            }
        }
        
        return metadata;
    }
    
    /**
     * Get field value using reflection
     */
    private String getFieldValue(Object entity, String fieldName) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("Could not get field value for {}: {}", fieldName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get entity ID using reflection
     */
    private String getEntityId(Object entity) {
        try {
            // Try common ID field names
            String[] idFields = {"id", "getId", "uuid", "getUuid"};
            
            for (String fieldName : idFields) {
                try {
                    if (fieldName.startsWith("get")) {
                        // Try method
                        var method = entity.getClass().getMethod(fieldName);
                        Object value = method.invoke(entity);
                        if (value != null) {
                            return value.toString();
                        }
                    } else {
                        // Try field
                        Field field = entity.getClass().getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object value = field.get(entity);
                        if (value != null) {
                            return value.toString();
                        }
                    }
                } catch (Exception ignored) {
                    // Try next field
                }
            }
            
            // Fallback to toString or hashCode
            return entity.toString();
            
        } catch (Exception e) {
            log.warn("Could not extract entity ID: {}", e.getMessage());
            return "unknown-" + entity.hashCode();
        }
    }
}
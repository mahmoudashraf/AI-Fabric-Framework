package com.ai.infrastructure.service;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.vector.VectorDatabaseService;
import com.ai.infrastructure.vector.EntityVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced AI Capability Service
 * 
 * Integrates the new vector database with existing AI processing capabilities.
 * Provides seamless migration from old storage to new vector database.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedAICapabilityService {
    
    private final AIEmbeddingService embeddingService;
    private final AICoreService aiCoreService;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final VectorDatabaseService vectorDatabaseService;
    
    /**
     * Process entity for AI with new vector database
     * 
     * @param entity The entity to process
     * @param entityType The type of entity
     */
    public void processEntityForAI(Object entity, String entityType) {
        try {
            log.debug("Processing entity for AI: {} of type {}", getEntityId(entity), entityType);
            
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
            
            // Store in new vector database
            vectorDatabaseService.storeEntityVector(
                entityType,
                entityId,
                content,
                embeddingResponse.getEmbedding(),
                metadata
            );
            
            // Also store in traditional database for backward compatibility
            storeInTraditionalDatabase(entityType, entityId, content, 
                                     embeddingResponse.getEmbedding(), metadata);
            
            log.debug("Successfully processed entity {} of type {} for AI", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error processing entity for AI: {}", entityType, e);
        }
    }
    
    /**
     * Batch process multiple entities for AI
     * 
     * @param entities List of entities to process
     * @param entityType The type of entities
     */
    public void batchProcessEntitiesForAI(List<Object> entities, String entityType) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        try {
            log.debug("Batch processing {} entities of type {}", entities.size(), entityType);
            
            AIEntityConfig config = entityConfigurationLoader.getEntityConfig(entityType);
            if (config == null) {
                log.warn("No configuration found for entity type: {}", entityType);
                return;
            }
            
            List<EntityVector> entityVectors = new ArrayList<>();
            
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
                    
                    // Create entity vector for batch storage
                    EntityVector entityVector = EntityVector.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .content(content)
                        .vector(embeddingResponse.getEmbedding())
                        .metadata(metadata)
                        .build();
                    
                    entityVectors.add(entityVector);
                    
                } catch (Exception e) {
                    log.warn("Error processing individual entity in batch: {}", getEntityId(entity), e);
                }
            }
            
            // Batch store in vector database
            if (!entityVectors.isEmpty()) {
                vectorDatabaseService.batchStoreEntityVectors(entityVectors);
                log.debug("Batch stored {} entity vectors", entityVectors.size());
            }
            
        } catch (Exception e) {
            log.error("Error in batch processing entities for AI: {}", entityType, e);
        }
    }
    
    /**
     * Generate embeddings for an entity
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void generateEmbeddings(Object entity, AIEntityConfig config) {
        try {
            String entityType = config.getEntityType();
            String entityId = getEntityId(entity);
            String content = extractSearchableContent(entity, config);
            
            AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(
                AIEmbeddingRequest.builder()
                    .text(content)
                    .build()
            );
            
            Map<String, Object> metadata = extractMetadata(entity, config);
            
            // Store in vector database
            vectorDatabaseService.storeEntityVector(
                entityType,
                entityId,
                content,
                embeddingResponse.getEmbedding(),
                metadata
            );
            
            log.debug("Generated and stored embeddings for {} entity {}", entityType, entityId);
            
        } catch (Exception e) {
            log.error("Error generating embeddings for entity", e);
        }
    }
    
    /**
     * Index entity for search
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void indexForSearch(Object entity, AIEntityConfig config) {
        // This is now handled by the vector database automatically
        generateEmbeddings(entity, config);
    }
    
    /**
     * Analyze entity with AI
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void analyzeEntity(Object entity, AIEntityConfig config) {
        try {
            String content = extractSearchableContent(entity, config);
            
            // Use AI core service for analysis
            // This could be enhanced with specific analysis prompts
            log.debug("Analyzing entity content: {}", content.substring(0, Math.min(50, content.length())));
            
        } catch (Exception e) {
            log.error("Error analyzing entity", e);
        }
    }
    
    /**
     * Remove entity from search index
     * 
     * @param entity The entity
     * @param config The AI configuration
     */
    public void removeFromSearch(Object entity, AIEntityConfig config) {
        try {
            String entityType = config.getEntityType();
            String entityId = getEntityId(entity);
            
            boolean deleted = vectorDatabaseService.deleteEntityVector(entityType, entityId);
            
            if (deleted) {
                log.debug("Removed {} entity {} from search index", entityType, entityId);
            } else {
                log.warn("Entity {} of type {} not found in search index", entityId, entityType);
            }
            
        } catch (Exception e) {
            log.error("Error removing entity from search index", e);
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
        // Add validation logic here
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        String entityId = getEntityId(entity);
        if (entityId == null || entityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
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
    
    /**
     * Store in traditional database for backward compatibility
     */
    private void storeInTraditionalDatabase(String entityType, String entityId, String content,
                                          List<Double> embeddings, Map<String, Object> metadata) {
        try {
            // Convert metadata to JSON string
            String metadataJson = metadata.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
            
            AISearchableEntity searchableEntity = AISearchableEntity.builder()
                .entityType(entityType)
                .entityId(entityId)
                .searchableContent(content)
                .embeddings(embeddings)
                .metadata(metadataJson)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
            
            searchableEntityRepository.save(searchableEntity);
            
        } catch (Exception e) {
            log.warn("Error storing in traditional database: {}", e.getMessage());
        }
    }
}
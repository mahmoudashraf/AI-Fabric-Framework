package com.ai.infrastructure.service;

import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AISearchableField;
import com.ai.infrastructure.dto.AIEmbeddableField;
import com.ai.infrastructure.dto.AIMetadataField;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.util.MetadataJsonSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI Capability Service
 * 
 * Core service that performs AI processing based on entity configuration.
 * Handles embedding generation, search indexing, and AI analysis.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AICapabilityService {
    
    private final AIEmbeddingService embeddingService;
    private final AICoreService aiCoreService;
    private final AISearchableEntityStorageStrategy storageStrategy;
    private final AIEntityConfigurationLoader configurationLoader;
    private final VectorManagementService vectorManagementService;
    
    public AICapabilityService(AIEmbeddingService embeddingService,
                              AICoreService aiCoreService,
                              AISearchableEntityStorageStrategy storageStrategy,
                              AIEntityConfigurationLoader configurationLoader,
                              VectorManagementService vectorManagementService) {
        this.embeddingService = embeddingService;
        this.aiCoreService = aiCoreService;
        this.storageStrategy = storageStrategy;
        this.configurationLoader = configurationLoader;
        this.vectorManagementService = Objects.requireNonNull(vectorManagementService,
            "VectorManagementService must be configured for AICapabilityService");
    }
    
    // Debug method to access configurationLoader
    public AIEntityConfigurationLoader getConfigurationLoader() {
        return configurationLoader;
    }
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("AICapabilityService initialized with configurationLoader: {}", configurationLoader != null ? "present" : "null");
        if (configurationLoader != null) {
            log.info("Configuration loader supports entity types: {}", configurationLoader.getSupportedEntityTypes());
        }
    }
    
    /**
     * Validate entity based on configuration
     */
    public void validateEntity(Object entity, AIEntityConfig config) {
        try {
            log.debug("Validating entity of type: {}", config.getEntityType());
            
            // Extract searchable content
            String searchableContent = extractSearchableContent(entity, config);
            
            // Validate content if needed
            if (config.getFeatures().contains("validation")) {
                // Perform AI-powered validation
                boolean isValid = aiCoreService.validateContent(searchableContent, Map.of()).containsKey("valid");
                if (!isValid) {
                    throw new RuntimeException("Entity validation failed");
                }
            }
            
        } catch (Exception e) {
            log.error("Error validating entity", e);
            throw new RuntimeException("Entity validation failed", e);
        }
    }
    
    /**
     * Generate embeddings for entity based on configuration
     */
    @Transactional
    public void generateEmbeddings(Object entity, AIEntityConfig config) {
        try {
            log.debug("Generating embeddings for entity of type: {}", config.getEntityType());
            log.debug("Config metadata fields: {}", config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");
            
            if (!config.isAutoEmbedding()) {
                log.debug("Auto-embedding disabled for entity type: {}", config.getEntityType());
                return;
            }
            
            // Extract embeddable content
            String embeddableContent = extractEmbeddableContent(entity, config);
            
            if (embeddableContent == null || embeddableContent.trim().isEmpty()) {
                log.warn("No embeddable content found for entity");
                return;
            }
            
            // Generate embeddings
            List<Double> embeddings = embeddingService.generateEmbedding(
                com.ai.infrastructure.dto.AIEmbeddingRequest.builder()
                    .text(embeddableContent)
                    .build()
            ).getEmbedding();
            
            // Store in searchable entity
            storeSearchableEntity(entity, config, embeddableContent, embeddings);
            
        } catch (Exception e) {
            log.error("Error generating embeddings for entity", e);
        }
    }
    
    /**
     * Index entity for search based on configuration
     */
    @Transactional
    public void indexForSearch(Object entity, AIEntityConfig config) {
        try {
            log.debug("Indexing entity for search of type: {}", config.getEntityType());
            
            if (!config.isIndexable()) {
                log.debug("Indexing disabled for entity type: {}", config.getEntityType());
                return;
            }
            
            // Extract searchable content
            String searchableContent = extractSearchableContent(entity, config);
            
            if (searchableContent == null || searchableContent.trim().isEmpty()) {
                log.warn("No searchable content found for entity");
                return;
            }
            
            // Generate embeddings if not already done
            List<Double> embeddings = embeddingService.generateEmbedding(
                com.ai.infrastructure.dto.AIEmbeddingRequest.builder()
                    .text(searchableContent)
                    .build()
            ).getEmbedding();
            
            // Store in searchable entity
            storeSearchableEntity(entity, config, searchableContent, embeddings);
            
        } catch (Exception e) {
            log.error("Error indexing entity for search", e);
        }
    }
    
    /**
     * Analyze entity based on configuration
     */
    public void analyzeEntity(Object entity, AIEntityConfig config) {
        try {
            log.debug("Analyzing entity of type: {}", config.getEntityType());
            
            // Extract content for analysis
            String content = extractSearchableContent(entity, config);
            
            if (content == null || content.trim().isEmpty()) {
                log.warn("No content found for analysis");
                return;
            }
            
            // Perform AI analysis
            String analysis = aiCoreService.generateText("Analyze this " + config.getEntityType() + " content: " + content);
            
            // Store analysis result
            storeAnalysisResult(entity, config, analysis);
            
        } catch (Exception e) {
            log.error("Error analyzing entity", e);
        }
    }
    
    /**
     * Remove entity from search index
     */
    @Transactional
    public void removeFromSearch(Object entity, AIEntityConfig config) {
        try {
            log.debug("Removing entity from search index of type: {}", config.getEntityType());
            
            // Get entity ID
            String entityId = getEntityId(entity);
            if (entityId == null) {
                log.warn("No entity ID found for removal");
                return;
            }
            
            // Remove from searchable entity storage
            storageStrategy.deleteByEntityTypeAndEntityId(config.getEntityType(), entityId);
            
        } catch (Exception e) {
            log.error("Error removing entity from search index", e);
        }
    }
    
    /**
     * Cleanup embeddings for entity
     */
    @Transactional
    public void cleanupEmbeddings(Object entity, AIEntityConfig config) {
        try {
            log.debug("Cleaning up embeddings for entity of type: {}", config.getEntityType());
            
            // Get entity ID
            String entityId = getEntityId(entity);
            if (entityId == null) {
                log.warn("No entity ID found for cleanup");
                return;
            }
            
            // Remove vector from vector database
            boolean vectorRemoved = vectorManagementService.removeVector(config.getEntityType(), entityId);
            if (vectorRemoved) {
                log.debug("Successfully removed vector from vector database for entity {} of type {}", entityId, config.getEntityType());
            } else {
                log.warn("Vector not found in vector database for entity {} of type {}", entityId, config.getEntityType());
            }
            
            // Remove from searchable entity storage
            storageStrategy.deleteByEntityTypeAndEntityId(config.getEntityType(), entityId);
            
        } catch (Exception e) {
            log.error("Error cleaning up embeddings for entity", e);
        }
    }
    
    private String extractSearchableContent(Object entity, AIEntityConfig config) {
        try {
            List<String> contentParts = new ArrayList<>();
            
            if (config.getSearchableFields() != null) {
                for (AISearchableField field : config.getSearchableFields()) {
                    String value = getFieldValue(entity, field.getName());
                    if (value != null && !value.trim().isEmpty()) {
                        contentParts.add(value);
                    }
                }
            }
            
            return String.join(" ", contentParts);
            
        } catch (Exception e) {
            log.error("Error extracting searchable content", e);
            return "";
        }
    }
    
    private String extractEmbeddableContent(Object entity, AIEntityConfig config) {
        try {
            List<String> contentParts = new ArrayList<>();
            
            if (config.getEmbeddableFields() != null) {
                for (AIEmbeddableField field : config.getEmbeddableFields()) {
                    String value = getFieldValue(entity, field.getName());
                    if (value != null && !value.trim().isEmpty()) {
                        contentParts.add(value);
                    }
                }
            }
            
            return String.join(" ", contentParts);
            
        } catch (Exception e) {
            log.error("Error extracting embeddable content", e);
            return "";
        }
    }
    
    private String getFieldValue(Object entity, String fieldName) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.debug("Field not found or accessible: {}", fieldName);
            return "";
        }
    }
    
    public String resolveEntityId(Object entity) {
        return getEntityId(entity);
    }

    private String getEntityId(Object entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(entity);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            log.debug("ID field not found or accessible");
            return null;
        }
    }
    
    private void storeSearchableEntity(Object entity, AIEntityConfig config, String content, List<Double> embeddings) {
        try {
            String entityId = getEntityId(entity);
            if (entityId == null) {
                log.warn("No entity ID found for storing searchable entity");
                return;
            }
            
            // Store vector in vector database
            Map<String, Object> metadata = extractMetadata(entity, config);
            String vectorId = vectorManagementService.storeVector(
                config.getEntityType(),
                entityId,
                content,
                embeddings,
                metadata
            );
            
            if (vectorId == null) {
                log.error("Failed to store vector in vector database for entity {} of type {}", entityId, config.getEntityType());
                return;
            }
            
            AISearchableEntity searchableEntity = storageStrategy
                .findByEntityTypeAndEntityId(config.getEntityType(), entityId)
                .orElseGet(() -> AISearchableEntity.builder()
                    .entityType(config.getEntityType())
                    .entityId(entityId)
                    .createdAt(java.time.LocalDateTime.now())
                    .build());

            String metadataJson = MetadataJsonSerializer.serialize(metadata, config);
            searchableEntity.setSearchableContent(content);
            searchableEntity.setVectorId(vectorId);
            searchableEntity.setVectorUpdatedAt(java.time.LocalDateTime.now());
            searchableEntity.setMetadata(metadataJson);
            searchableEntity.setUpdatedAt(java.time.LocalDateTime.now());

            storageStrategy.save(searchableEntity);
            
        } catch (Exception e) {
            log.error("Error storing searchable entity", e);
        }
    }
    
    private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        
        try {
            // Simple defensive check: if metadataFields is null, return empty metadata
            if (config == null || config.getMetadataFields() == null) {
                log.warn("Config or metadata fields are null, skipping metadata extraction");
                return metadata;
            }
            
            // Extract metadata from fields
            for (AIMetadataField field : config.getMetadataFields()) {
                try {
                    String value = getFieldValue(entity, field.getName());
                    if (value != null && !value.trim().isEmpty()) {
                        metadata.put(field.getName(), value);
                        log.debug("Extracted metadata field {}: {}", field.getName(), value);
                    }
                } catch (Exception fieldException) {
                    log.warn("Failed to extract metadata field {}: {}", field.getName(), fieldException.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error extracting metadata", e);
            // Return empty metadata instead of throwing exception to prevent breaking the flow
        }
        
        return metadata;
    }
    
    private void storeAnalysisResult(Object entity, AIEntityConfig config, String analysis) {
        try {
            String entityId = getEntityId(entity);
            if (entityId == null) {
                log.warn("No entity ID found for storing analysis result");
                return;
            }
            
            storageStrategy.findByEntityTypeAndEntityId(config.getEntityType(), entityId)
                .ifPresent(entityToUpdate -> {
                    entityToUpdate.setAiAnalysis(analysis);
                    entityToUpdate.setUpdatedAt(java.time.LocalDateTime.now());
                    storageStrategy.save(entityToUpdate);
                });

        } catch (Exception e) {
            log.error("Error storing analysis result", e);
        }
    }
    
    
    /**
     * Remove entity from AI index
     */
    @Transactional
    public void removeEntityFromIndex(String entityId, String entityType) {
        try {
            log.debug("Removing entity from AI index: {} of type {}", entityId, entityType);
            
            Optional<AISearchableEntity> searchableEntity = storageStrategy
                .findByEntityTypeAndEntityId(entityType, entityId);

            searchableEntity.ifPresentOrElse(storageStrategy::delete,
                () -> log.warn("Entity not found in AI index: {} of type {}", entityId, entityType));
            
        } catch (Exception e) {
            log.error("Error removing entity from AI index", e);
        }
    }
    
    /**
     * Validate AI entity configuration
     */
    private void validateConfiguration(AIEntityConfig config, String entityType) {
        if (config == null) {
            throw new IllegalArgumentException("AI configuration cannot be null for entity type: " + entityType);
        }
        
        if (config.getEntityType() == null || config.getEntityType().trim().isEmpty()) {
            throw new IllegalArgumentException("AI configuration entity type cannot be null or empty");
        }
        
        if (config.getSearchableFields() == null || config.getSearchableFields().isEmpty()) {
            log.warn("No searchable fields configured for entity type: {}", entityType);
        }
        
        if (config.getEmbeddableFields() == null || config.getEmbeddableFields().isEmpty()) {
            log.warn("No embeddable fields configured for entity type: {}", entityType);
        }
        
        if (config.getMetadataFields() == null) {
            log.warn("No metadata fields configured for entity type: {} - metadata extraction will be skipped", entityType);
        }
    }
    
    /**
     * Process entity for AI capabilities
     */
    @Transactional
    public void processEntityForAI(Object entity, String entityType) {
        try {
            log.debug("Processing entity for AI of type: {}", entityType);
            log.debug("Configuration loader is: {}", configurationLoader != null ? "available" : "null");
            
            // Validate configuration loader
            if (configurationLoader == null) {
                log.error("Configuration loader is not available");
                throw new IllegalStateException("AI configuration loader is not available. Check Spring context configuration.");
            }
            
            // Get entity configuration from configuration loader
            AIEntityConfig config = configurationLoader.getEntityConfig(entityType);
            if (config == null) {
                log.error("No configuration found for entity type: {}", entityType);
                log.error("Available entity types: {}", configurationLoader.getSupportedEntityTypes());
                throw new IllegalArgumentException("No AI configuration found for entity type: " + entityType + 
                    ". Available types: " + configurationLoader.getSupportedEntityTypes());
            }
            
            // Debug: Check if this is the same instance as in ConfigurationTest
            log.debug("Config instance: {}, metadataFields: {}", 
                config.getClass().getSimpleName(), 
                config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");
            log.debug("Config metadataFields instance: {}", 
                config.getMetadataFields() != null ? config.getMetadataFields().getClass().getSimpleName() : "null");
            
            // Validate configuration
            validateConfiguration(config, entityType);
            
            log.debug("Retrieved config for entity type: {}, metadata fields: {}",
                entityType, config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");

            // Validate configuration completeness
            if (config.getMetadataFields() == null) {
                log.warn("Metadata fields are null for entity type: {}", entityType);
                log.warn("Config details - entityType: {}, searchableFields: {}, embeddableFields: {}",
                    config.getEntityType(),
                    config.getSearchableFields() != null ? config.getSearchableFields().size() : "null",
                    config.getEmbeddableFields() != null ? config.getEmbeddableFields().size() : "null");
                log.warn("Continuing with null metadata fields - this may cause issues in metadata extraction");
            }
            
            // Generate embeddings
            log.debug("About to call generateEmbeddings with config metadata fields: {}", 
                config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");
            log.debug("Config object details: entityType={}, metadataFields={}, searchableFields={}", 
                config.getEntityType(),
                config.getMetadataFields() != null ? config.getMetadataFields().size() : "null",
                config.getSearchableFields() != null ? config.getSearchableFields().size() : "null");
            generateEmbeddings(entity, config);
            
            // Index for search
            log.debug("About to call indexForSearch with config metadata fields: {}", 
                config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");
            indexForSearch(entity, config);
            
            // Analyze entity
            log.debug("About to call analyzeEntity with config metadata fields: {}", 
                config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");
            analyzeEntity(entity, config);
            
            log.debug("Successfully processed entity for AI");
            
        } catch (Exception e) {
            log.error("Error processing entity for AI", e);
        }
    }
}

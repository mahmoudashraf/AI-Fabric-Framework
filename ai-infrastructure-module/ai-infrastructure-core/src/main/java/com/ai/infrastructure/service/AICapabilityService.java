package com.ai.infrastructure.service;

import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AISearchableField;
import com.ai.infrastructure.dto.AIEmbeddableField;
import com.ai.infrastructure.dto.AIMetadataField;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
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
@RequiredArgsConstructor
public class AICapabilityService {
    
    private final AIEmbeddingService embeddingService;
    private final AICoreService aiCoreService;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final AIEntityConfigurationLoader configurationLoader;
    
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
            
            // Remove from searchable entity repository
            searchableEntityRepository.deleteByEntityTypeAndEntityId(config.getEntityType(), entityId);
            
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
            
            // Remove embeddings from searchable entity repository
            searchableEntityRepository.deleteByEntityTypeAndEntityId(config.getEntityType(), entityId);
            
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
            
            // Check if entity already exists and remove duplicates
            List<AISearchableEntity> existing = searchableEntityRepository
                .findByEntityType(config.getEntityType())
                .stream()
                .filter(e -> entityId.equals(e.getEntityId()))
                .toList();
            
            // Remove duplicates if any
            if (existing.size() > 1) {
                for (int i = 1; i < existing.size(); i++) {
                    searchableEntityRepository.delete(existing.get(i));
                }
            }
            
            AISearchableEntity searchableEntity;
            if (!existing.isEmpty()) {
                // Update existing entity
                searchableEntity = existing.get(0);
                searchableEntity.setSearchableContent(content);
                searchableEntity.setEmbeddings(embeddings);
                searchableEntity.setMetadata(convertMetadataToJson(extractMetadata(entity, config)));
                searchableEntity.setUpdatedAt(java.time.LocalDateTime.now());
            } else {
                // Create new entity
                searchableEntity = AISearchableEntity.builder()
                    .entityType(config.getEntityType())
                    .entityId(entityId)
                    .searchableContent(content)
                    .embeddings(embeddings)
                    .metadata(convertMetadataToJson(extractMetadata(entity, config)))
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            }
            
            searchableEntityRepository.save(searchableEntity);
            
        } catch (Exception e) {
            log.error("Error storing searchable entity", e);
        }
    }
    
    private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            log.debug("extractMetadata called with config: entityType={}, metadataFields={}", 
                config.getEntityType(), 
                config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");
            
            if (config.getMetadataFields() != null && !config.getMetadataFields().isEmpty()) {
                log.debug("Extracting metadata from {} fields for entity type {}", 
                    config.getMetadataFields().size(), config.getEntityType());
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
            } else {
                log.debug("No metadata fields configured for entity type {}", config.getEntityType());
            }
        } catch (Exception e) {
            log.error("Error extracting metadata", e);
            // Return empty metadata instead of throwing exception to prevent breaking the flow
        }
        
        return metadata;
    }
    
    private String convertMetadataToJson(Map<String, Object> metadata) {
        try {
            // Simple JSON conversion - in production, use Jackson or Gson
            if (metadata.isEmpty()) {
                return "{}";
            }
            
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            log.error("Error converting metadata to JSON", e);
            return "{}";
        }
    }
    
    private void storeAnalysisResult(Object entity, AIEntityConfig config, String analysis) {
        try {
            String entityId = getEntityId(entity);
            if (entityId == null) {
                log.warn("No entity ID found for storing analysis result");
                return;
            }
            
            // Find all matching entities and update the first one
            List<AISearchableEntity> existing = searchableEntityRepository
                .findByEntityType(config.getEntityType())
                .stream()
                .filter(e -> entityId.equals(e.getEntityId()))
                .toList();
            
            if (!existing.isEmpty()) {
                AISearchableEntity entityToUpdate = existing.get(0);
                entityToUpdate.setAiAnalysis(analysis);
                entityToUpdate.setUpdatedAt(java.time.LocalDateTime.now());
                searchableEntityRepository.save(entityToUpdate);
                
                // If there are duplicates, remove them
                if (existing.size() > 1) {
                    for (int i = 1; i < existing.size(); i++) {
                        searchableEntityRepository.delete(existing.get(i));
                    }
                }
            }
            
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
            
            // Find and remove searchable entity
            Optional<AISearchableEntity> searchableEntity = searchableEntityRepository
                .findByEntityTypeAndEntityId(entityType, entityId);
            
            if (searchableEntity.isPresent()) {
                searchableEntityRepository.delete(searchableEntity.get());
                log.debug("Removed entity from AI index");
            } else {
                log.warn("Entity not found in AI index: {} of type {}", entityId, entityType);
            }
            
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

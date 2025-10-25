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
            if (config.getMetadataFields() != null) {
                log.debug("Extracting metadata from {} fields for entity type {}", 
                    config.getMetadataFields().size(), config.getEntityType());
                for (AIMetadataField field : config.getMetadataFields()) {
                    String value = getFieldValue(entity, field.getName());
                    if (value != null && !value.trim().isEmpty()) {
                        metadata.put(field.getName(), value);
                        log.debug("Extracted metadata field {}: {}", field.getName(), value);
                    }
                }
            } else {
                log.debug("No metadata fields configured for entity type {}", config.getEntityType());
            }
        } catch (Exception e) {
            log.error("Error extracting metadata", e);
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
     * Process entity for AI capabilities
     */
    @Transactional
    public void processEntityForAI(Object entity, String entityType) {
        try {
            log.debug("Processing entity for AI of type: {}", entityType);
            log.debug("Configuration loader is: {}", configurationLoader != null ? "available" : "null");
            
            // Get entity configuration from configuration loader
            AIEntityConfig config = configurationLoader.getEntityConfig(entityType);
            if (config == null) {
                log.warn("No configuration found for entity type: {}", entityType);
                log.warn("Available entity types: {}", configurationLoader.getSupportedEntityTypes());
                return;
            }
            
            log.debug("Retrieved config for entity type: {}, metadata fields: {}", 
                entityType, config.getMetadataFields() != null ? config.getMetadataFields().size() : "null");
            
            if (config.getMetadataFields() == null) {
                log.warn("Metadata fields are null for entity type: {}", entityType);
                log.warn("Config details - entityType: {}, searchableFields: {}, embeddableFields: {}", 
                    config.getEntityType(),
                    config.getSearchableFields() != null ? config.getSearchableFields().size() : "null",
                    config.getEmbeddableFields() != null ? config.getEmbeddableFields().size() : "null");
            }
            
            // Generate embeddings
            generateEmbeddings(entity, config);
            
            // Index for search
            indexForSearch(entity, config);
            
            // Analyze entity
            analyzeEntity(entity, config);
            
            log.debug("Successfully processed entity for AI");
            
        } catch (Exception e) {
            log.error("Error processing entity for AI", e);
        }
    }
    
    /**
     * Get entity configuration by type
     */
    private AIEntityConfig getEntityConfig(String entityType) {
        // This would typically load from configuration
        // For now, return a basic config
        return AIEntityConfig.builder()
            .entityType(entityType)
            .autoEmbedding(true)
            .indexable(true)
            .searchableFields(List.of(
                AISearchableField.builder()
                    .name("name")
                    .weight(1.0)
                    .build()
            ))
            .embeddableFields(List.of(
                AIEmbeddableField.builder()
                    .name("description")
                    .model("text-embedding-3-small")
                    .autoGenerate(true)
                    .includeInSimilarity(true)
                    .build()
            ))
            .metadataFields(List.of(
                AIMetadataField.builder()
                    .name("category")
                    .type("TEXT")
                    .includeInSearch(true)
                    .build(),
                AIMetadataField.builder()
                    .name("price")
                    .type("NUMERIC")
                    .includeInSearch(false)
                    .build(),
                AIMetadataField.builder()
                    .name("brand")
                    .type("TEXT")
                    .includeInSearch(true)
                    .build()
            ))
            .features(List.of("embedding", "search", "analysis"))
            .build();
    }
}

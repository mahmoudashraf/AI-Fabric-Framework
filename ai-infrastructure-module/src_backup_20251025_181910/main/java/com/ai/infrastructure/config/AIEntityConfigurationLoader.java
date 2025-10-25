package com.ai.infrastructure.config;

import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AISearchableField;
import com.ai.infrastructure.dto.AIEmbeddableField;
import com.ai.infrastructure.dto.AIMetadataField;
import com.ai.infrastructure.dto.AICrudOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Entity Configuration Loader
 * 
 * Loads and parses AI entity configuration from YAML files.
 * Provides configuration lookup for AI processing.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class AIEntityConfigurationLoader {
    
    private final ResourceLoader resourceLoader;
    private final Map<String, AIEntityConfig> entityConfigs = new ConcurrentHashMap<>();
    private final Map<String, Object> globalConfig = new ConcurrentHashMap<>();
    
    @Value("${ai.config.default-file:ai-entity-config.yml}")
    private String defaultConfigFile;
    
    public AIEntityConfigurationLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    @PostConstruct
    public void loadConfiguration() {
        try {
            log.info("Loading AI entity configuration from: {}", defaultConfigFile);
            loadConfigurationFromFile(defaultConfigFile);
            log.info("Successfully loaded configuration for {} entities", entityConfigs.size());
        } catch (Exception e) {
            log.error("Failed to load AI entity configuration", e);
            throw new RuntimeException("Failed to load AI entity configuration", e);
        }
    }
    
    public void loadConfigurationFromFile(String configFile) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + configFile);
            InputStream inputStream = resource.getInputStream();
            
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(inputStream);
            
            // Load global configuration
            if (config.containsKey("ai-config")) {
                globalConfig.putAll((Map<String, Object>) config.get("ai-config"));
            }
            
            // Load entity configurations
            if (config.containsKey("ai-entities")) {
                Map<String, Object> entities = (Map<String, Object>) config.get("ai-entities");
                for (Map.Entry<String, Object> entry : entities.entrySet()) {
                    String entityType = entry.getKey();
                    Map<String, Object> entityConfig = (Map<String, Object>) entry.getValue();
                    AIEntityConfig configObj = parseEntityConfig(entityType, entityConfig);
                    entityConfigs.put(entityType, configObj);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to load configuration from file: {}", configFile, e);
            throw new RuntimeException("Failed to load configuration from file: " + configFile, e);
        }
    }
    
    private AIEntityConfig parseEntityConfig(String entityType, Map<String, Object> config) {
        AIEntityConfig.AIEntityConfigBuilder builder = AIEntityConfig.builder()
            .entityType(entityType)
            .features((List<String>) config.getOrDefault("features", Arrays.asList("embedding", "search")))
            .autoProcess((Boolean) config.getOrDefault("auto-process", true))
            .enableSearch((Boolean) config.getOrDefault("enable-search", true))
            .enableRecommendations((Boolean) config.getOrDefault("enable-recommendations", false))
            .autoEmbedding((Boolean) config.getOrDefault("auto-embedding", true))
            .indexable((Boolean) config.getOrDefault("indexable", true));
        
        // Parse searchable fields
        if (config.containsKey("searchable-fields")) {
            List<Map<String, Object>> searchableFields = (List<Map<String, Object>>) config.get("searchable-fields");
            List<AISearchableField> fields = new ArrayList<>();
            for (Map<String, Object> field : searchableFields) {
                fields.add(AISearchableField.builder()
                    .name((String) field.get("name"))
                    .includeInRAG((Boolean) field.getOrDefault("include-in-rag", true))
                    .enableSemanticSearch((Boolean) field.getOrDefault("enable-semantic-search", true))
                    .weight(((Number) field.getOrDefault("weight", 1.0)).doubleValue())
                    .build());
            }
            builder.searchableFields(fields);
        }
        
        // Parse embeddable fields
        if (config.containsKey("embeddable-fields")) {
            List<Map<String, Object>> embeddableFields = (List<Map<String, Object>>) config.get("embeddable-fields");
            List<AIEmbeddableField> fields = new ArrayList<>();
            for (Map<String, Object> field : embeddableFields) {
                fields.add(AIEmbeddableField.builder()
                    .name((String) field.get("name"))
                    .model((String) field.getOrDefault("model", "text-embedding-3-small"))
                    .autoGenerate((Boolean) field.getOrDefault("auto-generate", true))
                    .includeInSimilarity((Boolean) field.getOrDefault("include-in-similarity", true))
                    .build());
            }
            builder.embeddableFields(fields);
        }
        
        // Parse metadata fields
        if (config.containsKey("metadata-fields")) {
            List<Map<String, Object>> metadataFields = (List<Map<String, Object>>) config.get("metadata-fields");
            List<AIMetadataField> fields = new ArrayList<>();
            for (Map<String, Object> field : metadataFields) {
                fields.add(AIMetadataField.builder()
                    .name((String) field.get("name"))
                    .type((String) field.get("type"))
                    .includeInSearch((Boolean) field.getOrDefault("include-in-search", true))
                    .build());
            }
            builder.metadataFields(fields);
        }
        
        // Parse CRUD operations
        if (config.containsKey("crud-operations")) {
            Map<String, Object> crudOps = (Map<String, Object>) config.get("crud-operations");
            Map<String, AICrudOperation> operations = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : crudOps.entrySet()) {
                String operation = entry.getKey();
                Map<String, Object> opConfig = (Map<String, Object>) entry.getValue();
                operations.put(operation, AICrudOperation.builder()
                    .operation(operation)
                    .generateEmbedding((Boolean) opConfig.getOrDefault("generate-embedding", true))
                    .indexForSearch((Boolean) opConfig.getOrDefault("index-for-search", true))
                    .enableAnalysis((Boolean) opConfig.getOrDefault("enable-analysis", false))
                    .removeFromSearch((Boolean) opConfig.getOrDefault("remove-from-search", true))
                    .cleanupEmbeddings((Boolean) opConfig.getOrDefault("cleanup-embeddings", true))
                    .build());
            }
            builder.crudOperations(operations);
        }
        
        return builder.build();
    }
    
    public AIEntityConfig getEntityConfig(String entityType) {
        return entityConfigs.get(entityType);
    }
    
    public boolean hasEntityConfig(String entityType) {
        return entityConfigs.containsKey(entityType);
    }
    
    public Set<String> getSupportedEntityTypes() {
        return entityConfigs.keySet();
    }
    
    public Object getGlobalConfig(String key) {
        return globalConfig.get(key);
    }
    
    public String getDefaultEmbeddingModel() {
        return (String) globalConfig.getOrDefault("default-embedding-model", "text-embedding-3-small");
    }
    
    public int getDefaultSearchLimit() {
        return ((Number) globalConfig.getOrDefault("default-search-limit", 10)).intValue();
    }
    
    public double getDefaultSimilarityThreshold() {
        return ((Number) globalConfig.getOrDefault("default-similarity-threshold", 0.7)).doubleValue();
    }
}

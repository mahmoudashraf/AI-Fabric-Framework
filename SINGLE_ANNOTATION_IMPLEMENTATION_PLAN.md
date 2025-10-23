# Single Annotation Implementation Plan: Complete Migration & Implementation

## Overview

This document provides a comprehensive implementation plan for the single annotation approach with configuration-driven AI processing, including AICapable and AIProcess annotations with entity-type support and AOP implementation. This plan follows the transformation patterns established in the AI_INFRASTRUCTURE_TRANSFORMATION_PLAN.md and FILE_MIGRATION_CHECKLIST.md.

## Implementation Strategy

### **Phase 1: Core Infrastructure (Two-Level Annotation System)**
### **Phase 2: Configuration System**
### **Phase 3: AOP Implementation**
### **Phase 4: Domain Entity Integration**
### **Phase 5: Domain Service Integration**
### **Phase 6: Auto-Configuration**
### **Phase 7: Testing & Validation**
### **Phase 8: Migration from Current Approach**

---

## Phase 1: Core Infrastructure (Two-Level Annotation System)

### 1.1 Create the Entity-Level Annotation

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/annotation/AICapable.java`

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * AICapable Annotation
 * 
 * Entity-level annotation to enable AI capabilities for classes.
 * AI behavior is defined in the configuration file.
 * This annotation marks an entity as AI-capable and triggers automatic
 * AI processing through AOP when the entity is created, updated, or deleted.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AICapable {
    
    /**
     * Entity type for AI processing
     * Used to lookup configuration in ai-entity-config.yml
     * This is the primary identifier for AI configuration lookup
     */
    String entityType() default "";
    
    /**
     * Configuration file path
     * Default: ai-entity-config.yml
     */
    String configFile() default "ai-entity-config.yml";
    
    /**
     * Enable automatic AI processing
     * Default: true
     * When true, AI processing happens automatically via AOP
     */
    boolean autoProcess() default true;
    
    /**
     * AI features to enable
     * Options: embedding, search, rag, recommendation, validation, analysis, behavioral
     */
    String[] features() default {"embedding", "search"};
    
    /**
     * Enable search capabilities
     * Default: true
     */
    boolean enableSearch() default true;
    
    /**
     * Enable recommendation capabilities
     * Default: false
     */
    boolean enableRecommendations() default false;
    
    /**
     * Enable automatic embedding generation
     * Default: true
     */
    boolean autoEmbedding() default true;
    
    /**
     * Enable indexing for search
     * Default: true
     */
    boolean indexable() default true;
}
```

### 1.2 Create AI Processing Annotation

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/annotation/AIProcess.java`

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * AIProcess Annotation
 * 
 * Method-level annotation for automatic AI processing.
 * Contains both entity type and processing configuration.
 * This annotation is used on service methods to trigger
 * specific AI processing operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIProcess {
    
    /**
     * Entity type for AI processing
     * Used to lookup configuration in ai-entity-config.yml
     * This is the primary identifier for AI configuration lookup
     */
    String entityType() default "";
    
    /**
     * AI processing type
     * Options: create, update, delete, search, analyze, recommend
     */
    String processType() default "create";
    
    /**
     * Enable embedding generation
     * Default: true
     */
    boolean generateEmbedding() default true;
    
    /**
     * Enable search indexing
     * Default: true
     */
    boolean indexForSearch() default true;
    
    /**
     * Enable AI analysis
     * Default: false
     */
    boolean enableAnalysis() default false;
    
    /**
     * Enable behavioral tracking
     * Default: false
     */
    boolean enableBehavioralTracking() default false;
    
    /**
     * Enable content validation
     * Default: false
     */
    boolean enableContentValidation() default false;
    
    /**
     * Enable UI adaptation
     * Default: false
     */
    boolean enableUIAdaptation() default false;
}
```

**Note**: We don't need @AISearchable and @AIEmbeddable annotations because all field-level configuration is handled through the YAML configuration file. The AICapabilityService uses reflection to extract field values based on the YAML configuration, making field-level annotations unnecessary.

---

## Phase 2: Configuration System

### 2.1 Create Configuration File

#### **File**: `ai-infrastructure-module/src/main/resources/ai-entity-config.yml`

```yaml
# AI Entity Configuration
# Defines AI behavior for all entities

ai-entities:
  # Product Entity Configuration
  product:
    entity-type: "product"
    features: ["embedding", "search", "rag", "recommendation"]
    auto-process: true
    enable-search: true
    enable-recommendations: true
    auto-embedding: true
    indexable: true
    
    # Searchable Fields Configuration
    searchable-fields:
      - name: "name"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
      - name: "description"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.8
      - name: "category"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
      - name: "tags"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.4
    
    # Embeddable Fields Configuration
    embeddable-fields:
      - name: "name"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "description"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "category"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    # Metadata Fields Configuration
    metadata-fields:
      - name: "price"
        type: "number"
        include-in-search: true
      - name: "rating"
        type: "number"
        include-in-search: true
      - name: "availability"
        type: "boolean"
        include-in-search: true
    
    # CRUD Operations Configuration
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
        enable-analysis: false

  # User Entity Configuration
  user:
    entity-type: "user"
    features: ["embedding", "search", "rag", "behavioral"]
    auto-process: true
    enable-search: true
    enable-recommendations: false
    auto-embedding: true
    indexable: true
    
    searchable-fields:
      - name: "firstName"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
      - name: "lastName"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
      - name: "email"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.8
      - name: "bio"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.4
    
    embeddable-fields:
      - name: "firstName"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "lastName"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "bio"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    metadata-fields:
      - name: "age"
        type: "number"
        include-in-search: true
      - name: "location"
        type: "string"
        include-in-search: true
      - name: "preferences"
        type: "object"
        include-in-search: true
    
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
        enable-analysis: false

  # Order Entity Configuration
  order:
    entity-type: "order"
    features: ["embedding", "search", "rag", "analysis"]
    auto-process: true
    enable-search: true
    enable-recommendations: false
    auto-embedding: true
    indexable: true
    
    searchable-fields:
      - name: "orderNumber"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.8
      - name: "notes"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
      - name: "status"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.4
    
    embeddable-fields:
      - name: "orderNumber"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "notes"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    metadata-fields:
      - name: "totalAmount"
        type: "number"
        include-in-search: true
      - name: "orderDate"
        type: "date"
        include-in-search: true
      - name: "status"
        type: "string"
        include-in-search: true
    
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
        enable-analysis: false

# Global AI Configuration
ai-config:
  default-embedding-model: "text-embedding-3-small"
  default-search-limit: 10
  default-similarity-threshold: 0.7
  enable-caching: true
  cache-ttl: 3600
  enable-monitoring: true
  enable-health-checks: true
```

### 2.2 Create Configuration Loader

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/config/AIEntityConfigurationLoader.java`

```java
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
```

### 2.3 Create Configuration DTOs

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/AIEntityConfig.java`

```java
package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * AI Entity Configuration
 * 
 * Represents the configuration for an AI-capable entity.
 * Contains all AI processing settings and field configurations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AIEntityConfig {
    
    private String entityType;
    private List<String> features;
    private boolean autoProcess;
    private boolean enableSearch;
    private boolean enableRecommendations;
    private boolean autoEmbedding;
    private boolean indexable;
    
    private List<AISearchableField> searchableFields;
    private List<AIEmbeddableField> embeddableFields;
    private List<AIMetadataField> metadataFields;
    private Map<String, AICrudOperation> crudOperations;
}
```

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/AISearchableField.java`

```java
package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AI Searchable Field Configuration
 * 
 * Represents the configuration for a searchable field.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AISearchableField {
    
    private String name;
    private boolean includeInRAG;
    private boolean enableSemanticSearch;
    private double weight;
}
```

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/AIEmbeddableField.java`

```java
package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AI Embeddable Field Configuration
 * 
 * Represents the configuration for an embeddable field.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AIEmbeddableField {
    
    private String name;
    private String model;
    private boolean autoGenerate;
    private boolean includeInSimilarity;
}
```

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/AIMetadataField.java`

```java
package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AI Metadata Field Configuration
 * 
 * Represents the configuration for a metadata field.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AIMetadataField {
    
    private String name;
    private String type;
    private boolean includeInSearch;
}
```

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/dto/AICrudOperation.java`

```java
package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AI CRUD Operation Configuration
 * 
 * Represents the configuration for a CRUD operation.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AICrudOperation {
    
    private String operation;
    private boolean generateEmbedding;
    private boolean indexForSearch;
    private boolean enableAnalysis;
    private boolean removeFromSearch;
    private boolean cleanupEmbeddings;
}
```

---

## Phase 3: AOP Implementation

### 3.1 Create AI Processing Aspect

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/aspect/AICapableAspect.java`

```java
package com.ai.infrastructure.aspect;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.service.AICapabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * AICapable Aspect
 * 
 * Spring AOP aspect that intercepts methods annotated with @AICapable
 * and triggers automatic AI processing based on configuration.
 * This aspect handles both entity-level and method-level AI processing.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AICapableAspect {
    
    private final AIEntityConfigurationLoader configLoader;
    private final AICapabilityService aiCapabilityService;
    
    @Around("@annotation(aiCapable)")
    public Object processAICapableMethod(ProceedingJoinPoint joinPoint, AICapable aiCapable) throws Throwable {
        try {
            log.debug("Processing AI-capable method: {}", joinPoint.getSignature().getName());
            
            // Get method signature
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            
            // Get entity type from annotation or method name
            String entityType = getEntityType(aiCapable, method);
            
            // Load configuration for entity type
            AIEntityConfig config = configLoader.getEntityConfig(entityType);
            if (config == null) {
                log.warn("No configuration found for entity type: {}", entityType);
                return joinPoint.proceed();
            }
            
            // Check if auto-processing is enabled
            if (!config.isAutoProcess()) {
                log.debug("Auto-processing disabled for entity type: {}", entityType);
                return joinPoint.proceed();
            }
            
            // Process before method execution
            processBeforeMethod(joinPoint, config, entityType);
            
            // Execute the original method
            Object result = joinPoint.proceed();
            
            // Process after method execution
            processAfterMethod(joinPoint, result, config, entityType);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing AI-capable method: {}", joinPoint.getSignature().getName(), e);
            // Don't fail the original method if AI processing fails
            return joinPoint.proceed();
        }
    }
    
    @Around("@annotation(aiProcess)")
    public Object processAIMethod(ProceedingJoinPoint joinPoint, AIProcess aiProcess) throws Throwable {
        try {
            log.debug("Processing AI method: {}", joinPoint.getSignature().getName());
            
            // Get method signature
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            
            // Get entity type from annotation
            String entityType = aiProcess.entityType();
            if (entityType.isEmpty()) {
                log.warn("No entity type specified for AIProcess annotation on method: {}", method.getName());
                return joinPoint.proceed();
            }
            
            // Load configuration for entity type
            AIEntityConfig config = configLoader.getEntityConfig(entityType);
            if (config == null) {
                log.warn("No configuration found for entity type: {}", entityType);
                return joinPoint.proceed();
            }
            
            // Process before method execution
            processBeforeAIMethod(joinPoint, config, entityType, aiProcess);
            
            // Execute the original method
            Object result = joinPoint.proceed();
            
            // Process after method execution
            processAfterAIMethod(joinPoint, result, config, entityType, aiProcess);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing AI method: {}", joinPoint.getSignature().getName(), e);
            // Don't fail the original method if AI processing fails
            return joinPoint.proceed();
        }
    }
    
    private void processBeforeMethod(ProceedingJoinPoint joinPoint, AIEntityConfig config, String entityType) {
        try {
            log.debug("Processing before method for entity type: {}", entityType);
            
            // Get method arguments
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                Object entity = args[0];
                
                // Perform pre-processing based on configuration
                if (config.isAutoEmbedding()) {
                    aiCapabilityService.prepareEntityForAI(entity, entityType, config);
                }
            }
        } catch (Exception e) {
            log.error("Error in before method processing for entity type: {}", entityType, e);
        }
    }
    
    private void processAfterMethod(ProceedingJoinPoint joinPoint, Object result, AIEntityConfig config, String entityType) {
        try {
            log.debug("Processing after method for entity type: {}", entityType);
            
            if (result != null) {
                // Perform post-processing based on configuration
                aiCapabilityService.processEntityAfterAI(result, entityType, config);
            }
        } catch (Exception e) {
            log.error("Error in after method processing for entity type: {}", entityType, e);
        }
    }
    
    private void processBeforeAIMethod(ProceedingJoinPoint joinPoint, AIEntityConfig config, String entityType, AIProcess aiProcess) {
        try {
            log.debug("Processing before AI method for entity type: {}", entityType);
            
            // Get method arguments
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                Object entity = args[0];
                
                // Perform pre-processing based on AIProcess configuration
                if (aiProcess.generateEmbedding()) {
                    aiCapabilityService.prepareEntityForAI(entity, entityType, config);
                }
            }
        } catch (Exception e) {
            log.error("Error in before AI method processing for entity type: {}", entityType, e);
        }
    }
    
    private void processAfterAIMethod(ProceedingJoinPoint joinPoint, Object result, AIEntityConfig config, String entityType, AIProcess aiProcess) {
        try {
            log.debug("Processing after AI method for entity type: {}", entityType);
            
            if (result != null) {
                // Perform post-processing based on AIProcess configuration
                aiCapabilityService.processEntityAfterAI(result, entityType, config);
                
                // Additional processing based on AIProcess settings
                if (aiProcess.enableBehavioralTracking()) {
                    aiCapabilityService.trackBehavioralData(result, entityType, config);
                }
                
                if (aiProcess.enableContentValidation()) {
                    aiCapabilityService.validateContent(result, entityType, config);
                }
                
                if (aiProcess.enableUIAdaptation()) {
                    aiCapabilityService.adaptUI(result, entityType, config);
                }
            }
        } catch (Exception e) {
            log.error("Error in after AI method processing for entity type: {}", entityType, e);
        }
    }
    
    private String getEntityType(AICapable aiCapable, Method method) {
        // First try to get from annotation
        String entityType = aiCapable.entityType();
        if (!entityType.isEmpty()) {
            return entityType;
        }
        
        // Try to infer from method name
        String methodName = method.getName().toLowerCase();
        if (methodName.contains("user")) {
            return "user";
        } else if (methodName.contains("product")) {
            return "product";
        } else if (methodName.contains("order")) {
            return "order";
        }
        
        // Default fallback
        return "unknown";
    }
}
```

### 3.2 Create AOP Configuration

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/config/AIAspectConfiguration.java`

```java
package com.ai.infrastructure.config;

import com.ai.infrastructure.aspect.AICapableAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AI Aspect Configuration
 * 
 * Configuration class for enabling AOP and AI processing aspects.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Configuration
@EnableAspectJAutoProxy
public class AIAspectConfiguration {
    
    @Bean
    public AICapableAspect aiCapableAspect() {
        return new AICapableAspect();
    }
}
```

---

## Phase 4: Domain Entity Integration

### 4.1 Clean Domain Entities

Remove AI coupling from domain entities:

```java
// Before: User.java with AI coupling
@Entity
@Table(name = "users")
@AICapable(entityType = "user")
public class User {
    @Id
    private UUID id;
    
    @AIKnowledge
    private String firstName;
    
    @AIEmbedding
    private String lastName;
    
    private String searchVector;
    private Double behaviorScore;
    private String aiInsights;
    
    // ... other fields
}

// After: User.java cleaned
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;
    
    private String firstName;
    private String lastName;
    private String email;
    
    // ... other domain fields (no AI coupling)
}
```

### 4.2 Create AI Searchable Entity

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/entity/AISearchableEntity.java`

```java
package com.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI Searchable Entity
 * 
 * Generic entity for storing AI search data.
 * This entity is used to store searchable content and embeddings
 * for any AI-capable entity.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Entity
@Table(name = "ai_searchable_entities")
@Data
public class AISearchableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "entity_id", nullable = false)
    private String entityId;
    
    @Column(name = "searchable_content", columnDefinition = "TEXT")
    private String searchableContent;
    
    @Column(name = "search_vector", columnDefinition = "TEXT")
    private String searchVector;
    
    @Column(name = "ai_metadata", columnDefinition = "TEXT")
    private String aiMetadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

---

## Phase 5: Domain Service Integration

### 5.1 Update Domain Services

Add @AICapable and @AIProcess annotations to domain services:

```java
// Before: UserService.java with manual AI processing
@Service
public class UserService {
    
    public User createUser(User user) {
        // Manual AI processing
        if (user.getFirstName() != null) {
            // Generate embedding
            String embedding = aiEmbeddingService.generateEmbedding(user.getFirstName());
            user.setSearchVector(embedding);
        }
        
        // Save user
        return userRepository.save(user);
    }
}

// After: UserService.java with automatic AI processing
@Service
public class UserService {
    
    @AICapable(entityType = "user")
    @AIProcess(entityType = "user", processType = "create")
    public User createUser(User user) {
        // No manual AI processing needed - handled by AOP
        return userRepository.save(user);
    }
    
    @AICapable(entityType = "user")
    @AIProcess(entityType = "user", processType = "update")
    public User updateUser(User user) {
        // No manual AI processing needed - handled by AOP
        return userRepository.save(user);
    }
    
    @AICapable(entityType = "user")
    @AIProcess(entityType = "user", processType = "delete")
    public void deleteUser(UUID userId) {
        // No manual AI processing needed - handled by AOP
        userRepository.deleteById(userId);
    }
}
```

### 5.2 Create AI Capability Service

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/service/AICapabilityService.java`

```java
package com.ai.infrastructure.service;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AIEmbeddableField;
import com.ai.infrastructure.dto.AISearchableField;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI Capability Service
 * 
 * Core service for managing AI capabilities of entities.
 * Handles automatic AI processing based on configuration.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICapabilityService {
    
    private final AIEntityConfigurationLoader configLoader;
    private final AISearchableEntityRepository aiSearchableEntityRepository;
    private final AIEmbeddingService aiEmbeddingService;
    private final AISearchService aiSearchService;
    
    public void prepareEntityForAI(Object entity, String entityType, AIEntityConfig config) {
        try {
            log.debug("Preparing entity for AI processing: {}", entityType);
            
            // Extract searchable content
            String searchableContent = extractSearchableContent(entity, config);
            
            // Generate embeddings if enabled
            if (config.isAutoEmbedding()) {
                generateEmbeddings(entity, config);
            }
            
        } catch (Exception e) {
            log.error("Error preparing entity for AI: {}", entityType, e);
        }
    }
    
    public void processEntityAfterAI(Object entity, String entityType, AIEntityConfig config) {
        try {
            log.debug("Processing entity after AI: {}", entityType);
            
            // Extract entity ID
            String entityId = extractEntityId(entity);
            
            // Extract searchable content
            String searchableContent = extractSearchableContent(entity, config);
            
            // Generate search vector
            String searchVector = generateSearchVector(entity, config);
            
            // Create AI searchable entity
            AISearchableEntity aiEntity = AISearchableEntity.builder()
                .entityType(entityType)
                .entityId(entityId)
                .searchableContent(searchableContent)
                .searchVector(searchVector)
                .aiMetadata(extractMetadata(entity, config))
                .build();
            
            // Save to database
            aiSearchableEntityRepository.save(aiEntity);
            
            log.debug("Successfully processed entity for AI: {}", entityType);
            
        } catch (Exception e) {
            log.error("Error processing entity after AI: {}", entityType, e);
        }
    }
    
    private String extractSearchableContent(Object entity, AIEntityConfig config) {
        StringBuilder content = new StringBuilder();
        
        if (config.getSearchableFields() != null) {
            for (AISearchableField field : config.getSearchableFields()) {
                try {
                    Field entityField = entity.getClass().getDeclaredField(field.getName());
                    entityField.setAccessible(true);
                    Object value = entityField.get(entity);
                    
                    if (value != null) {
                        content.append(value.toString()).append(" ");
                    }
                } catch (Exception e) {
                    log.warn("Could not extract field {} from entity: {}", field.getName(), e.getMessage());
                }
            }
        }
        
        return content.toString().trim();
    }
    
    private String generateSearchVector(Object entity, AIEntityConfig config) {
        try {
            if (config.getEmbeddableFields() != null && !config.getEmbeddableFields().isEmpty()) {
                List<String> texts = new ArrayList<>();
                
                for (AIEmbeddableField field : config.getEmbeddableFields()) {
                    try {
                        Field entityField = entity.getClass().getDeclaredField(field.getName());
                        entityField.setAccessible(true);
                        Object value = entityField.get(entity);
                        
                        if (value != null) {
                            texts.add(value.toString());
                        }
                    } catch (Exception e) {
                        log.warn("Could not extract field {} from entity: {}", field.getName(), e.getMessage());
                    }
                }
                
                if (!texts.isEmpty()) {
                    return aiEmbeddingService.generateEmbedding(String.join(" ", texts));
                }
            }
        } catch (Exception e) {
            log.error("Error generating search vector", e);
        }
        
        return null;
    }
    
    private void generateEmbeddings(Object entity, AIEntityConfig config) {
        // Implementation for generating embeddings
        // This would call the AI embedding service
    }
    
    private String extractEntityId(Object entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(entity);
            return id != null ? id.toString() : UUID.randomUUID().toString();
        } catch (Exception e) {
            log.warn("Could not extract entity ID, generating new one");
            return UUID.randomUUID().toString();
        }
    }
    
    private String extractMetadata(Object entity, AIEntityConfig config) {
        // Implementation for extracting metadata
        return "{}";
    }
    
    public void trackBehavioralData(Object entity, String entityType, AIEntityConfig config) {
        // Implementation for behavioral tracking
    }
    
    public void validateContent(Object entity, String entityType, AIEntityConfig config) {
        // Implementation for content validation
    }
    
    public void adaptUI(Object entity, String entityType, AIEntityConfig config) {
        // Implementation for UI adaptation
    }
}
```

---

## Phase 6: Auto-Configuration

### 6.1 Create Auto-Configuration

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`

```java
package com.ai.infrastructure.config;

import com.ai.infrastructure.aspect.AICapableAspect;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.AIEmbeddingService;
import com.ai.infrastructure.service.AISearchService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AI Infrastructure Auto-Configuration
 * 
 * Auto-configuration for AI infrastructure module.
 * Enables automatic configuration when the module is included.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Configuration
@ConditionalOnClass(AICapableAspect.class)
@EnableAspectJAutoProxy
public class AIInfrastructureAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public AICapableAspect aiCapableAspect() {
        return new AICapableAspect();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AIEntityConfigurationLoader aiEntityConfigurationLoader() {
        return new AIEntityConfigurationLoader(null);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapabilityService aiCapabilityService() {
        return new AICapabilityService(null, null, null, null);
    }
}
```

### 6.2 Create Spring Boot Starter

#### **File**: `ai-infrastructure-module/src/main/resources/META-INF/spring.factories`

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ai.infrastructure.config.AIInfrastructureAutoConfiguration
```

---

## Phase 7: Testing & Validation

### 7.1 Unit Tests

#### **File**: `ai-infrastructure-module/src/test/java/com/ai/infrastructure/aspect/AICapableAspectTest.java`

```java
package com.ai.infrastructure.aspect;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.service.AICapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig
class AICapableAspectTest {
    
    @Mock
    private AIEntityConfigurationLoader configLoader;
    
    @Mock
    private AICapabilityService aiCapabilityService;
    
    private AICapableAspect aspect;
    
    @BeforeEach
    void setUp() {
        aspect = new AICapableAspect(configLoader, aiCapabilityService);
    }
    
    @Test
    void testProcessAICapableMethod() {
        // Test implementation
    }
    
    @Test
    void testProcessAIMethod() {
        // Test implementation
    }
}
```

### 7.2 Integration Tests

#### **File**: `ai-infrastructure-module/src/test/java/com/ai/infrastructure/integration/AIInfrastructureIntegrationTest.java`

```java
package com.ai.infrastructure.integration;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "ai.config.default-file=test-ai-entity-config.yml"
})
class AIInfrastructureIntegrationTest {
    
    @Test
    void testAICapableAnnotationProcessing() {
        // Test AI-capable annotation processing
    }
    
    @Test
    void testAIProcessAnnotationProcessing() {
        // Test AI process annotation processing
    }
    
    @Test
    void testConfigurationLoading() {
        // Test configuration loading
    }
}
```

---

## Phase 8: Migration from Current Approach

### 8.1 Current State Analysis

Based on the AI_INFRASTRUCTURE_TRANSFORMATION_PLAN.md and FILE_MIGRATION_CHECKLIST.md, the current system has:

#### **Existing AI Infrastructure Module**
- **Location**: `/workspace/ai-infrastructure-module/`
- **Type**: Spring Boot Starter
- **Current Features**: Basic AI services, RAG capabilities, vector search
- **Dependencies**: OpenAI, Lucene, Spring Boot

#### **Backend Module AI Services**
- **Location**: `/workspace/backend/src/main/java/com/easyluxury/ai/`
- **Current Services**: 15+ AI services with domain coupling
- **Issues**: Tightly coupled to domain entities, not reusable

#### **Domain Entities with AI Coupling**
- **User**: Mixed domain/AI fields
- **Product**: Mixed domain/AI fields  
- **Order**: Mixed domain/AI fields
- **UserBehavior**: Primarily AI-specific
- **AIProfile**: Primarily AI-specific

### 8.2 Migration Strategy

#### **Phase 8.1: Move Generic AI Services**

Following the FILE_MIGRATION_CHECKLIST.md, move generic AI services from backend to AI infrastructure:

```bash
# Move generic AI services to AI infrastructure
mv backend/src/main/java/com/easyluxury/ai/service/BehaviorTrackingService.java \
   ai-infrastructure-module/src/main/java/com/ai/infrastructure/behavior/GenericBehaviorTrackingService.java

mv backend/src/main/java/com/easyluxury/ai/service/ContentValidationService.java \
   ai-infrastructure-module/src/main/java/com/ai/infrastructure/validation/GenericContentValidationService.java

mv backend/src/main/java/com/easyluxury/ai/service/UIAdaptationService.java \
   ai-infrastructure-module/src/main/java/com/ai/infrastructure/personalization/GenericUIAdaptationService.java

mv backend/src/main/java/com/easyluxury/ai/service/RecommendationEngine.java \
   ai-infrastructure-module/src/main/java/com/ai/infrastructure/recommendation/GenericRecommendationEngine.java
```

#### **Phase 8.2: Move AI-Specific Entities**

Move AI-specific entities from backend to AI infrastructure:

```bash
# Move AI entities to AI infrastructure
mv backend/src/main/java/com/easyluxury/entity/UserBehavior.java \
   ai-infrastructure-module/src/main/java/com/ai/infrastructure/entity/Behavior.java

mv backend/src/main/java/com/easyluxury/entity/AIProfile.java \
   ai-infrastructure-module/src/main/java/com/ai/infrastructure/entity/AIProfile.java
```

#### **Phase 8.3: Clean Domain Entities**

Remove AI coupling from domain entities:

```java
// Before: User.java with AI coupling
@Entity
@Table(name = "users")
@AICapable(entityType = "user")
public class User {
    @Id
    private UUID id;
    
    @AIKnowledge
    private String firstName;
    
    @AIEmbedding
    private String lastName;
    
    private String searchVector;
    private Double behaviorScore;
    private String aiInsights;
    
    // ... other fields
}

// After: User.java cleaned
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;
    
    private String firstName;
    private String lastName;
    private String email;
    
    // ... other domain fields (no AI coupling)
}
```

#### **Phase 8.4: Update Domain Services**

Add @AICapable and @AIProcess annotations to domain services:

```java
// Before: UserService.java with manual AI processing
@Service
public class UserService {
    
    public User createUser(User user) {
        // Manual AI processing
        if (user.getFirstName() != null) {
            // Generate embedding
            String embedding = aiEmbeddingService.generateEmbedding(user.getFirstName());
            user.setSearchVector(embedding);
        }
        
        // Save user
        return userRepository.save(user);
    }
}

// After: UserService.java with automatic AI processing
@Service
public class UserService {
    
    @AICapable(entityType = "user")
    @AIProcess(entityType = "user", processType = "create")
    public User createUser(User user) {
        // No manual AI processing needed - handled by AOP
        return userRepository.save(user);
    }
    
    @AICapable(entityType = "user")
    @AIProcess(entityType = "user", processType = "update")
    public User updateUser(User user) {
        // No manual AI processing needed - handled by AOP
        return userRepository.save(user);
    }
    
    @AICapable(entityType = "user")
    @AIProcess(entityType = "user", processType = "delete")
    public void deleteUser(UUID userId) {
        // No manual AI processing needed - handled by AOP
        userRepository.deleteById(userId);
    }
}
```

#### **Phase 8.5: Create Domain-Specific Adapters**

Create adapters to bridge between domain and AI infrastructure:

```java
// Backend - User AI Adapter
@Service
public class UserAIAdapter {
    
    private final GenericBehaviorTrackingService behaviorTrackingService;
    private final GenericContentValidationService contentValidationService;
    private final GenericUIAdaptationService uiAdaptationService;
    private final UserRepository userRepository;
    
    public void trackUserBehavior(UUID userId, String behaviorType, String entityType, String entityId, String action, String context, Map<String, Object> metadata) {
        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        
        // Delegate to generic service
        behaviorTrackingService.trackBehavior(userId, behaviorType, entityType, entityId, action, context, metadata);
    }
    
    public TextValidationResult validateUserContent(String content, String contentType, String validationLevel) {
        return contentValidationService.validateTextContent(content, contentType, validationLevel);
    }
    
    public Map<String, Object> generatePersonalizedUI(UUID userId) {
        // Get user data
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Delegate to generic service
        return uiAdaptationService.generatePersonalizedUIConfig(userId, user.getFirstName(), user.getLastName(), user.getEmail());
    }
}
```

#### **Phase 8.6: Create Configuration Files**

Create YAML configuration files for each domain:

```yaml
# backend/src/main/resources/ai-entity-config.yml
ai-entities:
  user:
    entity-type: "user"
    features: ["embedding", "search", "rag", "behavioral"]
    auto-process: true
    enable-search: true
    enable-recommendations: false
    auto-embedding: true
    indexable: true
    
    searchable-fields:
      - name: "firstName"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
      - name: "lastName"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
      - name: "email"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.8
    
    embeddable-fields:
      - name: "firstName"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "lastName"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
        enable-analysis: false

  product:
    entity-type: "product"
    features: ["embedding", "search", "rag", "recommendation"]
    auto-process: true
    enable-search: true
    enable-recommendations: true
    auto-embedding: true
    indexable: true
    
    searchable-fields:
      - name: "name"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
      - name: "description"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.8
      - name: "category"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
    
    embeddable-fields:
      - name: "name"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "description"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
        enable-analysis: false

  order:
    entity-type: "order"
    features: ["embedding", "search", "rag", "analysis"]
    auto-process: true
    enable-search: true
    enable-recommendations: false
    auto-embedding: true
    indexable: true
    
    searchable-fields:
      - name: "orderNumber"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.8
      - name: "notes"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.6
      - name: "status"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.4
    
    embeddable-fields:
      - name: "orderNumber"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
      - name: "notes"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
        enable-analysis: false
```

---

## Implementation Checklist

### **Phase 1: Core Infrastructure**
- [ ] Create `@AICapable` annotation
- [ ] Create `@AIProcess` annotation
- [ ] Add annotation processing dependencies
- [ ] Test annotation compilation

### **Phase 2: Configuration System**
- [ ] Create YAML configuration schema
- [ ] Implement `AIEntityConfigurationLoader`
- [ ] Create configuration DTOs
- [ ] Test configuration loading

### **Phase 3: AOP Implementation**
- [ ] Implement `AICapableAspect`
- [ ] Create AOP configuration
- [ ] Test aspect execution
- [ ] Test automatic AI processing

### **Phase 4: Domain Entity Integration**
- [ ] Clean domain entities
- [ ] Create `AISearchableEntity`
- [ ] Test entity separation

### **Phase 5: Domain Service Integration**
- [ ] Update domain services with annotations
- [ ] Create `AICapabilityService`
- [ ] Test service integration

### **Phase 6: Auto-Configuration**
- [ ] Create auto-configuration
- [ ] Create Spring Boot starter
- [ ] Test auto-configuration

### **Phase 7: Testing & Validation**
- [ ] Create unit tests
- [ ] Create integration tests
- [ ] Test complete AI processing flow
- [ ] Validate configuration-driven behavior

### **Phase 8: Migration from Current Approach**
- [ ] Move generic AI services
- [ ] Move AI-specific entities
- [ ] Clean domain entities
- [ ] Update domain services
- [ ] Create domain adapters
- [ ] Create configuration files
- [ ] Test migration

## Summary

This implementation plan provides a complete roadmap for implementing the single annotation approach with configuration-driven AI processing. The key benefits are:

1. **Single Annotation**: Only `@AICapable` annotation needed per entity
2. **Configuration-Driven**: All AI behavior defined in YAML configuration
3. **Automatic Processing**: AI processing happens automatically via AOP aspects
4. **Clean Domain Code**: No AI coupling in domain entities
5. **Easy Maintenance**: AI behavior can be modified without code changes
6. **Spring Integration**: Leverages Spring AOP for automatic processing
7. **Entity-Type Support**: AIProcess annotation includes entity-type for configuration lookup
8. **AOP Implementation**: Comprehensive AOP implementation for automatic AI processing
9. **Migration Path**: Clear migration path from current approach following established patterns

The result is a truly generic, reusable AI infrastructure that can enable AI capabilities in any application with minimal code and maximum flexibility.
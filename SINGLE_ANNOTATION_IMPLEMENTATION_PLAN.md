# Single Annotation Implementation Plan: Complete Migration & Implementation

## Overview

This document provides a comprehensive implementation plan for the single annotation approach with configuration-driven AI processing, including all the parts discussed in our conversation.

## Implementation Strategy

### **Phase 1: Core Infrastructure (Single Annotation System)**
### **Phase 2: Configuration System**
### **Phase 3: AI Processing Aspect**
### **Phase 4: Domain Entity Integration**
### **Phase 5: Domain Service Integration**
### **Phase 6: Auto-Configuration**
### **Phase 7: Testing & Validation**

---

## Phase 1: Core Infrastructure (Single Annotation System)

### 1.1 Create the Single Annotation

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/annotation/AICapable.java`

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AICapable Annotation
 * 
 * Single annotation to enable AI capabilities for any entity or method.
 * AI behavior is defined in the configuration file.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AICapable {
    
    /**
     * Entity type for AI processing
     * Used to lookup configuration in ai-entity-config.yml
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
     */
    boolean autoProcess() default true;
    
    /**
     * AI features to enable
     * Options: embedding, search, rag, recommendation, validation, analysis
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

### 1.2 Create AI Processing Annotations

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/annotation/AIProcess.java`

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AIProcess Annotation
 * 
 * Marks methods for automatic AI processing.
 * Used in combination with @AICapable for method-level AI processing.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIProcess {
    
    /**
     * AI processing type
     * Options: create, update, delete, search, analyze
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
}
```

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/annotation/AISearchable.java`

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AISearchable Annotation
 * 
 * Marks fields as searchable for AI processing.
 * Used for field-level AI configuration.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AISearchable {
    
    /**
     * Field name for AI processing
     */
    String fieldName() default "";
    
    /**
     * Include in RAG processing
     * Default: true
     */
    boolean includeInRAG() default true;
    
    /**
     * Enable semantic search
     * Default: true
     */
    boolean enableSemanticSearch() default true;
    
    /**
     * Field weight for search relevance
     * Default: 1.0
     */
    double weight() default 1.0;
}
```

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/annotation/AIEmbeddable.java`

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AIEmbeddable Annotation
 * 
 * Marks fields as embeddable for AI processing.
 * Used for field-level embedding configuration.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIEmbeddable {
    
    /**
     * Field name for AI processing
     */
    String fieldName() default "";
    
    /**
     * Embedding model to use
     * Default: text-embedding-3-small
     */
    String model() default "text-embedding-3-small";
    
    /**
     * Auto-generate embedding
     * Default: true
     */
    boolean autoGenerate() default true;
    
    /**
     * Include in similarity search
     * Default: true
     */
    boolean includeInSimilarity() default true;
}
```

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

## Phase 3: AI Processing Aspect

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
            
            // Get entity type from method name or class
            String entityType = getEntityTypeFromMethod(method);
            
            // Load configuration for entity type
            AIEntityConfig config = configLoader.getEntityConfig(entityType);
            if (config == null) {
                log.warn("No configuration found for entity type: {}", entityType);
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
            log.error("Error processing AI method: {}", joinPoint.getSignature().getName(), e);
            // Don't fail the original method if AI processing fails
            return joinPoint.proceed();
        }
    }
    
    private String getEntityType(AICapable aiCapable, Method method) {
        if (!aiCapable.entityType().isEmpty()) {
            return aiCapable.entityType();
        }
        return getEntityTypeFromMethod(method);
    }
    
    private String getEntityTypeFromMethod(Method method) {
        String methodName = method.getName().toLowerCase();
        
        if (methodName.contains("product")) {
            return "product";
        } else if (methodName.contains("user")) {
            return "user";
        } else if (methodName.contains("order")) {
            return "order";
        }
        
        // Default to method name
        return methodName;
    }
    
    private void processBeforeMethod(ProceedingJoinPoint joinPoint, AIEntityConfig config, String entityType) {
        try {
            log.debug("Processing before method for entity type: {}", entityType);
            
            // Extract entity data from method arguments
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                Object entity = args[0];
                
                // Validate entity if needed
                if (config.getFeatures().contains("validation")) {
                    aiCapabilityService.validateEntity(entity, config);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing before method for entity type: {}", entityType, e);
        }
    }
    
    private void processAfterMethod(ProceedingJoinPoint joinPoint, Object result, AIEntityConfig config, String entityType) {
        try {
            log.debug("Processing after method for entity type: {}", entityType);
            
            if (result != null) {
                // Determine operation type
                String operation = getOperationType(joinPoint);
                
                // Get CRUD operation configuration
                var crudOp = config.getCrudOperations().get(operation);
                if (crudOp == null) {
                    log.warn("No CRUD operation configuration found for: {}", operation);
                    return;
                }
                
                // Process entity based on configuration
                if (crudOp.isGenerateEmbedding()) {
                    aiCapabilityService.generateEmbeddings(result, config);
                }
                
                if (crudOp.isIndexForSearch()) {
                    aiCapabilityService.indexForSearch(result, config);
                }
                
                if (crudOp.isEnableAnalysis()) {
                    aiCapabilityService.analyzeEntity(result, config);
                }
                
                if (crudOp.isRemoveFromSearch()) {
                    aiCapabilityService.removeFromSearch(result, config);
                }
                
                if (crudOp.isCleanupEmbeddings()) {
                    aiCapabilityService.cleanupEmbeddings(result, config);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing after method for entity type: {}", entityType, e);
        }
    }
    
    private String getOperationType(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName().toLowerCase();
        
        if (methodName.startsWith("create") || methodName.startsWith("save") || methodName.startsWith("add")) {
            return "create";
        } else if (methodName.startsWith("update") || methodName.startsWith("modify") || methodName.startsWith("edit")) {
            return "update";
        } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "delete";
        } else if (methodName.startsWith("search") || methodName.startsWith("find")) {
            return "search";
        } else if (methodName.startsWith("analyze")) {
            return "analyze";
        }
        
        return "create"; // Default
    }
}
```

### 3.2 Create AI Capability Service

#### **File**: `ai-infrastructure-module/src/main/java/com/ai/infrastructure/service/AICapabilityService.java`

```java
package com.ai.infrastructure.service;

import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AISearchableField;
import com.ai.infrastructure.dto.AIEmbeddableField;
import com.ai.infrastructure.dto.AIMetadataField;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
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
                boolean isValid = aiCoreService.validateContent(searchableContent);
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
            List<Double> embeddings = embeddingService.generateEmbedding(embeddableContent);
            
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
            List<Double> embeddings = embeddingService.generateEmbedding(searchableContent);
            
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
            String analysis = aiCoreService.analyzeContent(content, config.getEntityType());
            
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
            
            for (AISearchableField field : config.getSearchableFields()) {
                String value = getFieldValue(entity, field.getName());
                if (value != null && !value.trim().isEmpty()) {
                    contentParts.add(value);
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
            
            for (AIEmbeddableField field : config.getEmbeddableFields()) {
                String value = getFieldValue(entity, field.getName());
                if (value != null && !value.trim().isEmpty()) {
                    contentParts.add(value);
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
            
            AISearchableEntity searchableEntity = AISearchableEntity.builder()
                .entityType(config.getEntityType())
                .entityId(entityId)
                .searchableContent(content)
                .embeddings(embeddings)
                .metadata(extractMetadata(entity, config))
                .build();
            
            searchableEntityRepository.save(searchableEntity);
            
        } catch (Exception e) {
            log.error("Error storing searchable entity", e);
        }
    }
    
    private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            for (AIMetadataField field : config.getMetadataFields()) {
                String value = getFieldValue(entity, field.getName());
                if (value != null && !value.trim().isEmpty()) {
                    metadata.put(field.getName(), value);
                }
            }
        } catch (Exception e) {
            log.error("Error extracting metadata", e);
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
            
            // Store analysis result in searchable entity
            Optional<AISearchableEntity> existing = searchableEntityRepository
                .findByEntityTypeAndEntityId(config.getEntityType(), entityId);
            
            if (existing.isPresent()) {
                AISearchableEntity entityToUpdate = existing.get();
                entityToUpdate.setAiAnalysis(analysis);
                searchableEntityRepository.save(entityToUpdate);
            }
            
        } catch (Exception e) {
            log.error("Error storing analysis result", e);
        }
    }
}
```

---

## Phase 4: Domain Entity Integration

### 4.1 Clean Domain Entities

#### **File**: `backend/src/main/java/com/easyluxury/entity/Product.java` (Updated)

```java
package com.easyluxury.entity;

import com.ai.infrastructure.annotation.AICapable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Product Entity
 * 
 * Clean domain entity with single AI annotation.
 * AI behavior is defined in configuration file.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(entityType = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private BigDecimal price;
    
    @Column(nullable = false)
    private String category;
    
    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private List<String> tags;
    
    @Column(nullable = false)
    private boolean available;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

#### **File**: `backend/src/main/java/com/easyluxury/entity/User.java` (Updated)

```java
package com.easyluxury.entity;

import com.ai.infrastructure.annotation.AICapable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Entity
 * 
 * Clean domain entity with single AI annotation.
 * AI behavior is defined in configuration file.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(entityType = "user")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(columnDefinition = "TEXT")
    private String bio;
    
    @Column
    private Integer age;
    
    @Column
    private String location;
    
    @ElementCollection
    @CollectionTable(name = "user_preferences", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "preference")
    private List<String> preferences;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

#### **File**: `backend/src/main/java/com/easyluxury/entity/Order.java` (Updated)

```java
package com.easyluxury.entity;

import com.ai.infrastructure.annotation.AICapable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity
 * 
 * Clean domain entity with single AI annotation.
 * AI behavior is defined in configuration file.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(entityType = "order")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String orderNumber;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private BigDecimal totalAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @ElementCollection
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items;
    
    @Column(name = "order_date")
    private LocalDateTime orderDate;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (orderDate == null) {
            orderDate = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum OrderStatus {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }
    
    @Embeddable
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}
```

---

## Phase 5: Domain Service Integration

### 5.1 Update Domain Services

#### **File**: `backend/src/main/java/com/easyluxury/service/ProductService.java` (Updated)

```java
package com.easyluxury.service;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.easyluxury.entity.Product;
import com.easyluxury.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Product Service
 * 
 * Domain service with AI capabilities enabled via annotations.
 * AI processing happens automatically via AOP aspects.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    
    /**
     * Create a new product
     * AI processing: Automatic embedding generation, search indexing
     */
    @AICapable(entityType = "product")
    @AIProcess(processType = "create")
    @Transactional
    public Product createProduct(Product product) {
        log.info("Creating product: {}", product.getName());
        return productRepository.save(product);
    }
    
    /**
     * Update an existing product
     * AI processing: Automatic embedding regeneration, search re-indexing, analysis
     */
    @AICapable(entityType = "product")
    @AIProcess(processType = "update")
    @Transactional
    public Product updateProduct(String id, Product product) {
        log.info("Updating product: {}", id);
        
        Product existingProduct = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        
        existingProduct.setName(product.getName());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setCategory(product.getCategory());
        existingProduct.setTags(product.getTags());
        existingProduct.setAvailable(product.isAvailable());
        
        return productRepository.save(existingProduct);
    }
    
    /**
     * Delete a product
     * AI processing: Automatic removal from search index, cleanup
     */
    @AICapable(entityType = "product")
    @AIProcess(processType = "delete")
    @Transactional
    public void deleteProduct(String id) {
        log.info("Deleting product: {}", id);
        
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        
        productRepository.delete(product);
    }
    
    /**
     * Find product by ID
     */
    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }
    
    /**
     * Find all products
     */
    public List<Product> findAll() {
        return productRepository.findAll();
    }
    
    /**
     * Find products by category
     */
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }
    
    /**
     * Find available products
     */
    public List<Product> findAvailableProducts() {
        return productRepository.findByAvailableTrue();
    }
}
```

#### **File**: `backend/src/main/java/com/easyluxury/service/UserService.java` (Updated)

```java
package com.easyluxury.service;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.easyluxury.entity.User;
import com.easyluxury.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * User Service
 * 
 * Domain service with AI capabilities enabled via annotations.
 * AI processing happens automatically via AOP aspects.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    
    /**
     * Create a new user
     * AI processing: Automatic embedding generation, search indexing
     */
    @AICapable(entityType = "user")
    @AIProcess(processType = "create")
    @Transactional
    public User createUser(User user) {
        log.info("Creating user: {}", user.getEmail());
        return userRepository.save(user);
    }
    
    /**
     * Update an existing user
     * AI processing: Automatic embedding regeneration, search re-indexing, analysis
     */
    @AICapable(entityType = "user")
    @AIProcess(processType = "update")
    @Transactional
    public User updateUser(String id, User user) {
        log.info("Updating user: {}", id);
        
        User existingUser = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found: " + id));
        
        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());
        existingUser.setEmail(user.getEmail());
        existingUser.setBio(user.getBio());
        existingUser.setAge(user.getAge());
        existingUser.setLocation(user.getLocation());
        existingUser.setPreferences(user.getPreferences());
        
        return userRepository.save(existingUser);
    }
    
    /**
     * Delete a user
     * AI processing: Automatic removal from search index, cleanup
     */
    @AICapable(entityType = "user")
    @AIProcess(processType = "delete")
    @Transactional
    public void deleteUser(String id) {
        log.info("Deleting user: {}", id);
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found: " + id));
        
        userRepository.delete(user);
    }
    
    /**
     * Find user by ID
     */
    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }
    
    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * Find all users
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }
}
```

#### **File**: `backend/src/main/java/com/easyluxury/service/OrderService.java` (Updated)

```java
package com.easyluxury.service;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.easyluxury.entity.Order;
import com.easyluxury.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Order Service
 * 
 * Domain service with AI capabilities enabled via annotations.
 * AI processing happens automatically via AOP aspects.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    
    /**
     * Create a new order
     * AI processing: Automatic embedding generation, search indexing
     */
    @AICapable(entityType = "order")
    @AIProcess(processType = "create")
    @Transactional
    public Order createOrder(Order order) {
        log.info("Creating order: {}", order.getOrderNumber());
        return orderRepository.save(order);
    }
    
    /**
     * Update an existing order
     * AI processing: Automatic embedding regeneration, search re-indexing, analysis
     */
    @AICapable(entityType = "order")
    @AIProcess(processType = "update")
    @Transactional
    public Order updateOrder(String id, Order order) {
        log.info("Updating order: {}", id);
        
        Order existingOrder = orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        
        existingOrder.setOrderNumber(order.getOrderNumber());
        existingOrder.setUserId(order.getUserId());
        existingOrder.setTotalAmount(order.getTotalAmount());
        existingOrder.setStatus(order.getStatus());
        existingOrder.setNotes(order.getNotes());
        existingOrder.setItems(order.getItems());
        
        return orderRepository.save(existingOrder);
    }
    
    /**
     * Delete an order
     * AI processing: Automatic removal from search index, cleanup
     */
    @AICapable(entityType = "order")
    @AIProcess(processType = "delete")
    @Transactional
    public void deleteOrder(String id) {
        log.info("Deleting order: {}", id);
        
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        
        orderRepository.delete(order);
    }
    
    /**
     * Find order by ID
     */
    public Optional<Order> findById(String id) {
        return orderRepository.findById(id);
    }
    
    /**
     * Find order by order number
     */
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }
    
    /**
     * Find orders by user ID
     */
    public List<Order> findByUserId(String userId) {
        return orderRepository.findByUserId(userId);
    }
    
    /**
     * Find all orders
     */
    public List<Order> findAll() {
        return orderRepository.findAll();
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
import com.ai.infrastructure.service.AIEntityConfigurationLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AI Infrastructure Auto-Configuration
 * 
 * Auto-configures AI infrastructure components when the module is included.
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
    public AIEntityConfigurationLoader aiEntityConfigurationLoader() {
        return new AIEntityConfigurationLoader(null);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapabilityService aiCapabilityService() {
        return new AICapabilityService(null, null, null);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapableAspect aiCapableAspect() {
        return new AICapableAspect(null, null);
    }
}
```

### 6.2 Create Spring Boot Starter

#### **File**: `ai-infrastructure-module/src/main/resources/META-INF/spring.factories`

```properties
# Auto-configuration
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ai.infrastructure.config.AIInfrastructureAutoConfiguration
```

---

## Phase 7: Testing & Validation

### 7.1 Create Integration Tests

#### **File**: `ai-infrastructure-module/src/test/java/com/ai/infrastructure/AIInfrastructureIntegrationTest.java`

```java
package com.ai.infrastructure;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.service.AICapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Infrastructure Integration Test
 * 
 * Tests the complete AI infrastructure functionality.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "ai.config.default-file=test-ai-entity-config.yml"
})
class AIInfrastructureIntegrationTest {
    
    @Test
    void testConfigurationLoading() {
        // Test configuration loading
        AIEntityConfigurationLoader loader = new AIEntityConfigurationLoader(null);
        assertNotNull(loader);
    }
    
    @Test
    void testAICapabilityService() {
        // Test AI capability service
        AICapabilityService service = new AICapabilityService(null, null, null);
        assertNotNull(service);
    }
    
    @Test
    void testAICapableAnnotation() {
        // Test @AICapable annotation
        AICapable annotation = TestEntity.class.getAnnotation(AICapable.class);
        assertNotNull(annotation);
        assertEquals("test", annotation.entityType());
    }
    
    @Test
    void testAIProcessAnnotation() {
        // Test @AIProcess annotation
        AIProcess annotation = TestService.class.getMethod("testMethod").getAnnotation(AIProcess.class);
        assertNotNull(annotation);
        assertEquals("create", annotation.processType());
    }
    
    @AICapable(entityType = "test")
    static class TestEntity {
        private String id;
        private String name;
    }
    
    static class TestService {
        @AIProcess(processType = "create")
        public void testMethod() {
            // Test method
        }
    }
}
```

### 7.2 Create Backend Integration Tests

#### **File**: `backend/src/test/java/com/easyluxury/ai/AIAnnotationIntegrationTest.java`

```java
package com.easyluxury.ai;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.easyluxury.entity.Product;
import com.easyluxury.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Annotation Integration Test
 * 
 * Tests the AI annotation system in the backend.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "ai.config.default-file=ai-entity-config.yml"
})
class AIAnnotationIntegrationTest {
    
    @Test
    void testProductEntityAnnotation() {
        // Test Product entity has @AICapable annotation
        AICapable annotation = Product.class.getAnnotation(AICapable.class);
        assertNotNull(annotation);
        assertEquals("product", annotation.entityType());
    }
    
    @Test
    void testProductServiceAnnotations() {
        // Test ProductService methods have AI annotations
        try {
            var createMethod = ProductService.class.getMethod("createProduct", Product.class);
            var createAnnotation = createMethod.getAnnotation(AICapable.class);
            assertNotNull(createAnnotation);
            assertEquals("product", createAnnotation.entityType());
            
            var updateMethod = ProductService.class.getMethod("updateProduct", String.class, Product.class);
            var updateAnnotation = updateMethod.getAnnotation(AICapable.class);
            assertNotNull(updateAnnotation);
            assertEquals("product", updateAnnotation.entityType());
            
        } catch (NoSuchMethodException e) {
            fail("Method not found: " + e.getMessage());
        }
    }
}
```

---

## Implementation Checklist

### **Phase 1: Core Infrastructure**
- [ ] Create `@AICapable` annotation
- [ ] Create `@AIProcess` annotation
- [ ] Create `@AISearchable` annotation
- [ ] Create `@AIEmbeddable` annotation
- [ ] Test annotations

### **Phase 2: Configuration System**
- [ ] Create `ai-entity-config.yml` configuration file
- [ ] Create `AIEntityConfigurationLoader` class
- [ ] Create configuration DTOs (`AIEntityConfig`, `AISearchableField`, etc.)
- [ ] Test configuration loading

### **Phase 3: AI Processing Aspect**
- [ ] Create `AICapableAspect` class
- [ ] Create `AICapabilityService` class
- [ ] Test aspect functionality

### **Phase 4: Domain Entity Integration**
- [ ] Clean `Product` entity (remove old AI annotations)
- [ ] Clean `User` entity (remove old AI annotations)
- [ ] Clean `Order` entity (remove old AI annotations)
- [ ] Add single `@AICapable` annotation to entities
- [ ] Test entity annotations

### **Phase 5: Domain Service Integration**
- [ ] Update `ProductService` with AI annotations
- [ ] Update `UserService` with AI annotations
- [ ] Update `OrderService` with AI annotations
- [ ] Test service annotations

### **Phase 6: Auto-Configuration**
- [ ] Create `AIInfrastructureAutoConfiguration` class
- [ ] Create `spring.factories` file
- [ ] Test auto-configuration

### **Phase 7: Testing & Validation**
- [ ] Create integration tests
- [ ] Create backend integration tests
- [ ] Test complete AI processing flow
- [ ] Validate configuration-driven behavior

## Summary

This implementation plan provides a complete roadmap for implementing the single annotation approach with configuration-driven AI processing. The key benefits are:

1. **Single Annotation**: Only `@AICapable` annotation needed per entity
2. **Configuration-Driven**: All AI behavior defined in YAML configuration
3. **Automatic Processing**: AI processing happens automatically via AOP aspects
4. **Clean Domain Code**: No AI coupling in domain entities
5. **Easy Maintenance**: AI behavior can be modified without code changes
6. **Spring Integration**: Leverages Spring AOP for automatic processing

The result is a truly generic, reusable AI infrastructure that can enable AI capabilities in any application with minimal code and maximum flexibility.
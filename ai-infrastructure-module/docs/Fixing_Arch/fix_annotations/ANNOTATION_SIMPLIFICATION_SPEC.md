# AI Annotations Simplification Specification

**Status:** Approved for Implementation  
**Version:** 1.0  
**Date:** January 2026

---

## Executive Summary

Simplify AI field annotations from 360+ line complex definitions to pure marker annotations. Configuration moves to YAML with annotation auto-discovery.

**Key Changes:**
- `@AIEmbedding`: 50+ attributes → **0 attributes** (pure marker)
- `@AIKnowledge`: 100+ attributes → **0 attributes** (pure marker)
- YAML config: Required → **Optional** (auto-discovery from annotations)
- Single source of truth: Annotations define WHAT, YAML defines HOW (optional)

---

## 1. Current State (Problems)

### 1.1 Redundant Configuration

```java
// Problem: Must specify in TWO places

// Place 1: Annotation
@AIEmbedding(weight = 1.0, chunkingStrategy = "sentence", ...)
private String title;

// Place 2: YAML (redundant!)
embeddable-fields:
  - name: "title"
    weight: 1.0
```

### 1.2 Bloated Annotations (Dead Code)

```java
// @AIEmbedding has 50+ attributes - NONE are used!
@AIEmbedding(
    weight, chunkingStrategy, maxChunkSize, dimension,
    similarityThreshold, similarityMetric, indexable,
    cacheable, cacheTtlSeconds, compressible, quantizable,
    prunable, clusterable, reducible, scalable, ...
    // 360 lines of unused attributes
)
```

### 1.3 No Auto-Discovery

- `AICapableProcessor.getEmbeddingFields()` exists but is NEVER called
- Annotations are read but not used in actual flow
- YAML is the only actual source of truth

---

## 2. Recommended Design

### 2.1 Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Annotation = Intent** | Presence of annotation means "include this" |
| **YAML = Configuration** | Optional overrides for advanced tuning |
| **Auto-Discovery** | Framework scans annotations automatically |
| **DRY** | No redundant specification |
| **KISS** | Simplest possible design |

### 2.2 Annotation Definitions

#### `@AICapable` (Entity-Level) - KEEP WITH SIMPLIFICATION

```java
package com.ai.infrastructure.annotation;

import com.ai.infrastructure.indexing.IndexingStrategy;
import java.lang.annotation.*;

/**
 * Marks an entity class as AI-enabled.
 * 
 * <p>This is the primary annotation that enables AI capabilities for an entity.
 * Field-level annotations (@AIEmbedding, @AIKnowledge) are auto-discovered
 * when this annotation is present.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Entity
 * @AICapable(entityType = "product")
 * public class Product {
 *     @AIEmbedding
 *     private String name;
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AICapable {
    
    /**
     * Unique entity type identifier for AI processing.
     * Used to lookup configuration and store in vector database.
     * 
     * @return the entity type (required)
     */
    String entityType();
    
    /**
     * Default indexing strategy for all operations.
     * Can be overridden per-operation or via @AIProcess.
     * 
     * @return indexing strategy (default: ASYNC)
     */
    IndexingStrategy indexingStrategy() default IndexingStrategy.ASYNC;
    
    /**
     * Override indexing strategy for CREATE operations.
     * AUTO inherits from indexingStrategy().
     */
    IndexingStrategy onCreateStrategy() default IndexingStrategy.AUTO;
    
    /**
     * Override indexing strategy for UPDATE operations.
     * AUTO inherits from indexingStrategy().
     */
    IndexingStrategy onUpdateStrategy() default IndexingStrategy.AUTO;
    
    /**
     * Override indexing strategy for DELETE operations.
     * AUTO inherits from indexingStrategy().
     */
    IndexingStrategy onDeleteStrategy() default IndexingStrategy.AUTO;
}
```

#### `@AIProcess` (Method-Level) - KEEP AS-IS

```java
package com.ai.infrastructure.annotation;

import com.ai.infrastructure.indexing.IndexingStrategy;
import java.lang.annotation.*;

/**
 * Marks a method as a trigger for AI processing.
 * 
 * <p>When the annotated method executes, AI processing (embedding generation,
 * indexing, etc.) is triggered automatically via AOP.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @AIProcess(entityType = "product", processType = "create")
 * public Product createProduct(Product product) {
 *     return repository.save(product);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIProcess {
    
    /**
     * Entity type for AI processing.
     * Must match @AICapable.entityType() on the entity class.
     */
    String entityType() default "";
    
    /**
     * Operation type: "create", "update", "delete", "search", "analyze"
     */
    String processType() default "create";
    
    /**
     * Generate embeddings for this operation.
     * Default: true for create/update, false for delete.
     */
    boolean generateEmbedding() default true;
    
    /**
     * Index for search after this operation.
     */
    boolean indexForSearch() default true;
    
    /**
     * Optional indexing strategy override.
     * AUTO inherits from entity configuration.
     */
    IndexingStrategy indexingStrategy() default IndexingStrategy.AUTO;
}
```

#### `@AIEmbedding` (Field-Level) - SIMPLIFIED TO MARKER

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.*;

/**
 * Marks a field for embedding generation.
 * 
 * <p>Fields annotated with @AIEmbedding are automatically included in
 * vector embedding generation. The presence of this annotation is
 * sufficient - no attributes required.</p>
 * 
 * <p>Configuration (weight, chunking, etc.) can be specified in YAML
 * if needed for advanced tuning.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Entity
 * @AICapable(entityType = "product")
 * public class Product {
 *     
 *     @AIEmbedding  // This field will be embedded
 *     private String name;
 *     
 *     @AIEmbedding  // This field will also be embedded
 *     private String description;
 *     
 *     private BigDecimal price;  // NOT embedded (no annotation)
 * }
 * }</pre>
 * 
 * <p><b>Optional YAML Override:</b></p>
 * <pre>{@code
 * ai-entities:
 *   product:
 *     embeddable-fields:
 *       - name: "name"
 *         weight: 2.0  # Override default weight
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIEmbedding {
    // Pure marker annotation - no attributes needed
    // Configuration comes from YAML if customization is required
}
```

#### `@AIKnowledge` (Field-Level) - SIMPLIFIED TO MARKER

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.*;

/**
 * Marks a field as knowledge for RAG (Retrieval-Augmented Generation).
 * 
 * <p>Fields annotated with @AIKnowledge are included in RAG context
 * when generating AI responses. The presence of this annotation is
 * sufficient - no attributes required.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Entity
 * @AICapable(entityType = "faq")
 * public class FAQ {
 *     
 *     @AIEmbedding
 *     @AIKnowledge  // Include in RAG responses
 *     private String question;
 *     
 *     @AIEmbedding
 *     @AIKnowledge  // Include in RAG responses
 *     private String answer;
 *     
 *     private String internalNotes;  // NOT in RAG (no annotation)
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIKnowledge {
    // Pure marker annotation - no attributes needed
}
```

#### `@AISmartValidation` (Field-Level) - REMOVE OR SIMPLIFY

**Recommendation: REMOVE** - Not used in current flow, adds complexity.

If kept, simplify to:

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AISmartValidation {
    // Pure marker - validation rules in YAML
}
```

---

## 3. Auto-Discovery Implementation

### 3.1 Enhanced `AIEntityConfigurationLoader`

```java
package com.ai.infrastructure.config;

import com.ai.infrastructure.annotation.*;
import com.ai.infrastructure.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AIEntityConfigurationLoader {
    
    private final Map<String, AIEntityConfig> entityConfigs = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> entityClasses = new ConcurrentHashMap<>();
    
    @Value("${ai.config.base-packages:}")
    private String basePackages;
    
    @PostConstruct
    public void init() {
        // Step 1: Scan for @AICapable classes
        scanForAICapableEntities();
        
        // Step 2: Load YAML overrides
        loadYamlOverrides();
        
        log.info("Loaded AI configurations for entity types: {}", entityConfigs.keySet());
    }
    
    /**
     * Scan classpath for @AICapable annotated classes and auto-discover fields.
     */
    private void scanForAICapableEntities() {
        ClassPathScanningCandidateComponentProvider scanner = 
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(AICapable.class));
        
        String[] packages = basePackages.isEmpty() 
            ? new String[]{"com"} 
            : basePackages.split(",");
        
        for (String pkg : packages) {
            for (BeanDefinition bd : scanner.findCandidateComponents(pkg.trim())) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    processAICapableClass(clazz);
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load class: {}", bd.getBeanClassName());
                }
            }
        }
    }
    
    /**
     * Process a single @AICapable class and extract field configurations.
     */
    private void processAICapableClass(Class<?> clazz) {
        AICapable aiCapable = clazz.getAnnotation(AICapable.class);
        if (aiCapable == null) return;
        
        String entityType = aiCapable.entityType();
        entityClasses.put(entityType, clazz);
        
        // Auto-discover fields
        List<AIEmbeddableField> embeddableFields = new ArrayList<>();
        List<AISearchableField> searchableFields = new ArrayList<>();
        List<AIMetadataField> metadataFields = new ArrayList<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            
            // Check for @AIEmbedding
            if (field.isAnnotationPresent(AIEmbedding.class)) {
                embeddableFields.add(AIEmbeddableField.builder()
                    .name(fieldName)
                    .autoGenerate(true)
                    .includeInSimilarity(true)
                    .build());
                
                // Also make it searchable by default
                searchableFields.add(AISearchableField.builder()
                    .name(fieldName)
                    .includeInRag(field.isAnnotationPresent(AIKnowledge.class))
                    .enableSemanticSearch(true)
                    .weight(1.0)
                    .build());
            }
            
            // Check for @AIKnowledge (may or may not have @AIEmbedding)
            if (field.isAnnotationPresent(AIKnowledge.class)) {
                if (!field.isAnnotationPresent(AIEmbedding.class)) {
                    // Knowledge-only field (not embedded but in RAG)
                    searchableFields.add(AISearchableField.builder()
                        .name(fieldName)
                        .includeInRag(true)
                        .enableSemanticSearch(false)
                        .weight(1.0)
                        .build());
                }
            }
        }
        
        // Build config from annotations
        AIEntityConfig config = AIEntityConfig.builder()
            .entityType(entityType)
            .entityClass(clazz.getName())
            .autoEmbedding(true)
            .indexable(true)
            .enableSearch(true)
            .embeddableFields(embeddableFields)
            .searchableFields(searchableFields)
            .metadataFields(metadataFields)
            .indexingStrategy(aiCapable.indexingStrategy())
            .onCreateStrategy(aiCapable.onCreateStrategy())
            .onUpdateStrategy(aiCapable.onUpdateStrategy())
            .onDeleteStrategy(aiCapable.onDeleteStrategy())
            .build();
        
        entityConfigs.put(entityType, config);
        log.debug("Auto-discovered AI config for {}: {} embeddable fields, {} searchable fields",
            entityType, embeddableFields.size(), searchableFields.size());
    }
    
    /**
     * Load YAML overrides and merge with annotation-discovered config.
     */
    private void loadYamlOverrides() {
        // Load from ai-entity-config.yml
        // Merge: YAML values override annotation-discovered defaults
        // ... existing YAML loading logic ...
    }
    
    /**
     * Get configuration for an entity type.
     * Returns merged config (annotations + YAML overrides).
     */
    public AIEntityConfig getEntityConfig(String entityType) {
        return entityConfigs.get(entityType);
    }
    
    /**
     * Get all supported entity types.
     */
    public Set<String> getSupportedEntityTypes() {
        return Collections.unmodifiableSet(entityConfigs.keySet());
    }
}
```

### 3.2 Config Merge Strategy

```java
/**
 * Merge annotation-discovered config with YAML overrides.
 * YAML takes precedence for any explicitly specified values.
 */
private AIEntityConfig mergeConfigs(AIEntityConfig annotationConfig, 
                                     AIEntityConfig yamlConfig) {
    if (yamlConfig == null) {
        return annotationConfig;  // No YAML override
    }
    
    return AIEntityConfig.builder()
        .entityType(annotationConfig.getEntityType())
        .entityClass(annotationConfig.getEntityClass())
        
        // YAML overrides annotation values
        .autoEmbedding(yamlConfig.isAutoEmbedding())
        .indexable(yamlConfig.isIndexable())
        .enableSearch(yamlConfig.isEnableSearch())
        
        // Merge field lists (YAML can add/modify fields)
        .embeddableFields(mergeEmbeddableFields(
            annotationConfig.getEmbeddableFields(),
            yamlConfig.getEmbeddableFields()))
        .searchableFields(mergeSearchableFields(
            annotationConfig.getSearchableFields(),
            yamlConfig.getSearchableFields()))
        .metadataFields(yamlConfig.getMetadataFields())  // Metadata from YAML only
        
        .build();
}

/**
 * Merge embeddable fields: YAML overrides annotation defaults.
 */
private List<AIEmbeddableField> mergeEmbeddableFields(
        List<AIEmbeddableField> annotationFields,
        List<AIEmbeddableField> yamlFields) {
    
    if (yamlFields == null || yamlFields.isEmpty()) {
        return annotationFields;
    }
    
    Map<String, AIEmbeddableField> merged = new LinkedHashMap<>();
    
    // Start with annotation-discovered fields
    for (AIEmbeddableField field : annotationFields) {
        merged.put(field.getName(), field);
    }
    
    // Override/add from YAML
    for (AIEmbeddableField yamlField : yamlFields) {
        merged.put(yamlField.getName(), yamlField);
    }
    
    return new ArrayList<>(merged.values());
}
```

---

## 4. YAML Configuration (Optional)

### 4.1 Minimal YAML (Just Overrides)

```yaml
# ai-entity-config.yml
# Only specify what you need to OVERRIDE from annotation defaults

ai-entities:
  product:
    # Override field weights
    embeddable-fields:
      - name: "name"
        weight: 2.0  # Title is more important
    
    # Add metadata fields (not auto-discovered)
    metadata-fields:
      - name: "category"
        type: "TEXT"
      - name: "price"
        type: "NUMERIC"
```

### 4.2 Full YAML (When No Annotations)

For entities without annotations (legacy or external):

```yaml
ai-entities:
  legacy-product:
    entity-type: "legacy-product"
    entity-class: "com.example.LegacyProduct"
    auto-embedding: true
    indexable: true
    enable-search: true
    
    embeddable-fields:
      - name: "title"
        weight: 2.0
      - name: "description"
        weight: 1.0
    
    searchable-fields:
      - name: "title"
        include-in-rag: true
      - name: "description"
        include-in-rag: true
    
    metadata-fields:
      - name: "category"
        type: "TEXT"
```

---

## 5. Usage Examples

### 5.1 Simple Case: Annotations Only

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @AIEmbedding
    private String name;
    
    @AIEmbedding
    @AIKnowledge
    private String description;
    
    private BigDecimal price;  // Not AI-processed
    private String sku;        // Not AI-processed
}

@Service
public class ProductService {
    
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product create(Product product) {
        return repository.save(product);
    }
    
    @AIProcess(entityType = "product", processType = "update")
    @Transactional
    public Product update(Product product) {
        return repository.save(product);
    }
    
    @AIProcess(entityType = "product", processType = "delete",
               generateEmbedding = false, indexForSearch = false)
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
```

**Result:** Zero YAML needed. Auto-discovered and processed.

### 5.2 Advanced Case: With YAML Override

```java
@Entity
@AICapable(entityType = "article")
public class Article {
    
    @AIEmbedding
    @AIKnowledge
    private String title;
    
    @AIEmbedding
    @AIKnowledge
    private String content;
    
    private String author;
    private LocalDateTime publishDate;
}
```

```yaml
# Override defaults for production tuning
ai-entities:
  article:
    embeddable-fields:
      - name: "title"
        weight: 3.0  # Boost title importance
      - name: "content"
        weight: 1.0
        chunking-strategy: "paragraph"
        max-chunk-size: 500
    
    metadata-fields:
      - name: "author"
        type: "TEXT"
        include-in-search: true
      - name: "publishDate"
        type: "DATE"
```

---

## 6. Migration Plan

### Phase 1: Simplify Annotations (Week 1)

1. Replace `@AIEmbedding` with marker annotation
2. Replace `@AIKnowledge` with marker annotation
3. Remove or simplify `@AISmartValidation`
4. Update JavaDocs

### Phase 2: Implement Auto-Discovery (Week 2)

1. Enhance `AIEntityConfigurationLoader` with scanning
2. Implement config merge logic
3. Add unit tests for auto-discovery

### Phase 3: Update Existing Code (Week 3)

1. Remove redundant YAML configs where annotations exist
2. Update integration tests
3. Update documentation

### Phase 4: Cleanup (Week 4)

1. Delete `AICapableProcessor.getEmbeddingFields()` (now in loader)
2. Delete `AICapableProcessor.getKnowledgeFields()` (now in loader)
3. Simplify `AICapableProcessor` to minimal functionality
4. Final testing and validation

---

## 7. File Changes Summary

| File | Action | Lines Before | Lines After |
|------|--------|--------------|-------------|
| `AIEmbedding.java` | Simplify | 360 | ~30 |
| `AIKnowledge.java` | Simplify | 523 | ~30 |
| `AISmartValidation.java` | Remove/Simplify | 107 | 0 or ~20 |
| `AICapable.java` | Minor cleanup | 99 | ~60 |
| `AIProcess.java` | Keep as-is | 60 | 60 |
| `AIEntityConfigurationLoader.java` | Enhance | ~200 | ~350 |
| `AICapableProcessor.java` | Simplify | 317 | ~100 |
| Various YAML configs | Remove redundancy | ~1000 | ~200 |

**Total reduction: ~1,500+ lines of code**

---

## 8. Testing Strategy

### Unit Tests

```java
@Test
void shouldAutoDiscoverEmbeddingFields() {
    // Given entity with @AIEmbedding annotations
    // When loader initializes
    // Then config should contain discovered fields
}

@Test
void shouldMergeYamlOverrides() {
    // Given annotation config with weight 1.0
    // And YAML override with weight 2.0
    // When merged
    // Then final weight should be 2.0
}

@Test
void shouldWorkWithoutYaml() {
    // Given only annotations, no YAML
    // When processing entity
    // Then should work with annotation defaults
}
```

### Integration Tests

```java
@Test
void shouldIndexEntityWithOnlyAnnotations() {
    // Given Product with @AIEmbedding on name, description
    // No YAML config for product
    // When createProduct called
    // Then embeddings generated for name and description
}
```

---

## 9. Configuration Reference

### Application Properties

```yaml
ai:
  config:
    # Packages to scan for @AICapable entities
    base-packages: "com.example.domain,com.example.entities"
    
    # YAML config file (optional)
    config-file: "ai-entity-config.yml"
    
    # Enable auto-discovery (default: true)
    auto-discovery-enabled: true
```

---

## 10. Summary

| Aspect | Before | After |
|--------|--------|-------|
| `@AIEmbedding` | 50+ attributes | **0 attributes** |
| `@AIKnowledge` | 100+ attributes | **0 attributes** |
| YAML config | Required | **Optional** |
| Field discovery | Manual in YAML | **Auto from annotations** |
| Config merge | None | **YAML overrides annotations** |
| Lines of code | ~1,500 | **~300** |
| Complexity | High | **Low** |
| DRY compliance | ❌ Violated | ✅ Compliant |

**Result:** Clean, simple, maintainable annotation system with single source of truth.

---

*Implementation Ready: This document provides complete specifications for the recommended approach.*


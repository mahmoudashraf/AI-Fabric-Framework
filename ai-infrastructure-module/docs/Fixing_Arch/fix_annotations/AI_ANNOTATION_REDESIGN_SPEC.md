# AI Annotation Redesign Specification

**Status:** Approved for Implementation  
**Version:** 2.0  
**Date:** January 2026

---

## Executive Summary

Redesign AI field annotations to be intuitive, purpose-driven, and properly integrated with the existing `ai-entity-config.yml` structure and `AISearchableEntity` storage model.

**Key Changes:**
- `@AIEmbedding` → **`@AISearchable`** (semantic search)
- `@AIKnowledge` → **Removed** (redundant)
- New: **`@AIContext`** (LLM context, not embedded)
- Auto-discovery from annotations with YAML override support
- Proper integration with `AISearchableEntity` storage

---

## 1. Current Architecture Understanding

### 1.1 AISearchableEntity Storage Model

When an entity is processed, we store:

```java
@Entity
@Table(name = "ai_searchable_entity")
public class AISearchableEntity {
    private String entityType;         // "product"
    private String entityId;           // "123"
    private String searchableContent;  // Text from @AISearchable fields
    private String vectorId;           // Reference to vector in vector DB
    private String metadata;           // JSON from @AIContext fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Key insight:** We do NOT load the full original entity during RAG retrieval. We use:
- `searchableContent` → For LLM context (embedded text)
- `metadata` → For additional LLM context (structured data)

### 1.2 Current YAML Structure

```yaml
ai-entities:
  product:
    entity-type: "product"
    auto-embedding: true
    indexable: true
    enable-search: true
    
    # Text fields for embedding and semantic search
    embeddable-fields:
      - name: "name"
        model: "text-embedding-3-small"
        auto-generate: true
      - name: "description"
        auto-generate: true
    
    # Fields for search (may overlap with embeddable)
    searchable-fields:
      - name: "name"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
      - name: "description"
        include-in-rag: true
        weight: 0.8
    
    # Structured data stored as JSON metadata
    metadata-fields:
      - name: "price"
        type: "NUMERIC"
        include-in-search: false
      - name: "brand"
        type: "TEXT"
        include-in-search: true
      - name: "category"
        type: "TEXT"
```

---

## 2. Redesigned Annotation Model

### 2.1 Annotation Suite

| Annotation | Level | Purpose | Storage |
|------------|-------|---------|---------|
| `@AICapable` | Class | Entity is AI-enabled | - |
| `@AIProcess` | Method | Triggers AI processing | - |
| `@AISearchable` | Field | Semantic search | `searchableContent` + vector |
| `@AIContext` | Field | LLM context only | `metadata` JSON |

### 2.2 Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  INDEXING (When Entity is Saved)                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Product Entity                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ @AISearchable name = "Bamboo Toothbrush"                │    │
│  │ @AISearchable description = "Eco-friendly dental..."   │    │
│  │ @AIContext price = 29.99                                │    │
│  │ @AIContext brand = "EcoLife"                            │    │
│  │ internalSku = "SKU-123"  (not annotated)                │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ AISearchableEntity                                      │    │
│  │                                                         │    │
│  │ searchableContent: "Bamboo Toothbrush Eco-friendly..."  │    │
│  │ metadata: {"price": 29.99, "brand": "EcoLife"}          │    │
│  │ vectorId: "vec-abc-123"                                 │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Vector DB                                               │    │
│  │                                                         │    │
│  │ ID: "vec-abc-123"                                       │    │
│  │ Vector: [0.023, -0.156, 0.891, ...]                     │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  RETRIEVAL (When User Queries)                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User: "eco-friendly dental products"                           │
│                              │                                  │
│                              ▼                                  │
│  Query Embedding: [0.019, -0.148, 0.887, ...]                   │
│                              │                                  │
│                              ▼                                  │
│  Vector DB Search → Returns: vec-abc-123 (similarity: 0.94)     │
│                              │                                  │
│                              ▼                                  │
│  Load AISearchableEntity by vectorId                            │
│                              │                                  │
│                              ▼                                  │
│  Build LLM Context:                                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Content: "Bamboo Toothbrush Eco-friendly dental..."     │    │
│  │ Price: $29.99                                           │    │
│  │ Brand: EcoLife                                          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                  │
│                              ▼                                  │
│  LLM generates response using context                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Annotation Definitions

### 3.1 `@AICapable` (Class-Level)

```java
package com.ai.infrastructure.annotation;

import com.ai.infrastructure.indexing.IndexingStrategy;
import java.lang.annotation.*;

/**
 * Marks an entity class as AI-enabled.
 * 
 * Field-level annotations (@AISearchable, @AIContext) are auto-discovered
 * when this annotation is present.
 * 
 * <pre>{@code
 * @Entity
 * @AICapable(entityType = "product")
 * public class Product {
 *     @AISearchable
 *     private String name;
 *     
 *     @AIContext
 *     private BigDecimal price;
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AICapable {
    
    /**
     * Unique entity type identifier.
     * Must match the key in ai-entity-config.yml if YAML override is used.
     */
    String entityType();
    
    /**
     * Default indexing strategy for all operations.
     */
    IndexingStrategy indexingStrategy() default IndexingStrategy.ASYNC;
    
    /**
     * Override for CREATE operations.
     */
    IndexingStrategy onCreateStrategy() default IndexingStrategy.AUTO;
    
    /**
     * Override for UPDATE operations.
     */
    IndexingStrategy onUpdateStrategy() default IndexingStrategy.AUTO;
    
    /**
     * Override for DELETE operations.
     */
    IndexingStrategy onDeleteStrategy() default IndexingStrategy.AUTO;
}
```

### 3.2 `@AIProcess` (Method-Level)

```java
package com.ai.infrastructure.annotation;

import com.ai.infrastructure.indexing.IndexingStrategy;
import java.lang.annotation.*;

/**
 * Marks a method as a trigger for AI processing.
 * 
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
     */
    String entityType() default "";
    
    /**
     * Operation type: "create", "update", "delete"
     */
    String processType() default "create";
    
    /**
     * Generate embeddings for this operation.
     */
    boolean generateEmbedding() default true;
    
    /**
     * Index for search after this operation.
     */
    boolean indexForSearch() default true;
    
    /**
     * Optional indexing strategy override.
     */
    IndexingStrategy indexingStrategy() default IndexingStrategy.AUTO;
}
```

### 3.3 `@AISearchable` (Field-Level)

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.*;

/**
 * Marks a field for semantic search.
 * 
 * Fields annotated with @AISearchable:
 * - Are included in the embedding vector (for similarity search)
 * - Are stored in AISearchableEntity.searchableContent
 * - Are included in LLM context during RAG
 * 
 * <pre>{@code
 * @Entity
 * @AICapable(entityType = "product")
 * public class Product {
 *     
 *     @AISearchable  // Users can find products by searching name
 *     private String name;
 *     
 *     @AISearchable  // Users can find products by searching description
 *     private String description;
 * }
 * }</pre>
 * 
 * <p><b>YAML Override:</b> Weight and other settings can be customized:</p>
 * <pre>{@code
 * ai-entities:
 *   product:
 *     searchable-fields:
 *       - name: "name"
 *         weight: 2.0  # Title is more important
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AISearchable {
    // Pure marker annotation
    // Configuration via YAML if needed
}
```

### 3.4 `@AIContext` (Field-Level)

```java
package com.ai.infrastructure.annotation;

import java.lang.annotation.*;

/**
 * Marks a field for inclusion in LLM context (without embedding).
 * 
 * Fields annotated with @AIContext:
 * - Are NOT included in the embedding vector
 * - Are stored in AISearchableEntity.metadata as JSON
 * - Are included in LLM context during RAG
 * 
 * Use this for structured data that the LLM needs to know but
 * doesn't benefit from semantic search (prices, ratings, IDs, etc.)
 * 
 * <pre>{@code
 * @Entity
 * @AICapable(entityType = "product")
 * public class Product {
 *     
 *     @AISearchable
 *     private String name;
 *     
 *     @AIContext  // AI knows the price when responding
 *     private BigDecimal price;
 *     
 *     @AIContext  // AI knows the brand
 *     private String brand;
 *     
 *     @AIContext  // AI knows the rating
 *     private Double rating;
 * }
 * }</pre>
 * 
 * <p><b>YAML Override:</b> Type and filtering options can be customized:</p>
 * <pre>{@code
 * ai-entities:
 *   product:
 *     metadata-fields:
 *       - name: "price"
 *         type: "NUMERIC"
 *         include-in-search: true  # Can filter by price
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIContext {
    // Pure marker annotation
    // Configuration via YAML if needed
}
```

---

## 4. YAML Integration

### 4.1 Mapping: Annotations ↔ YAML

| Annotation | YAML Section | Purpose |
|------------|--------------|---------|
| `@AISearchable` | `searchable-fields` + `embeddable-fields` | Semantic search + embedding |
| `@AIContext` | `metadata-fields` | Structured data for LLM |

### 4.2 Auto-Discovery + YAML Override

**Priority (highest to lowest):**
1. YAML explicit configuration
2. Annotation defaults
3. Framework defaults

```
┌─────────────────────────────────────────────────────────────────┐
│  Configuration Resolution                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Scan @AICapable class for @AISearchable/@AIContext fields   │
│  2. Build default config from annotations                       │
│  3. Load YAML (if exists for this entityType)                   │
│  4. Merge: YAML overrides annotation defaults                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Example: Annotations Only (Zero YAML)

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    private Long id;
    
    @AISearchable
    private String name;
    
    @AISearchable
    private String description;
    
    @AIContext
    private BigDecimal price;
    
    @AIContext
    private String brand;
    
    private String internalSku;  // Not in AI system
}
```

**Auto-generated config (equivalent to):**

```yaml
ai-entities:
  product:
    entity-type: "product"
    auto-embedding: true
    indexable: true
    
    # From @AISearchable fields
    searchable-fields:
      - name: "name"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
      - name: "description"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
    
    embeddable-fields:
      - name: "name"
        auto-generate: true
      - name: "description"
        auto-generate: true
    
    # From @AIContext fields
    metadata-fields:
      - name: "price"
        type: "NUMERIC"
      - name: "brand"
        type: "TEXT"
```

### 4.4 Example: Annotations + YAML Override

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable
    private String name;
    
    @AISearchable
    private String description;
    
    @AIContext
    private BigDecimal price;
    
    @AIContext
    private String brand;
    
    @AIContext
    private String category;
}
```

```yaml
# ai-entity-config.yml - OPTIONAL overrides
ai-entities:
  product:
    # Override weights for searchable fields
    searchable-fields:
      - name: "name"
        weight: 2.0           # Title is more important
      - name: "description"
        weight: 1.0
        include-in-rag: false # Don't send full description to LLM
    
    # Override metadata field settings
    metadata-fields:
      - name: "price"
        type: "NUMERIC"
        include-in-search: true  # Enable filtering by price
      - name: "category"
        type: "TEXT"
        include-in-search: true  # Enable filtering by category
```

---

## 5. Implementation

### 5.1 Enhanced AIEntityConfigurationLoader

```java
package com.ai.infrastructure.config;

@Slf4j
@Component
public class AIEntityConfigurationLoader {
    
    private final Map<String, AIEntityConfig> entityConfigs = new ConcurrentHashMap<>();
    private final ResourceLoader resourceLoader;
    
    @Value("${ai.config.base-packages:}")
    private String basePackages;
    
    @Value("${ai.config.file:ai-entity-config.yml}")
    private String configFile;
    
    @PostConstruct
    public void init() {
        // Step 1: Scan for @AICapable classes
        Map<String, AIEntityConfig> annotationConfigs = scanAnnotations();
        
        // Step 2: Load YAML overrides
        Map<String, AIEntityConfig> yamlConfigs = loadYamlConfigs();
        
        // Step 3: Merge (YAML overrides annotations)
        for (String entityType : annotationConfigs.keySet()) {
            AIEntityConfig annotationConfig = annotationConfigs.get(entityType);
            AIEntityConfig yamlConfig = yamlConfigs.get(entityType);
            
            AIEntityConfig merged = mergeConfigs(annotationConfig, yamlConfig);
            entityConfigs.put(entityType, merged);
        }
        
        // Step 4: Add YAML-only configs (no annotation)
        for (String entityType : yamlConfigs.keySet()) {
            if (!entityConfigs.containsKey(entityType)) {
                entityConfigs.put(entityType, yamlConfigs.get(entityType));
            }
        }
        
        log.info("Loaded AI configurations: {}", entityConfigs.keySet());
    }
    
    /**
     * Scan classpath for @AICapable classes and extract field annotations.
     */
    private Map<String, AIEntityConfig> scanAnnotations() {
        Map<String, AIEntityConfig> configs = new HashMap<>();
        
        ClassPathScanningCandidateComponentProvider scanner = 
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(AICapable.class));
        
        for (String pkg : getBasePackages()) {
            for (BeanDefinition bd : scanner.findCandidateComponents(pkg)) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    AIEntityConfig config = buildConfigFromAnnotations(clazz);
                    configs.put(config.getEntityType(), config);
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load class: {}", bd.getBeanClassName());
                }
            }
        }
        
        return configs;
    }
    
    /**
     * Build config from class annotations.
     */
    private AIEntityConfig buildConfigFromAnnotations(Class<?> clazz) {
        AICapable aiCapable = clazz.getAnnotation(AICapable.class);
        String entityType = aiCapable.entityType();
        
        List<AISearchableField> searchableFields = new ArrayList<>();
        List<AIEmbeddableField> embeddableFields = new ArrayList<>();
        List<AIMetadataField> metadataFields = new ArrayList<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            
            // @AISearchable → searchable + embeddable
            if (field.isAnnotationPresent(AISearchable.class)) {
                searchableFields.add(AISearchableField.builder()
                    .name(fieldName)
                    .includeInRag(true)
                    .enableSemanticSearch(true)
                    .weight(1.0)
                    .build());
                
                embeddableFields.add(AIEmbeddableField.builder()
                    .name(fieldName)
                    .autoGenerate(true)
                    .includeInSimilarity(true)
                    .build());
            }
            
            // @AIContext → metadata
            if (field.isAnnotationPresent(AIContext.class)) {
                String type = inferMetadataType(field.getType());
                metadataFields.add(AIMetadataField.builder()
                    .name(fieldName)
                    .type(type)
                    .includeInSearch(false)  // Default: no filtering
                    .build());
            }
        }
        
        return AIEntityConfig.builder()
            .entityType(entityType)
            .entityClass(clazz.getName())
            .autoEmbedding(true)
            .indexable(true)
            .enableSearch(true)
            .searchableFields(searchableFields)
            .embeddableFields(embeddableFields)
            .metadataFields(metadataFields)
            .indexingStrategy(aiCapable.indexingStrategy())
            .onCreateStrategy(aiCapable.onCreateStrategy())
            .onUpdateStrategy(aiCapable.onUpdateStrategy())
            .onDeleteStrategy(aiCapable.onDeleteStrategy())
            .build();
    }
    
    /**
     * Infer metadata type from Java type.
     */
    private String inferMetadataType(Class<?> type) {
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return "NUMERIC";
        } else if (type == Boolean.class || type == boolean.class) {
            return "BOOLEAN";
        } else if (type == java.time.LocalDate.class || 
                   type == java.time.LocalDateTime.class ||
                   type == java.util.Date.class) {
            return "DATE";
        }
        return "TEXT";
    }
    
    /**
     * Merge annotation config with YAML override.
     * YAML takes precedence for any explicitly specified values.
     */
    private AIEntityConfig mergeConfigs(AIEntityConfig annotation, 
                                         AIEntityConfig yaml) {
        if (yaml == null) {
            return annotation;
        }
        
        return AIEntityConfig.builder()
            .entityType(annotation.getEntityType())
            .entityClass(annotation.getEntityClass())
            .autoEmbedding(yaml.isAutoEmbedding())
            .indexable(yaml.isIndexable())
            .enableSearch(yaml.isEnableSearch())
            .searchableFields(mergeSearchableFields(
                annotation.getSearchableFields(), 
                yaml.getSearchableFields()))
            .embeddableFields(mergeEmbeddableFields(
                annotation.getEmbeddableFields(),
                yaml.getEmbeddableFields()))
            .metadataFields(mergeMetadataFields(
                annotation.getMetadataFields(),
                yaml.getMetadataFields()))
            .indexingStrategy(yaml.getIndexingStrategy() != null 
                ? yaml.getIndexingStrategy() 
                : annotation.getIndexingStrategy())
            .build();
    }
    
    /**
     * Merge searchable fields: YAML overrides annotation defaults.
     */
    private List<AISearchableField> mergeSearchableFields(
            List<AISearchableField> annotation,
            List<AISearchableField> yaml) {
        
        if (yaml == null || yaml.isEmpty()) {
            return annotation;
        }
        
        Map<String, AISearchableField> merged = new LinkedHashMap<>();
        
        // Start with annotation defaults
        for (AISearchableField field : annotation) {
            merged.put(field.getName(), field);
        }
        
        // Override with YAML values
        for (AISearchableField yamlField : yaml) {
            AISearchableField existing = merged.get(yamlField.getName());
            if (existing != null) {
                // Merge: YAML overrides specific values
                merged.put(yamlField.getName(), AISearchableField.builder()
                    .name(yamlField.getName())
                    .includeInRag(yamlField.isIncludeInRag())
                    .enableSemanticSearch(yamlField.isEnableSemanticSearch())
                    .weight(yamlField.getWeight())
                    .build());
            } else {
                // Add new field from YAML
                merged.put(yamlField.getName(), yamlField);
            }
        }
        
        return new ArrayList<>(merged.values());
    }
    
    // Similar merge methods for embeddable and metadata fields...
}
```

### 5.2 AICapabilityService Integration

```java
@Service
public class AICapabilityService {
    
    /**
     * Extract searchable content from @AISearchable fields.
     */
    private String extractSearchableContent(Object entity, AIEntityConfig config) {
        List<String> parts = new ArrayList<>();
        
        for (AISearchableField field : config.getSearchableFields()) {
            if (field.isEnableSemanticSearch()) {
                String value = getFieldValue(entity, field.getName());
                if (value != null && !value.isBlank()) {
                    parts.add(value);
                }
            }
        }
        
        return String.join(" ", parts);
    }
    
    /**
     * Extract metadata from @AIContext fields.
     */
    private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        
        for (AIMetadataField field : config.getMetadataFields()) {
            Object value = getFieldValueAsObject(entity, field.getName());
            if (value != null) {
                metadata.put(field.getName(), value);
            }
        }
        
        return metadata;
    }
    
    /**
     * Store in AISearchableEntity.
     */
    private void storeSearchableEntity(Object entity, AIEntityConfig config, 
                                        String content, List<Double> embeddings) {
        String entityId = getEntityId(entity);
        Map<String, Object> metadata = extractMetadata(entity, config);
        
        // Store vector
        String vectorId = vectorManagementService.storeVector(
            config.getEntityType(),
            entityId,
            content,
            embeddings,
            metadata
        );
        
        // Store AISearchableEntity
        AISearchableEntity searchable = AISearchableEntity.builder()
            .entityType(config.getEntityType())
            .entityId(entityId)
            .searchableContent(content)
            .vectorId(vectorId)
            .metadata(serializeMetadata(metadata))
            .build();
        
        storageStrategy.save(searchable);
    }
}
```

### 5.3 RAG Context Builder

```java
@Service
public class RAGContextBuilder {
    
    /**
     * Build LLM context from AISearchableEntity.
     */
    public String buildContext(AISearchableEntity entity) {
        StringBuilder context = new StringBuilder();
        
        // Add searchable content (from @AISearchable fields)
        context.append(entity.getSearchableContent());
        
        // Add metadata (from @AIContext fields)
        Map<String, Object> metadata = parseMetadata(entity.getMetadata());
        if (!metadata.isEmpty()) {
            context.append("\n\nAdditional Information:\n");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                context.append(String.format("- %s: %s\n", 
                    formatFieldName(entry.getKey()), 
                    entry.getValue()));
            }
        }
        
        return context.toString();
    }
    
    private String formatFieldName(String fieldName) {
        // "productPrice" → "Product Price"
        return fieldName.replaceAll("([a-z])([A-Z])", "$1 $2")
                        .substring(0, 1).toUpperCase() + 
               fieldName.replaceAll("([a-z])([A-Z])", "$1 $2")
                        .substring(1);
    }
}
```

---

## 6. Complete Usage Examples

### 6.1 Simple: Annotations Only

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @AISearchable   // Semantic search by name
    private String name;
    
    @AISearchable   // Semantic search by description
    private String description;
    
    @AIContext      // LLM knows the price
    private BigDecimal price;
    
    @AIContext      // LLM knows the brand
    private String brand;
    
    @AIContext      // LLM knows the rating
    private Double rating;
    
    private String internalSku;  // Not in AI system
}

@Service
public class ProductService {
    
    private final ProductRepository repository;
    
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

### 6.2 Advanced: With YAML Override

```java
@Entity
@AICapable(entityType = "article")
public class Article {
    
    @AISearchable
    private String title;
    
    @AISearchable
    private String content;
    
    @AISearchable
    private String tags;  // Search optimization
    
    @AIContext
    private String author;
    
    @AIContext
    private LocalDateTime publishDate;
    
    @AIContext
    private Integer readTimeMinutes;
}
```

```yaml
# ai-entity-config.yml
ai-entities:
  article:
    searchable-fields:
      - name: "title"
        weight: 3.0           # Title most important
        include-in-rag: true
      - name: "content"
        weight: 1.0
        include-in-rag: true
      - name: "tags"
        weight: 2.0
        include-in-rag: false  # Don't send tags to LLM (noise)
    
    metadata-fields:
      - name: "author"
        type: "TEXT"
        include-in-search: true  # Filter by author
      - name: "publishDate"
        type: "DATE"
        include-in-search: true  # Filter by date
```

---

## 7. Migration Plan

### Phase 1: Add New Annotations (Week 1)

1. Create `@AISearchable` annotation
2. Create `@AIContext` annotation
3. Keep old annotations for backward compatibility
4. Add deprecation warnings

### Phase 2: Implement Auto-Discovery (Week 2)

1. Enhance `AIEntityConfigurationLoader` with scanning
2. Implement config merge logic
3. Add unit tests

### Phase 3: Update Existing Entities (Week 3)

1. Add `@AISearchable` / `@AIContext` to entities
2. Remove redundant YAML configs
3. Update documentation

### Phase 4: Cleanup (Week 4)

1. Remove deprecated `@AIEmbedding`
2. Remove deprecated `@AIKnowledge`
3. Simplify `AICapableProcessor`
4. Final testing

---

## 8. Summary

### Before vs After

| Aspect | Before | After |
|--------|--------|-------|
| Embedding annotation | `@AIEmbedding` (50+ attrs) | **`@AISearchable`** (marker) |
| Metadata annotation | None (YAML only) | **`@AIContext`** (marker) |
| Knowledge annotation | `@AIKnowledge` (100+ attrs) | **Removed** |
| YAML config | Required | **Optional override** |
| Field discovery | Manual in YAML | **Auto from annotations** |
| Names | Implementation-focused | **Purpose-focused** |

### Final Annotation Suite

| Annotation | Level | Purpose | Storage Location |
|------------|-------|---------|------------------|
| `@AICapable` | Class | Entity is AI-enabled | - |
| `@AIProcess` | Method | Triggers processing | - |
| `@AISearchable` | Field | Semantic search | `searchableContent` + vector |
| `@AIContext` | Field | LLM context | `metadata` JSON |

### User Mental Model

```
@AISearchable  →  "Users can FIND this by meaning"
@AIContext     →  "AI will KNOW this when responding"
```

---

*Implementation Ready: Clean, purpose-driven annotations with proper storage model integration.*


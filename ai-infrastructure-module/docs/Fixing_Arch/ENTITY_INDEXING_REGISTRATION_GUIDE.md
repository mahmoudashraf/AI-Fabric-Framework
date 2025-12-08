# How to Register an Entity for Indexing (Current Strategy)

## Overview

Entities are registered for indexing through a **two-layer configuration system**:

1. **Entity Layer** - `@AICapable` annotation on your entity class
2. **Configuration Layer** - YAML configuration in `ai-entity-config.yml`

Once registered, indexing is **automatically triggered** during CRUD operations.

---

## Step-by-Step Registration Process

### Step 1: Annotate Your Entity Class

Add the `@AICapable` annotation to your entity:

```java
import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.indexing.IndexingStrategy;

@Entity
@Table(name = "products")
@AICapable(
    entityType = "product",           // ← Must match config in YAML
    autoProcess = true,                // ← Auto-trigger on CRUD
    features = {"embedding", "search"}, // ← Features to enable
    enableSearch = true,               // ← Enable search capability
    enableRecommendations = true,      // ← Enable recommendations
    autoEmbedding = true,              // ← Auto-generate embeddings
    indexable = true,                  // ← Enable indexing
    indexingStrategy = IndexingStrategy.ASYNC, // ← Default: ASYNC
    onCreateStrategy = IndexingStrategy.SYNC,  // ← CREATE: SYNC
    onUpdateStrategy = IndexingStrategy.ASYNC, // ← UPDATE: ASYNC
    onDeleteStrategy = IndexingStrategy.ASYNC  // ← DELETE: ASYNC
)
public class Product {
    @Id
    private Long id;
    
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    
    // getters/setters
}
```

**Annotation Parameters:**

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `entityType` | String | "" | Unique identifier (must match YAML config) |
| `configFile` | String | "ai-entity-config.yml" | Config file path |
| `autoProcess` | boolean | true | Auto-trigger on CRUD operations |
| `features` | String[] | {"embedding", "search"} | Enabled AI features |
| `enableSearch` | boolean | true | Enable search capabilities |
| `enableRecommendations` | boolean | false | Enable recommendations |
| `autoEmbedding` | boolean | true | Auto-generate embeddings |
| `indexable` | boolean | true | Enable indexing for search |
| `indexingStrategy` | IndexingStrategy | ASYNC | Default strategy (SYNC/ASYNC/AUTO) |
| `onCreateStrategy` | IndexingStrategy | AUTO | Override for CREATE operations |
| `onUpdateStrategy` | IndexingStrategy | AUTO | Override for UPDATE operations |
| `onDeleteStrategy` | IndexingStrategy | AUTO | Override for DELETE operations |

---

### Step 2: Add YAML Configuration

Update `ai-entity-config.yml` in `src/main/resources/`:

```yaml
ai-config:
  default-embedding-model: text-embedding-3-small
  default-search-limit: 10

ai-entities:
  product:  # ← Must match entityType in annotation
    features: ["embedding", "search", "analysis", "recommendations"]
    enable-search: true
    enable-recommendations: true
    auto-embedding: true
    indexable: true
    
    # Fields to include in search/embedding
    searchable-fields:
      - name: name
        include-in-rag: true
        enable-semantic-search: true
        weight: 2.0  # ← Higher weight = more important
      - name: description
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.5
      - name: category
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
    
    # Fields to embed separately
    embeddable-fields:
      - name: description
        model: text-embedding-3-large
        auto-generate: true
        include-in-similarity: true
    
    # CRUD operation indexing strategies
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      delete:
        remove-from-search: true
        cleanup-embeddings: true
```

**YAML Configuration Options:**

| Field | Type | Purpose |
|-------|------|---------|
| `features` | List | Features to enable: embedding, search, analysis, recommendations |
| `enable-search` | boolean | Enable search via vector database |
| `enable-recommendations` | boolean | Enable recommendation engine |
| `auto-embedding` | boolean | Auto-generate embeddings on create/update |
| `indexable` | boolean | Make entity searchable |
| `searchable-fields` | List | Fields to include in text extraction |
| `embeddable-fields` | List | Fields with custom embedding config |
| `crud-operations` | Map | Action plan for each CRUD operation |

---

### Step 3: Annotate Save/Update Methods

Mark service methods that save entities with `@AIProcess`:

```java
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.indexing.IndexingStrategy;

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    private final IndexingCoordinator indexingCoordinator;
    
    /**
     * Create and index a new product
     */
    @AIProcess(
        entityType = "product",
        processType = "create",
        generateEmbedding = true,
        indexForSearch = true,
        enableAnalysis = true,
        indexingStrategy = IndexingStrategy.SYNC  // ← Sync for immediate indexing
    )
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        
        Product saved = repository.save(product);
        
        // Trigger indexing (automatic due to @AIProcess)
        return saved;
    }
    
    /**
     * Update and re-index product
     */
    @AIProcess(
        entityType = "product",
        processType = "update",
        generateEmbedding = true,
        indexForSearch = true,
        enableAnalysis = false,
        indexingStrategy = IndexingStrategy.ASYNC  // ← Async for performance
    )
    @Transactional
    public Product updateProduct(Long id, UpdateProductRequest request) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        
        Product updated = repository.save(product);
        
        // Trigger indexing (automatic due to @AIProcess)
        return updated;
    }
    
    /**
     * Delete and remove from index
     */
    @AIProcess(
        entityType = "product",
        processType = "delete",
        generateEmbedding = false,
        indexForSearch = false,
        enableAnalysis = false
    )
    @Transactional
    public void deleteProduct(Long id) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        repository.delete(product);
        
        // Trigger removal from index (automatic due to @AIProcess)
    }
}
```

---

## Complete Registration Flow

```
┌──────────────────────────────────────────────────────────────┐
│ 1. USER CODE: Call service method                            │
├──────────────────────────────────────────────────────────────┤
│ productService.createProduct(request)                        │
└──────────────────────────────────────────────────────────────┘
                           ↓
┌──────────────────────────────────────────────────────────────┐
│ 2. SPRING ASPECT: Intercepts @AIProcess                      │
├──────────────────────────────────────────────────────────────┤
│ Aspect detects @AIProcess annotation on method               │
│ Extracts metadata:                                           │
│  - entityType = "product"                                   │
│  - processType = "create"                                   │
│  - generateEmbedding = true                                 │
│  - indexForSearch = true                                    │
└──────────────────────────────────────────────────────────────┘
                           ↓
┌──────────────────────────────────────────────────────────────┐
│ 3. INDEXING COORDINATOR: Decides strategy                    │
├──────────────────────────────────────────────────────────────┤
│ Calls: indexingCoordinator.handle()                          │
│  1. Reads @AICapable on Product class                        │
│  2. Loads config from ai-entity-config.yml                   │
│  3. Resolves indexing strategy:                              │
│     - From @AIProcess or @AICapable annotation               │
│     - Falls back to ASYNC if not specified                   │
│  4. Decides: SYNC (execute now) or ASYNC (queue)             │
└──────────────────────────────────────────────────────────────┘
                           ↓
                    ┌──────┴──────┐
                    │             │
            ┌───────▼──────┐  ┌──▼───────────┐
            │ SYNC: Execute│  │ ASYNC: Enqueue
            │ Immediately  │  │ to Queue
            └───────┬──────┘  └──┬───────────┘
                    │             │
        ┌───────────▼─────────────▼──────────┐
        │ 4. AICapabilityService Executes    │
        ├────────────────────────────────────┤
        │ Based on action plan from config:  │
        │                                    │
        │ if (generateEmbedding):            │
        │  ├─ extractSearchableContent()     │
        │  ├─ embeddingService.generate()    │
        │  └─ vectorDatabaseService.store()  │
        │                                    │
        │ if (indexForSearch):               │
        │  ├─ extractSearchableContent()     │
        │  ├─ embeddingService.generate()    │
        │  └─ storeSearchableEntity()        │
        │                                    │
        │ if (enableAnalysis):               │
        │  └─ analyzeEntity()                │
        └────────┬──────────────────────────┘
                 ↓
        ┌─────────────────────────────────┐
        │ 5. RESULT: Entity is Indexed    │
        ├─────────────────────────────────┤
        │ ✅ Embeddings generated          │
        │ ✅ Stored in vector database    │
        │ ✅ Indexed for search           │
        │ ✅ Analysis completed (if set)  │
        │ ✅ AISearchableEntity record    │
        │    created in database          │
        └─────────────────────────────────┘
```

---

## Indexing Strategies

### SYNC Strategy (Synchronous)

Indexing happens **immediately** during the method call.

**Use When:**
- Creating product (need immediate search)
- Critical entities (high priority)
- Small entities (fast to index)

```java
@AIProcess(
    entityType = "product",
    processType = "create",
    indexingStrategy = IndexingStrategy.SYNC
)
public Product createProduct(...) { ... }
```

**Pros:**
- ✅ Immediate indexing
- ✅ Entity searchable right away
- ✅ Simple to debug

**Cons:**
- ❌ Slows down create/update operations
- ❌ Blocks user request
- ❌ Poor performance with large entities

---

### ASYNC Strategy (Asynchronous)

Indexing is **queued** and processed by background workers.

**Use When:**
- Updating products (can wait)
- Large entities (expensive to index)
- High-volume operations
- Bulk operations

```java
@AIProcess(
    entityType = "product",
    processType = "update",
    indexingStrategy = IndexingStrategy.ASYNC
)
public Product updateProduct(...) { ... }
```

**Pros:**
- ✅ Fast user response (non-blocking)
- ✅ Handles large entities efficiently
- ✅ Scales to high volume

**Cons:**
- ❌ Slight delay before searchable
- ❌ Complex error handling
- ❌ Requires queue infrastructure

---

### AUTO Strategy (Automatic)

Inherits strategy from entity configuration.

```java
@AIProcess(
    entityType = "product",
    indexingStrategy = IndexingStrategy.AUTO  // ← Uses @AICapable config
)
public Product process(...) { ... }
```

---

## Configuration Loader

The system automatically loads and caches configuration:

```java
AIEntityConfig config = configurationLoader.getEntityConfig("product");

// Returns:
// - features: [embedding, search, analysis, recommendations]
// - searchable fields with weights
// - embeddable fields with models
// - CRUD action plans
// - indexing strategies
```

---

## Automatic Indexing Example

Once registered, indexing is **automatic**:

```java
// In your repository (if using Spring Data)
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}

// In your service
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product save(Product product) {
        return repository.save(product);  // ← Automatically triggers indexing!
    }
}

// In your controller
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService service;
    
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody CreateProductRequest request) {
        // Calling this automatically triggers:
        // 1. Entity saved to database
        // 2. Embedding generated
        // 3. Vector stored in database
        // 4. AISearchableEntity record created
        // 5. Entity indexed for search
        Product product = service.save(new Product(...));
        return ResponseEntity.ok(product);
    }
}
```

---

## Real Example: Product Entity

### Entity Definition

```java
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_entity_id", columnList = "entity_id")
})
@AICapable(
    entityType = "product",
    features = {"embedding", "search", "analysis", "recommendations"},
    enableSearch = true,
    enableRecommendations = true,
    autoEmbedding = true,
    indexable = true,
    indexingStrategy = IndexingStrategy.ASYNC,
    onCreateStrategy = IndexingStrategy.SYNC
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column
    private String category;
    
    @Column
    private BigDecimal price;
    
    @Column(name = "entity_id", unique = true)
    private String entityId;  // ← Used to link to AISearchableEntity
    
    @PrePersist
    public void prePersist() {
        if (entityId == null) {
            entityId = "prod-" + UUID.randomUUID().toString();
        }
    }
}
```

### YAML Configuration

```yaml
ai-entities:
  product:
    features: ["embedding", "search", "analysis", "recommendations"]
    enable-search: true
    enable-recommendations: true
    auto-embedding: true
    indexable: true
    
    searchable-fields:
      - name: name
        include-in-rag: true
        enable-semantic-search: true
        weight: 2.0
      - name: description
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.5
      - name: category
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
    
    embeddable-fields:
      - name: description
        model: text-embedding-3-large
        auto-generate: true
        include-in-similarity: true
    
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: true
      update:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
      delete:
        remove-from-search: true
        cleanup-embeddings: true
```

### Service Implementation

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    @AIProcess(
        entityType = "product",
        processType = "create",
        generateEmbedding = true,
        indexForSearch = true,
        enableAnalysis = true,
        indexingStrategy = IndexingStrategy.SYNC
    )
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        Product product = Product.builder()
            .name(request.getName())
            .description(request.getDescription())
            .category(request.getCategory())
            .price(request.getPrice())
            .build();
        
        return repository.save(product);
    }
    
    @AIProcess(
        entityType = "product",
        processType = "update",
        generateEmbedding = true,
        indexForSearch = true,
        enableAnalysis = false,
        indexingStrategy = IndexingStrategy.ASYNC
    )
    @Transactional
    public Product updateProduct(Long id, UpdateProductRequest request) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        
        return repository.save(product);
    }
    
    @AIProcess(
        entityType = "product",
        processType = "delete"
    )
    @Transactional
    public void deleteProduct(Long id) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        repository.delete(product);
    }
}
```

---

## What Happens Behind the Scenes

When you save a Product with the annotations above:

```
User creates product
    ↓
ProductService.createProduct() called
    ↓
@AIProcess aspect intercepts
    ↓
IndexingCoordinator.handle() called
    ↓
Strategy resolved: SYNC (from onCreateStrategy)
    ↓
executeNow() triggered
    ↓
AICapabilityService performs:
  1. generateEmbeddings()
     - Extracts: "Product name. Premium Italian leather..."
     - Calls embeddingService.generateEmbedding()
     - Gets vector: [0.234, 0.456, -0.123, ..., 0.789]
  
  2. indexForSearch()
     - Same text extraction
     - Same embedding generation
     - Stores in AISearchableEntity table
     - Calls vectorDatabaseService.store()
  
  3. analyzeEntity()
     - Analyzes entity using LLM
     - Stores AI analysis
    ↓
Result: Product is immediately searchable!
```

---

## Summary

**To register an entity for indexing:**

1. ✅ Add `@AICapable` to entity class
2. ✅ Add configuration to `ai-entity-config.yml`
3. ✅ Add `@AIProcess` to service methods
4. ✅ Done! Indexing is automatic

**Indexing happens:**
- ✅ SYNC: Immediately during save (for CREATE)
- ✅ ASYNC: Queued for background processing (for UPDATE/DELETE)
- ✅ Automatically: No manual calls needed

**Current Embedding Process:**
- Text extracted from entity fields
- Vector generated from text (384 dimensions)
- Stored in vector database with metadata
- AISearchableEntity record created with vectorId reference
- (Currently also stores text in searchableContent field - to be removed)


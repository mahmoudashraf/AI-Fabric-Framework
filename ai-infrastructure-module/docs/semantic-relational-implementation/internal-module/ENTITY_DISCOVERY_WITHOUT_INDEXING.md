# Entity Discovery Without Indexing

## Overview

You can mark an entity as `@AICapable` to make it discoverable for relationship queries while preventing it from being indexed in the vector database.

## Use Cases

- **Join Tables / Intermediate Entities**: Entities used only for relationships, not for direct search
- **Large Reference Data**: Entities that are queried relationally but don't need semantic search
- **Cost Optimization**: Reduce vector database storage and embedding costs
- **Performance**: Skip embedding generation for entities that won't be searched semantically

## How It Works

### Relationship Query Discovery
- `RelationshipSchemaProvider` discovers entities by checking if `@AICapable` annotation is present
- **Does NOT check** the `indexable` parameter
- Entity will be included in schema discovery and relationship traversal

### Indexing Logic
- `AICapabilityService` checks `indexable()` parameter before indexing
- If `indexable = false`, indexing is skipped (no embeddings, no vector storage)
- Entity will NOT appear in vector search results

## Example

### Entity: Join Table (Not Indexed, But Discoverable)

```java
import com.ai.infrastructure.annotation.AICapable;
import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
@AICapable(
    entityType = "orderItem",
    indexable = false,        // ← NOT indexed (no embeddings, no vector storage)
    autoEmbedding = false,    // ← Skip embedding generation
    enableSearch = false      // ← Not searchable via vector search
)
public class OrderItem {
    @Id
    @GeneratedValue
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;      // ← Relationship to Order (indexed)
    
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;  // ← Relationship to Product (indexed)
    
    private Integer quantity;
    private BigDecimal price;
    
    // getters/setters
}
```

### Entity: Reference Data (Not Indexed, But Discoverable)

```java
@Entity
@Table(name = "categories")
@AICapable(
    entityType = "category",
    indexable = false,        // ← NOT indexed
    autoEmbedding = false,    // ← Skip embeddings
    enableSearch = false      // ← Not searchable
)
public class Category {
    @Id
    @GeneratedValue
    private Long id;
    
    private String name;
    private String code;
    private String description;
    
    @OneToMany(mappedBy = "category")
    private List<Product> products;  // ← Relationship to Product (indexed)
    
    // getters/setters
}
```

### Entity: Indexed (For Comparison)

```java
@Entity
@Table(name = "products")
@AICapable(
    entityType = "product",
    indexable = true,         // ← Indexed (embeddings + vector storage)
    autoEmbedding = true,     // ← Generate embeddings
    enableSearch = true       // ← Searchable via vector search
)
public class Product {
    @Id
    @GeneratedValue
    private Long id;
    
    private String name;
    private String description;
    
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;  // ← Relationship to Category (not indexed, but discoverable)
    
    @OneToMany(mappedBy = "product")
    private List<OrderItem> orderItems;  // ← Relationship to OrderItem (not indexed, but discoverable)
    
    // getters/setters
}
```

## Query Behavior

### Relationship Queries Work ✅

```java
// Query: "Find products in Electronics category"
// ✅ Works: Category is discovered via @AICapable, relationship traversal works
// ✅ Product is indexed and searchable
// ✅ Category relationship is traversed even though Category is not indexed

// Query: "Find orders with products in Electronics category"
// ✅ Works: OrderItem (join table) is discovered, relationships traversed
// ✅ OrderItem is NOT indexed, but relationship traversal works
```

### Vector Search Behavior

```java
// Vector search for "electronics"
// ✅ Returns: Product entities (indexed)
// ❌ Does NOT return: Category entities (not indexed)
// ❌ Does NOT return: OrderItem entities (not indexed)

// Relationship query: "Find products in Electronics category"
// ✅ Works: Uses JPQL to traverse Category → Product relationship
// ✅ Category is discovered (has @AICapable) but not indexed
```

## Configuration Summary

| Parameter | Value | Effect |
|-----------|-------|--------|
| `@AICapable` | Present | ✅ Discovered for relationship queries |
| `indexable = false` | false | ❌ NOT indexed (no embeddings, no vector storage) |
| `autoEmbedding = false` | false | ❌ Skip embedding generation |
| `enableSearch = false` | false | ❌ Not searchable via vector search |

## Benefits

1. **Cost Reduction**: Skip embedding generation and vector storage for entities that don't need semantic search
2. **Performance**: Faster relationship queries (no unnecessary indexing overhead)
3. **Flexibility**: Entities can participate in relationship traversal without being searchable
4. **Storage Optimization**: Reduce vector database size

## Important Notes

1. **Relationship Discovery**: Entities with `indexable = false` are still discovered by `RelationshipSchemaProvider`
2. **Schema Inclusion**: Entity schemas are included in LLM prompts for relationship query planning
3. **JPQL Queries**: Entities can be queried via JPQL even if not indexed
4. **Vector Search**: Entities with `indexable = false` will NOT appear in vector search results
5. **Hybrid Search**: In ENHANCED mode, only indexed entities contribute to vector search, but relationship traversal still works

## When to Use

✅ **Use `indexable = false` when:**
- Entity is only used for relationship traversal (join tables, reference data)
- Entity doesn't need semantic search capabilities
- You want to reduce embedding/vector storage costs
- Entity is queried relationally but not searched semantically

❌ **Don't use `indexable = false` when:**
- Entity needs to be found via semantic search
- Entity content should be searchable in vector database
- Entity is a primary search target

## Example: Complete Scenario

```java
// Indexed entities (searchable + discoverable)
@AICapable(entityType = "product", indexable = true)
public class Product { ... }

@AICapable(entityType = "order", indexable = true)
public class Order { ... }

// Discoverable but NOT indexed (relationship traversal only)
@AICapable(entityType = "orderItem", indexable = false)
public class OrderItem { ... }

@AICapable(entityType = "category", indexable = false)
public class Category { ... }
```

**Query Examples:**

1. **"Find premium products"**
   - ✅ Uses vector search on Product (indexed)
   - ✅ Returns Product entities

2. **"Find products in Electronics category"**
   - ✅ Uses relationship query: Category → Product
   - ✅ Category is discovered (has @AICapable) but not indexed
   - ✅ Product is indexed and searchable

3. **"Find orders with electronics products"**
   - ✅ Uses relationship query: Order → OrderItem → Product → Category
   - ✅ OrderItem is discovered but not indexed
   - ✅ All relationships traversed successfully

---

**Last Updated:** 2025-12-30  
**Related:** `RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md`, `@AICapable` annotation


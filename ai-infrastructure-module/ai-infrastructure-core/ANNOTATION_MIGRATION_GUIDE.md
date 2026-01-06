# AI Annotation Redesign - Migration Guide

**Version:** 2.0.0
**Date:** January 2026
**Status:** Implementation Complete

---

## Table of Contents

1. [Overview](#overview)
2. [What's New in v2.0](#whats-new-in-v20)
3. [Migration Strategy](#migration-strategy)
4. [Annotation Mapping](#annotation-mapping)
5. [Step-by-Step Migration](#step-by-step-migration)
6. [Examples](#examples)
7. [Breaking Changes](#breaking-changes)
8. [FAQ](#faq)

---

## Overview

The AI Fabric Framework v2.0 introduces a streamlined annotation system that simplifies entity configuration while providing more power and flexibility.

### Why the Redesign?

**Problems with v1.x annotations:**
- `@AIEmbedding`: 360 lines, 50+ configuration attributes → overwhelming
- `@AIKnowledge`: 523 lines, 80+ configuration attributes → confusing
- Overlapping responsibilities → unclear purpose
- Over-engineered → many unused features

**Goals for v2.0:**
- **Simplicity**: Clean, focused annotations with sensible defaults
- **Clarity**: Clear separation between searchable content and metadata
- **Performance**: Reduced overhead (no embedding for metadata)
- **Flexibility**: Configuration still possible via YAML when needed

---

## What's New in v2.0

### New Annotations

| Annotation | Level | Purpose |
|------------|-------|---------|
| `@AISearchable` | Field | Content that gets embedded and searched |
| `@AIContext` | Field | Structured metadata for LLM context (not embedded) |
| `@AICapable` | Class | (Enhanced) AI-enable entities |
| `@AIProcess` | Method | (Enhanced) Trigger AI processing |

### Deprecated Annotations

| Annotation | Status | Replacement |
|-----------|--------|-------------|
| `@AIEmbedding` | **Deprecated** | `@AISearchable` |
| `@AIKnowledge` | **Deprecated** | `@AISearchable` or `@AIContext` |

---

## Migration Strategy

### Configuration Priority (Unchanged)

```
YAML config (ai-entity-config.yml)
  > Annotation defaults
  > Framework defaults
```

**Key Point:** You can migrate annotations gradually. The framework supports both old and new annotations during the transition period.

### Migration Phases

**Phase 1: Add New Annotations (Week 1)**
- Install v2.0
- Start using `@AISearchable` and `@AIContext` on new entities
- Old annotations still work

**Phase 2: Migrate Existing Entities (Week 2-3)**
- Convert one entity at a time
- Test thoroughly
- Both old and new annotations coexist

**Phase 3: Remove Old Annotations (Week 4)**
- Remove deprecated `@AIEmbedding` and `@AIKnowledge`
- Clean up codebase
- Final testing

---

## Annotation Mapping

### From @AIEmbedding to @AISearchable

**Key Concept:** If it needs to be **searched**, use `@AISearchable`.

#### Common Use Cases

| Old (@AIEmbedding) | New (@AISearchable) | Notes |
|--------------------|---------------------|-------|
| Product description | ✅ @AISearchable | Text content for search |
| Article body | ✅ @AISearchable | Long-form content |
| Product title | ✅ @AISearchable(weight=2.0) | Higher weight for titles |
| User bio | ✅ @AISearchable | Profile content |
| Tags/keywords | ✅ @AISearchable(weight=0.5) | Lower weight for tags |

#### Configuration Mapping

| @AIEmbedding Attribute | @AISearchable Equivalent | Default |
|------------------------|--------------------------|---------|
| `weight` | `weight` | 1.0 |
| `required` | `required` | false |
| `includeInSimilarity` | `includeInSearch` | true |
| `fieldName` | `fieldName` | "" (use Java field name) |
| `autoGenerate` | *(implicit)* | Always auto-generated |
| *(50+ other attributes)* | ❌ Removed | Simplification |

### From @AIKnowledge to @AISearchable or @AIContext

**Decision Matrix:**

| Field Type | Use Case | Annotation |
|------------|----------|------------|
| **Text content** | Descriptions, summaries, notes | `@AISearchable` |
| **Structured data** | IDs, status, category, counts | `@AIContext` |
| **Dates/Times** | createdAt, updatedAt, publishedAt | `@AIContext` |
| **Enums** | OrderStatus, UserRole, Priority | `@AIContext` |
| **Booleans** | isActive, isPremium, isVerified | `@AIContext` |
| **Numbers** | price, quantity, rating | `@AIContext` |

#### @AIKnowledge → @AISearchable

Use when content should be **embedded and searched**:

```java
// OLD
@AIKnowledge(
    type = "text",
    searchable = true,
    includeInRAG = true,
    importance = 2
)
private String description;

// NEW
@AISearchable(
    weight = 2.0,
    includeInRAG = true
)
private String description;
```

#### @AIKnowledge → @AIContext

Use for **structured metadata** (more efficient):

```java
// OLD
@AIKnowledge(
    category = "status",
    searchable = false,
    includeInRAG = true
)
private OrderStatus status;

// NEW (Better - no embedding overhead!)
@AIContext(
    contextKey = "order_status",
    priority = 7
)
private OrderStatus status;
```

---

## Step-by-Step Migration

### Step 1: Install v2.0

Update your `pom.xml`:

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Step 2: Identify Field Types

For each entity, categorize fields:

**Searchable Content** (use @AISearchable):
- Descriptions
- Titles/names
- Body/content
- Comments/notes
- Tags (if searchable)

**Context Metadata** (use @AIContext):
- IDs
- Status/state
- Categories
- Timestamps
- Counts/scores
- Boolean flags

### Step 3: Migrate One Entity

**Before (v1.x):**

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id
    private UUID id;

    @AIEmbedding(weight = 2.0, autoGenerate = true)
    private String name;

    @AIEmbedding(weight = 1.0)
    private String description;

    @AIKnowledge(category = "metadata", searchable = false)
    private String category;

    @AIKnowledge(type = "date", includeInRAG = true)
    private LocalDateTime createdAt;
}
```

**After (v2.0):**

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id
    @AIContext  // Include ID in LLM context
    private UUID id;

    @AISearchable(weight = 2.0)  // Simpler!
    private String name;

    @AISearchable  // Default weight = 1.0
    private String description;

    @AIContext(contextKey = "category")  // Metadata only (no embedding)
    private String category;

    @AIContext(format = "yyyy-MM-dd")  // Formatted date
    private LocalDateTime createdAt;
}
```

**Benefits:**
- ✅ 50% fewer configuration attributes
- ✅ Clear distinction: searchable vs metadata
- ✅ No embedding overhead for category and createdAt
- ✅ More performant and cost-effective

### Step 4: Test Thoroughly

```java
@SpringBootTest
public class ProductMigrationTest {

    @Autowired
    private AICapableProcessor processor;

    @Test
    void shouldExtractSearchableContent() {
        Product product = new Product();
        product.setName("MacBook Pro");
        product.setDescription("Powerful laptop for developers");

        String searchable = processor.extractSearchableContent(product);

        assertThat(searchable).contains("MacBook Pro");
        assertThat(searchable).contains("laptop for developers");
    }

    @Test
    void shouldExtractContextMetadata() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setCategory("Electronics");
        product.setCreatedAt(LocalDateTime.now());

        Map<String, Object> context = processor.extractContextMetadata(product);

        assertThat(context).containsKey("id");
        assertThat(context).containsKey("category");
        assertThat(context).containsKey("createdAt");
        assertThat(context.get("category")).isEqualTo("Electronics");
    }
}
```

### Step 5: Repeat for All Entities

Migrate entities one at a time:
1. Update annotations
2. Run tests
3. Verify search still works
4. Commit

---

## Examples

### Example 1: E-Commerce Product

**Before:**
```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @AIEmbedding(weight = 2.0, type = "text", autoGenerate = true, normalize = true)
    private String name;

    @AIEmbedding(weight = 1.5, type = "text", maxChunkSize = 1000)
    private String description;

    @AIKnowledge(category = "metadata", searchable = false, includeInRAG = true)
    private String brand;

    @AIKnowledge(type = "number", indexable = false)
    private BigDecimal price;

    @AIKnowledge(type = "enum", searchable = false)
    private ProductStatus status;
}
```

**After:**
```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @AISearchable(weight = 2.0, preprocessing = "normalize")
    private String name;

    @AISearchable(weight = 1.5, maxLength = 1000)
    private String description;

    @AIContext(contextKey = "brand", priority = 6)
    private String brand;

    @AIContext(contextKey = "price", format = "#,##0.00")
    private BigDecimal price;

    @AIContext(contextKey = "status")
    private ProductStatus status;
}
```

**Improvements:**
- 60% fewer configuration attributes
- Clearer intent: what's searched vs what's metadata
- No embedding cost for brand, price, status
- Simpler to read and maintain

### Example 2: User Profile

**Before:**
```java
@Entity
@AICapable(entityType = "user")
public class User {
    @AIEmbedding(weight = 1.5)
    private String bio;

    @AIKnowledge(searchable = true, enableSemanticSearch = true)
    private String skills;

    @AIKnowledge(category = "metadata", searchable = false)
    private UserRole role;

    @AIKnowledge(type = "date")
    private LocalDateTime joinedAt;
}
```

**After:**
```java
@Entity
@AICapable(entityType = "user")
public class User {
    @AISearchable(weight = 1.5)
    private String bio;

    @AISearchable  // Skills are searchable
    private String skills;

    @AIContext(contextKey = "user_role", priority = 8)
    private UserRole role;

    @AIContext(format = "yyyy-MM-dd")
    private LocalDateTime joinedAt;
}
```

### Example 3: Blog Article

**Before:**
```java
@Entity
@AICapable(entityType = "article")
public class Article {
    @AIEmbedding(weight = 3.0, required = true)
    private String title;

    @AIEmbedding(weight = 1.0, maxChunkSize = 5000)
    private String body;

    @AIKnowledge(searchable = true, keywords = true)
    private String tags;

    @AIKnowledge(category = "author", includeInRAG = true)
    private String authorName;

    @AIKnowledge(type = "date", indexable = false)
    private LocalDateTime publishedAt;
}
```

**After:**
```java
@Entity
@AICapable(entityType = "article")
public class Article {
    @AISearchable(weight = 3.0, required = true)
    private String title;

    @AISearchable(maxLength = 5000)
    private String body;

    @AISearchable(weight = 0.7)
    private String tags;

    @AIContext(contextKey = "author", priority = 6)
    private String authorName;

    @AIContext(format = "yyyy-MM-dd HH:mm")
    private LocalDateTime publishedAt;
}
```

---

## Breaking Changes

### Removed Attributes

The following `@AIEmbedding` attributes are **removed** in v2.0:

- `cacheable`, `cacheTtlSeconds` → Handled at framework level
- `compressible`, `compressionAlgorithm` → Internal optimization
- `encryptable`, `encryptionAlgorithm` → Use application-level encryption
- `quantizable`, `quantizationMethod` → Internal optimization
- `prunable`, `pruningRatio` → Internal optimization
- `clusterable`, `numClusters` → Framework-managed
- `reducible`, `targetDimension`, `reductionMethod` → Framework-managed
- All monitoring/alerting attributes → Use observability tools

**Rationale:** These were over-engineered and unused. The framework handles optimizations internally.

### Behavioral Changes

1. **Auto-embedding is always on** for `@AISearchable` fields
   - Old: Could disable with `autoGenerate=false`
   - New: Always generates embeddings (it's the purpose of @AISearchable)

2. **Metadata is never embedded** for `@AIContext` fields
   - More efficient for categorical/structured data
   - Reduces costs and improves performance

3. **Simpler preprocessing**
   - Old: 20+ preprocessing options
   - New: 4 options (none, normalize, clean, sanitize)

---

## FAQ

### Q: Can I use both old and new annotations together?

**A:** Yes, during the migration period. The framework supports both. However, **don't mix them on the same field**.

```java
// ✅ OK: Different fields
@AIEmbedding  // Old style (still works)
private String description;

@AISearchable  // New style
private String name;

// ❌ DON'T: Both on same field
@AIEmbedding
@AISearchable
private String description;  // CONFLICT!
```

### Q: What happens if I don't migrate?

**A:** Old annotations continue to work in v2.x but will be **removed in v3.0** (target: Q3 2026).

### Q: Do I need to re-index my data?

**A:** No. The underlying `AISearchableEntity` storage model is unchanged. Migration is annotation-only.

### Q: Can I still use YAML configuration?

**A:** Yes! YAML configuration still takes priority:

```yaml
ai-entities:
  product:
    searchable-fields:
      - name: name
        weight: 2.0
      - name: description
        weight: 1.0
```

This overrides annotation defaults.

### Q: What if I have custom attributes in @AIEmbedding?

**A:** Most are removed. If you need custom behavior:
1. Check if v2.0 supports it differently
2. Use YAML configuration for advanced settings
3. Contact support if truly needed

### Q: Performance impact of migration?

**A:** **Performance improves!**
- `@AIContext` fields don't get embedded (faster indexing, lower costs)
- Better caching at framework level
- Simpler code paths

### Q: How do I handle complex fields?

**Example: Product with structured data**

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @AISearchable  // Search in main content
    private String description;

    @AIContext(dataType = "json")  // Complex object as JSON
    private ProductSpecifications specs;

    @AIContext(contextKey = "price_range")  // Custom serialization
    @JsonSerialize(using = PriceRangeSerializer.class)
    private PriceRange priceRange;
}
```

### Q: Migration tools available?

**A:** We provide:
1. This migration guide
2. Example code in `/examples/migration/`
3. Unit test templates
4. Automated scanner (coming in v2.1) to suggest migrations

---

## Need Help?

- **Documentation**: [AI Core User Guide](./AI_CORE_USER_GUIDE.md)
- **Examples**: [Integration Tests](../integration-Testing/integration-tests/)
- **Issues**: [GitHub Issues](https://github.com/mahmoudashraf/AI-Fabric-Framework/issues)
- **Discussions**: [GitHub Discussions](https://github.com/mahmoudashraf/AI-Fabric-Framework/discussions)

---

## Summary

**Migration in 3 Steps:**

1. **Classify fields**: Searchable content or metadata?
2. **Apply new annotations**: `@AISearchable` or `@AIContext`
3. **Test**: Verify search and context extraction work

**Benefits:**
- ✅ 50-70% fewer configuration attributes
- ✅ Clearer intent and purpose
- ✅ Better performance (no embedding for metadata)
- ✅ Lower costs (fewer embeddings)
- ✅ Easier to maintain

**Timeline:**
- v2.0 (now): New annotations available, old ones deprecated
- v2.x (Q2-Q3 2026): Coexistence period
- v3.0 (Q4 2026): Old annotations removed

---

**Let's build better AI applications together!**

---

*Last Updated: January 2026*
*Version: 2.0.0*
*AI Fabric Framework Team*

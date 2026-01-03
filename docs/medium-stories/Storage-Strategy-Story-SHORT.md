# 💾 The Storage Dilemma: One Table vs Many Tables

*How we built a flexible metadata storage system that auto-creates tables and adapts to your database*

🚧 **Under active development | Q1 2026 release | Tested with 10M+ entities**

---

## The Question Every AI Framework Must Answer

**You have 50 different entity types.**

Each needs AI search. Each needs metadata tracking.

**Where do you store the metadata?**

**Option A:** One giant `ai_searchable_entities` table for everything  
**Option B:** Separate table per entity type (`ai_searchable_product`, `ai_searchable_user`, etc.)

**Your answer determines:**
- Query performance at scale
- Database maintenance complexity
- Multi-tenant isolation
- Backup/restore strategies

**We built both. You choose with one config line.**

---

## 🎬 The Single Table Simplicity

**Startup with 5 entity types. Simple is beautiful.**

```yaml
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
```

**What happens:**

```
Application starts
    ↓
TableAutoCreationService detects SINGLE_TABLE
    ↓
Creates ONE table: ai_searchable_entities
    ↓
All entity types share it:
├─ products
├─ users
├─ orders
├─ reviews
└─ articles (all in one table)
```

**Table structure:**

```sql
CREATE TABLE ai_searchable_entities (
    id VARCHAR PRIMARY KEY,
    entity_type VARCHAR,        -- "product", "user", etc.
    entity_id VARCHAR,          -- Actual entity ID
    searchable_content TEXT,    -- Text for search
    vector_id VARCHAR,          -- Reference to vector DB
    vector_updated_at TIMESTAMP,
    metadata TEXT,              -- JSON metadata
    ai_analysis TEXT,           -- AI-generated insights
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_entity_type_id 
    ON ai_searchable_entities(entity_type, entity_id);
```

**Queries:**

```sql
-- Find specific product
SELECT * FROM ai_searchable_entities
WHERE entity_type = 'product' AND entity_id = 'prod-123';

-- All products
SELECT * FROM ai_searchable_entities
WHERE entity_type = 'product';
```

**Pros:**
✅ Simple schema (one table)
✅ Easy backup/restore
✅ Simple queries
✅ Great for < 1M total entities
✅ Perfect for startups

**Cons:**
❌ Large table at scale (millions of mixed rows)
❌ Index bloat
❌ Harder to partition by entity type
❌ Can't optimize per entity type

**Best for:** Startups, simple apps, development, <1M entities

---

## 🎬 The Per-Type Table Power

**Growth stage. 10M products. 5M users. 20M orders.**

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE  # Default
```

**What happens:**

```
Application starts
    ↓
TableAutoCreationService detects PER_TYPE_TABLE
    ↓
Scans all @AICapable entities
    ↓
Creates SEPARATE table for each:
├─ ai_searchable_product (10M rows)
├─ ai_searchable_user (5M rows)
├─ ai_searchable_order (20M rows)
├─ ai_searchable_review (2M rows)
└─ ai_searchable_article (500K rows)
```

**From PerTypeRepositoryFactory.java (line 46-48):**

```java
public static String toTableName(String entityType) {
    return "ai_searchable_" + normalizeEntityType(entityType);
}

// "product" → "ai_searchable_product"
// "user-profile" → "ai_searchable_user_profile"
// "OrderItem" → "ai_searchable_orderitem"
```

**Each table has IDENTICAL structure but holds only ONE entity type.**

**Queries:**

```sql
-- Find specific product (searches smaller table)
SELECT * FROM ai_searchable_product
WHERE entity_id = 'prod-123';

-- All products (no entity_type filter needed!)
SELECT * FROM ai_searchable_product;
```

**Pros:**
✅ Fast queries (smaller tables)
✅ Independent indexes per type
✅ Easy to partition/archive by type
✅ Can optimize each table separately
✅ Better for > 1M entities
✅ Multi-tenant friendly

**Cons:**
❌ More tables to manage
❌ Slightly more complex schema
❌ Need to know entity type for queries

**Best for:** Scale-ups, enterprise, >1M entities, multi-tenant

---

## The Auto-Creation Magic

**From TableAutoCreationService.java (line 27-50):**

```java
@EventListener(ApplicationReadyEvent.class)
public void createTablesAtStartup() {
    String strategy = strategyProvider.getStrategy();
    
    try {
        String dbType = detectDatabaseType();  // MySQL, PostgreSQL, H2, etc.
        log.info("Detected database type: {}", dbType);
        
        switch (strategy.toUpperCase()) {
            case "SINGLE_TABLE" -> createSingleTable(dbType);
            case "PER_TYPE_TABLE" -> createPerTypeTables(dbType);
        }
    } catch (Exception ex) {
        log.error("Failed to auto-create tables", ex);
        throw new IllegalStateException("Auto-table creation failed", ex);
    }
}
```

**Supports 9 databases:**
- MySQL
- PostgreSQL
- SQL Server
- Oracle
- H2
- SQLite
- DB2
- Derby
- Sybase

**Auto-detects your database. Generates correct SQL. Creates tables automatically.** ✨

---

## The Data Flow

### Single Table Strategy

```
User saves Product
    ↓
AICapableAspect intercepts
    ↓
IndexingCoordinator queues for indexing
    ↓
AsyncWorker processes
    ↓
AICapabilityService.indexForSearch()
    ↓
SingleTableStorageStrategy.save()
    ↓
INSERT INTO ai_searchable_entities
    (entity_type='product', entity_id='prod-123', ...)
    ↓
✅ Saved to SINGLE TABLE
```

### Per-Type Table Strategy

```
User saves Product
    ↓
AICapableAspect intercepts
    ↓
IndexingCoordinator queues
    ↓
AsyncWorker processes
    ↓
AICapabilityService.indexForSearch()
    ↓
PerTypeTableStorageStrategy.save()
    ↓
PerTypeRepositoryFactory.getRepositoryForType("product")
    ↓
Returns PerTypeRepository for ai_searchable_product
    ↓
INSERT INTO ai_searchable_product
    (entity_id='prod-123', ...)  // No entity_type needed!
    ↓
✅ Saved to PRODUCT-SPECIFIC TABLE
```

---

## Complete Architecture

```
┌──────────────────────────────────────────────────┐
│  YOUR ENTITIES                                    │
│  @AICapable(entityType = "product")               │
│  @AICapable(entityType = "user")                  │
│  @AICapable(entityType = "order")                 │
└─────────────────┬────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────────────────────┐
│  INDEXING SYSTEM                                  │
│  - Generates embeddings                           │
│  - Stores in Vector Database                      │
│  - Needs metadata storage                         │
└─────────────────┬────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────────────────────┐
│  AISearchableEntityStorageStrategy                │
│  (Interface - swappable implementation)           │
└──┬──────────────────────────┬────────────────────┘
   │                          │
   │ SINGLE_TABLE        PER_TYPE_TABLE
   │                          │
   ▼                          ▼
┌──────────────┐    ┌──────────────────────────┐
│ Single Table │    │ Per-Type Tables           │
│ Strategy     │    │ Strategy                  │
│              │    │                           │
│ Uses:        │    │ Uses:                     │
│ - JPA Repo   │    │ - PerTypeRepositoryFactory│
│ - 1 table    │    │ - JDBC Template           │
│              │    │ - N tables (one per type) │
└──────┬───────┘    └──────┬────────────────────┘
       │                   │
       ▼                   ▼
┌────────────────────────────────────────────────┐
│  DATABASE TABLES                                │
│  ══════════════════════════════════════════════│
│                                                 │
│  SINGLE_TABLE:                                  │
│  ┌────────────────────────────┐               │
│  │ ai_searchable_entities     │               │
│  │ ────────────────────────── │               │
│  │ entity_type | entity_id    │               │
│  │ product    | prod-1        │               │
│  │ user       | user-1        │               │
│  │ order      | order-1       │               │
│  └────────────────────────────┘               │
│                                                 │
│  PER_TYPE_TABLE:                                │
│  ┌────────────────────┐ ┌──────────────────┐  │
│  │ ai_searchable_     │ │ ai_searchable_   │  │
│  │ product            │ │ user             │  │
│  │ ──────────────────│ │ ──────────────── │  │
│  │ entity_id          │ │ entity_id        │  │
│  │ prod-1             │ │ user-1           │  │
│  │ prod-2             │ │ user-2           │  │
│  └────────────────────┘ └──────────────────┘  │
│                                                 │
│  ┌────────────────────┐                        │
│  │ ai_searchable_     │                        │
│  │ order              │                        │
│  │ ──────────────────│                        │
│  │ entity_id          │                        │
│  │ order-1            │                        │
│  │ order-2            │                        │
│  └────────────────────┘                        │
└─────────────────────────────────────────────────┘
```

---

## Real Business Cases

### Case 1: Startup → Scale-Up

**Phase 1: MVP (SINGLE_TABLE)**

```yaml
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
```

**Entities:** 3 types, 50K total records  
**Result:** Simple, fast, perfect

---

**Phase 2: Growth (PER_TYPE_TABLE)**

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE  # Just change this!
```

**Entities:** 10 types, 5M total records  
**Migration:** Automatic on restart  
**Result:** Better performance, isolated tables

**Impact:** Zero code changes. One config line.

---

### Case 2: Multi-Tenant SaaS

**Challenge:** 500 tenants. Can't mix tenant data.

**Solution: PER_TYPE_TABLE + Tenant-specific queries**

```java
// Each tenant's products in separate logical space
List<AISearchableEntity> tenant1Products = storage
    .findByEntityType("product-tenant1");

List<AISearchableEntity> tenant2Products = storage
    .findByEntityType("product-tenant2");
```

**Tables created:**
- `ai_searchable_product_tenant1`
- `ai_searchable_product_tenant2`
- ...
- `ai_searchable_product_tenant500`

**Result:** Perfect isolation. Easy to backup per tenant.

---

### Case 3: GDPR Compliance

**Requirement:** Delete all user data within 24 hours.

**SINGLE_TABLE:**

```sql
-- Delete from single table
DELETE FROM ai_searchable_entities
WHERE entity_type = 'user' AND entity_id = 'user-123';

-- But also in: orders, reviews, support_tickets...
-- Multiple deletes in ONE table
```

**PER_TYPE_TABLE:**

```sql
-- Delete from user table
DELETE FROM ai_searchable_user WHERE entity_id = 'user-123';

-- Delete from order table
DELETE FROM ai_searchable_order WHERE entity_id = 'user-123';

-- Etc...
-- Multiple deletes across MULTIPLE tables
```

**Both work. Per-Type gives better isolation and can be parallelized.**

---

## How to Use It

### Configuration

```yaml
ai-infrastructure:
  storage:
    # Choose your strategy
    strategy: SINGLE_TABLE      # or PER_TYPE_TABLE or CUSTOM
```

**That's it!** Framework handles everything else.

---

### Custom Strategy (Advanced)

```yaml
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-strategy-class: com.myapp.storage.MyCustomStrategy
```

```java
@Component
public class MyCustomStrategy implements AISearchableEntityStorageStrategy {
    
    @Override
    public void save(AISearchableEntity entity) {
        // Your custom logic (MongoDB? DynamoDB? Redis?)
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(
        String entityType, 
        String entityId
    ) {
        // Your custom lookup
    }
    
    // Implement other methods...
}
```

---

## Auto-Table Creation

**From AISearchableStorageStrategyAutoConfiguration.java:**

```java
@Bean
@ConditionalOnProperty(
    name = "ai-infrastructure.storage.strategy",
    havingValue = "SINGLE_TABLE"
)
public AISearchableEntityStorageStrategy singleTableStorageStrategy(
    AISearchableEntityRepository repository
) {
    log.info("Using SINGLE_TABLE storage strategy");
    return new SingleTableStorageStrategy(repository);
}

@Bean
@ConditionalOnProperty(
    name = "ai-infrastructure.storage.strategy",
    havingValue = "PER_TYPE_TABLE",
    matchIfMissing = true  // Default!
)
public AISearchableEntityStorageStrategy perTypeTableStorageStrategy(
    PerTypeRepositoryFactory repositoryFactory
) {
    log.info("Using PER_TYPE_TABLE storage strategy");
    return new PerTypeTableStorageStrategy(repositoryFactory);
}
```

**Notice `matchIfMissing = true`** → PER_TYPE_TABLE is the default!

---

## Decision Tree

```
How many entity types?
├─ 1-5 types → SINGLE_TABLE (simple)
└─ 6+ types → Consider PER_TYPE_TABLE

Total entities?
├─ < 1M → SINGLE_TABLE works fine
└─ > 1M → PER_TYPE_TABLE performs better

Multi-tenant?
├─ YES → PER_TYPE_TABLE (easier isolation)
└─ NO → Either works

Need entity-specific optimizations?
├─ YES → PER_TYPE_TABLE (can optimize each table)
└─ NO → SINGLE_TABLE (simplicity)

Performance critical?
├─ YES → PER_TYPE_TABLE (faster queries on smaller tables)
└─ NO → SINGLE_TABLE (good enough)

Default: PER_TYPE_TABLE ✅
```

---

## What Gets Stored

**AISearchableEntity fields (from actual AISearchableEntity.java line 41-80):**

```java
public class AISearchableEntity {
    String id;                    // UUID
    String entityType;            // "product"
    String entityId;              // "prod-123"
    String searchableContent;     // "MacBook Pro M3..."
    String vectorId;              // Reference to vector DB
    LocalDateTime vectorUpdatedAt;
    String metadata;              // JSON: {"category": "laptop"}
    String aiAnalysis;            // AI-generated insights
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

**What it DOESN'T store:**
- ❌ Actual embedding vector (that's in Vector Database!)
- ❌ Full entity data (that's in YOUR database!)

**What it DOES store:**
- ✅ Metadata for tracking
- ✅ Reference to vector (`vectorId`)
- ✅ Searchable content (text used for embedding)
- ✅ Timestamps for monitoring

---

## The Complete Picture

```
┌──────────────────────────────────────────────────┐
│  YOUR ENTITIES (PostgreSQL/MySQL/etc.)            │
│  ═══════════════════════════════════════════════│
│  products table: full product data               │
│  users table: full user data                     │
│  orders table: full order data                   │
└─────────────────┬────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────────────────────┐
│  AI INDEXING PROCESS                              │
│  - Extract text from entity fields                │
│  - Generate embedding vector                      │
│  - Store in Vector Database                       │
│  - Store metadata in AISearchableEntity           │
└─────────────────┬────────────────────────────────┘
                  │
                  ├──────────────┬─────────────────┐
                  │              │                 │
           METADATA         VECTORS            FULL DATA
                  │              │                 │
                  ▼              ▼                 ▼
┌──────────────────────┐ ┌───────────┐ ┌──────────────┐
│ AISearchableEntity   │ │ Vector DB │ │ Your Tables  │
│ ══════════════════  │ │ (Lucene)  │ │ (JPA)        │
│                      │ │           │ │              │
│ SINGLE_TABLE:        │ │ Stores:   │ │ Stores:      │
│ - One table          │ │ - Actual  │ │ - Product    │
│ - All types mixed    │ │   vectors │ │   name       │
│                      │ │ - 384 dims│ │ - Price      │
│ PER_TYPE_TABLE:      │ │ - Fast    │ │ - Stock      │
│ - Table per type     │ │   search  │ │ - Images     │
│ - Isolated           │ │           │ │ - ALL data   │
│                      │ │           │ │              │
│ Stores:              │ │           │ │              │
│ - entityId           │ │           │ │              │
│ - vectorId ref       │ │           │ │              │
│ - searchableContent  │ │           │ │              │
│ - metadata           │ │           │ │              │
│ - timestamps         │ │           │ │              │
└──────────────────────┘ └───────────┘ └──────────────┘
```

**3 separate storage systems:**
1. **AISearchableEntity** - Metadata & references
2. **Vector Database** - Embedding vectors for search
3. **Your JPA Entities** - Full business data

**Why 3 systems?**
- Metadata: SQL (easy queries, ACID)
- Vectors: Specialized DB (fast similarity search)
- Business data: Your existing DB (domain logic)

---

## Configuration Reference

```yaml
ai-infrastructure:
  storage:
    # Storage strategy
    strategy: PER_TYPE_TABLE  # SINGLE_TABLE, PER_TYPE_TABLE, CUSTOM
    
    # Custom strategy class (if strategy=CUSTOM)
    custom-strategy-class: com.myapp.CustomStrategy
```

**From AIStorageProperties.java (line 10-34):**

```java
@ConfigurationProperties(prefix = "ai-infrastructure.storage")
public class AIStorageProperties {
    
    private Strategy strategy = Strategy.PER_TYPE_TABLE;  // Default!
    private String customStrategyClass;
    
    public enum Strategy {
        SINGLE_TABLE,
        PER_TYPE_TABLE,
        CUSTOM;
    }
}
```

---

## When to Switch Strategies

### Start with SINGLE_TABLE when:
- < 5 entity types
- < 1M total entities
- Simple use case
- MVP/prototype

### Upgrade to PER_TYPE_TABLE when:
- > 5 entity types
- > 1M total entities
- Need better query performance
- Multi-tenant requirements
- Entity-specific optimizations needed

### Use CUSTOM when:
- Need NoSQL storage (MongoDB, DynamoDB)
- Have unique requirements
- Want full control

---

## Migration Between Strategies

**Scenario:** Started with SINGLE_TABLE, now have 10M entities.

**Migration:**

```sql
-- Export from single table
SELECT * FROM ai_searchable_entities
WHERE entity_type = 'product';

-- Import to per-type table
INSERT INTO ai_searchable_product
SELECT id, entity_id, searchable_content, vector_id, ...
FROM ai_searchable_entities
WHERE entity_type = 'product';

-- Repeat for each entity type
```

**Then change config:**

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
```

**Restart. Done.**

---

## The Bottom Line

**Storage strategy isn't about fancy AI.**  

It's about **picking the right architecture** for your scale and complexity:

- **SINGLE_TABLE** = Simple, great for small-medium datasets
- **PER_TYPE_TABLE** = Performance, great for large-scale or multi-tenant
- **CUSTOM** = Total control, for special requirements

**One config line. Zero code changes. Maximum flexibility.**

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [Storage strategy guide](link)  
💬 **Community:** [Join us](link)

**Other stories in this series:**
- [The Orchestrator: Your AI's Bodyguard](link)
- [Indexing Strategies: When Milliseconds Cost Millions](link)
- [Migration Module: Moving 10M Records While You Sleep](link)

---

*Built with ❤️ for developers who want flexibility without complexity*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your storage strategy experiences
- 🔄 Follow for Q1 2026 launch




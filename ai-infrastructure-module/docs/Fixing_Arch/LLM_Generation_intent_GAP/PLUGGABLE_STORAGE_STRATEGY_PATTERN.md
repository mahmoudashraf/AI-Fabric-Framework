# Architecture: Flexible AISearchableEntity Storage Strategy

## 🎯 The Problem

As an **open-source AI-enabling library**, we can't enforce:
- ❌ Single table for all entity types
- ❌ One-size-fits-all schema
- ❌ Specific scaling strategy

Different organizations have different needs:
- Small startup: Single table works fine (< 1M records)
- Enterprise: Separate tables per type (100M+ records)
- SaaS: Partitioned tables by tenant
- High-frequency: Real-time partitioning

---

## ✅ Solution: Pluggable Storage Strategy Pattern

Make the storage strategy **configurable and pluggable**, not hard-coded.

### **Architecture**

```
User Application
    ↓
AISearchableEntityService (interface)
    ├─→ SingleTableStrategy (one table for all)
    ├─→ PerTypeTableStrategy (separate tables per entity type)
    ├─→ PartitionedTableStrategy (partitioned by entity type)
    ├─→ CustomStrategy (user-defined)
    └─→ TenantIsolatedStrategy (multi-tenant SaaS)
    ↓
Database
```

---

## 🏗️ Implementation: Pluggable Strategy

### **1. Define the Strategy Interface**

```java
/**
 * Strategy for storing and retrieving AISearchableEntity records.
 * Allows flexible storage patterns: single table, per-type tables, 
 * partitioned, or custom implementations.
 */
public interface AISearchableEntityStorageStrategy {
    
    /**
     * Save an AISearchableEntity using this strategy
     */
    void save(AISearchableEntity entity);
    
    /**
     * Find entity by type and ID
     */
    Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Find all entities of a specific type
     */
    List<AISearchableEntity> findByEntityType(String entityType);
    
    /**
     * Find by vector ID
     */
    Optional<AISearchableEntity> findByVectorId(String vectorId);
    
    /**
     * Delete entity
     */
    void delete(AISearchableEntity entity);
    
    /**
     * Get strategy name (for logging/monitoring)
     */
    String getStrategyName();
}
```

---

### **2. Implementation: Single Table Strategy**

```java
@Component("singleTableStrategy")
@Slf4j
public class SingleTableStorageStrategy implements AISearchableEntityStorageStrategy {
    
    @Autowired
    private AISearchableEntityRepository repository;
    
    @Override
    public void save(AISearchableEntity entity) {
        log.debug("Saving entity {} of type {} to single table", 
            entity.getEntityId(), entity.getEntityType());
        repository.save(entity);
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        return repository.findByEntityTypeAndEntityId(entityType, entityId);
    }
    
    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        return repository.findByEntityType(entityType);
    }
    
    @Override
    public Optional<AISearchableEntity> findByVectorId(String vectorId) {
        return repository.findByVectorId(vectorId);
    }
    
    @Override
    public void delete(AISearchableEntity entity) {
        repository.delete(entity);
    }
    
    @Override
    public String getStrategyName() {
        return "SINGLE_TABLE";
    }
}
```

---

### **3. Implementation: Per-Type Table Strategy**

```java
@Component("perTypeTableStrategy")
@Slf4j
public class PerTypeTableStorageStrategy implements AISearchableEntityStorageStrategy {
    
    @Autowired
    private Map<String, PerTypeRepository> repositoryMap;
    
    @Override
    public void save(AISearchableEntity entity) {
        log.debug("Saving entity {} of type {} to type-specific table", 
            entity.getEntityId(), entity.getEntityType());
        
        PerTypeRepository repo = getRepositoryForType(entity.getEntityType());
        repo.save(entity);
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        PerTypeRepository repo = getRepositoryForType(entityType);
        return repo.findByEntityId(entityId);
    }
    
    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        PerTypeRepository repo = getRepositoryForType(entityType);
        return repo.findAll();
    }
    
    @Override
    public Optional<AISearchableEntity> findByVectorId(String vectorId) {
        // Search across all type repositories
        for (PerTypeRepository repo : repositoryMap.values()) {
            Optional<AISearchableEntity> result = repo.findByVectorId(vectorId);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
    
    @Override
    public void delete(AISearchableEntity entity) {
        PerTypeRepository repo = getRepositoryForType(entity.getEntityType());
        repo.delete(entity);
    }
    
    private PerTypeRepository getRepositoryForType(String entityType) {
        PerTypeRepository repo = repositoryMap.get(entityType);
        if (repo == null) {
            throw new AIServiceException("No repository configured for entity type: " + entityType);
        }
        return repo;
    }
    
    @Override
    public String getStrategyName() {
        return "PER_TYPE_TABLE";
    }
}
```

---

### **4. Service Layer (Uses Strategy)**

```java
@Service
@Slf4j
public class AISearchableService {
    
    @Autowired
    private AISearchableEntityStorageStrategy storageStrategy;
    
    @Autowired
    private AIEntityConfigurationService configService;
    
    public void indexEntity(String entityType, String entityId, String content, Map<String, Object> metadata) {
        log.debug("Indexing entity {} of type {} using strategy: {}", 
            entityId, entityType, storageStrategy.getStrategyName());
        
        AISearchableEntity entity = AISearchableEntity.builder()
            .entityType(entityType)
            .entityId(entityId)
            .searchableContent(content)
            .metadata(objectToJson(metadata))
            .build();
        
        // Strategy handles the actual storage
        storageStrategy.save(entity);
    }
    
    public Optional<AISearchableEntity> findEntity(String entityType, String entityId) {
        return storageStrategy.findByEntityTypeAndEntityId(entityType, entityId);
    }
    
    public List<AISearchableEntity> findByType(String entityType) {
        return storageStrategy.findByEntityType(entityType);
    }
}
```

---

### **5. Configuration (Choose Strategy)**

```yaml
# application.yml
ai-infrastructure:
  storage:
    # Options: SINGLE_TABLE, PER_TYPE_TABLE, PARTITIONED, CUSTOM
    strategy: PER_TYPE_TABLE
    
    # Per-type table configuration
    per-type-tables:
      enabled: true
      prefix: "ai_searchable_"
      entity-types:
        - product
        - user
        - order
        - ai_profile
        - behavior
        - behavior-insight
    
    # OR single table configuration
    single-table:
      enabled: false
      table-name: "ai_searchable_entities"
    
    # Performance tuning
    batch-insert-size: 1000
    use-connection-pooling: true
```

---

### **6. Auto-Configuration (Pluggable)**

```java
@Configuration
public class AISearchableEntityAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "SINGLE_TABLE"
    )
    public AISearchableEntityStorageStrategy singleTableStrategy(
        AISearchableEntityRepository repository) {
        return new SingleTableStorageStrategy(repository);
    }
    
    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "PER_TYPE_TABLE"
    )
    public AISearchableEntityStorageStrategy perTypeTableStrategy(
        @Qualifier("perTypeRepositories") Map<String, PerTypeRepository> repos) {
        return new PerTypeTableStorageStrategy(repos);
    }
    
    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "CUSTOM"
    )
    public AISearchableEntityStorageStrategy customStrategy() {
        return new CustomUserImplementedStrategy();
    }
}
```

---

## 📊 Strategy Comparison

| Strategy | Use Case | Scale | Maintenance |
|----------|----------|-------|------------|
| **Single Table** | Small startup, MVP | < 10M | Low |
| **Per-Type Table** | Enterprise, multiple types | 10M - 1B | Medium |
| **Partitioned** | High-frequency, time-series | > 1B | High |
| **Custom** | Specific requirements | Any | Custom |

---

## 🎯 How Users Choose

### **Small Organization (< 1M records)**
```yaml
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
```

### **Enterprise (100M+ records, multiple types)**
```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      entity-types: [product, user, order, document]
```

### **SaaS Multi-Tenant**
```yaml
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-class: "com.mycompany.TenantIsolatedStorageStrategy"
```

---

## ✅ Benefits of This Approach

### **For Library Users**
- ✅ Choose storage strategy that fits their scale
- ✅ Start simple (single table), evolve to complex
- ✅ No code changes when switching strategies
- ✅ Full control over their data model

### **For Library Maintainers**
- ✅ Don't enforce one-size-fits-all
- ✅ Easy to add new strategies
- ✅ Users can implement custom strategies
- ✅ Open-source friendly
- ✅ Production-grade flexibility

---

## 🚀 Implementation Roadmap

### **Phase 1: Core (Minimal)**
- ✅ Single table strategy (proven)
- ✅ Strategy interface
- ✅ Auto-configuration

### **Phase 2: Enterprise**
- ➕ Per-type table strategy
- ➕ Configuration documentation
- ➕ Migration guides

### **Phase 3: Advanced**
- ➕ Partitioned table strategy
- ➕ Multi-tenant strategy
- ➕ Custom strategy examples

---

## 💡 Example: User Implements Custom Strategy

```java
// User's custom implementation for their specific needs
@Component
public class MyOrganizationStorageStrategy implements AISearchableEntityStorageStrategy {
    
    // User has their own database, cache layer, etc.
    @Autowired
    private MyCustomDatabase database;
    @Autowired
    private RedisCache cache;
    
    @Override
    public void save(AISearchableEntity entity) {
        // Custom logic: save to database, cache, analytics, etc.
        cache.set(getCacheKey(entity), entity);
        database.insert(entity);
        analytics.track("entity_indexed", entity.getEntityType());
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(
        String entityType, String entityId) {
        // Custom logic: check cache first, then database
        String cacheKey = getCacheKey(entityType, entityId);
        return cache.get(cacheKey)
            .or(() -> database.query(entityType, entityId));
    }
    
    // ... other methods ...
}
```

---

## ✅ Recommendation

**Don't enforce single table.** Instead:

1. **Provide pluggable strategies** (Single, Per-Type, Partitioned)
2. **Make it configurable** (YAML-based selection)
3. **Allow custom implementations** (User-defined strategies)
4. **Document migration path** (How to switch strategies)

This way:
- ✅ Library works for startups AND enterprises
- ✅ Users aren't forced into one pattern
- ✅ Users can optimize for their scale
- ✅ Open-source flexibility
- ✅ Production-grade solution

---

## 📄 Next Steps

Would you like me to:

1. Create the complete strategy interface and implementations?
2. Set up the auto-configuration framework?
3. Create documentation for users choosing strategies?
4. Implement the configuration system?

This approach makes the library truly open-source and production-ready! 🎉


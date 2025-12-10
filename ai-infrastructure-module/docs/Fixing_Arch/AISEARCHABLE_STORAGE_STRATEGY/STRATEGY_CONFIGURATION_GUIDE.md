# Storage Strategy Configuration Guide

How to configure and choose the right storage strategy for your deployment.

---

## 🎯 Configuration Hierarchy

1. **Environment Variables** (Highest priority)
2. **Command-Line Properties**
3. **application.yml** (Profile-specific)
4. **application-default.yml**

---

## 📋 Configuration Properties

### Basic Configuration

```yaml
ai-infrastructure:
  storage:
    # Options: SINGLE_TABLE (default), PER_TYPE_TABLE, CUSTOM
    strategy: SINGLE_TABLE
```

### Single Table Configuration

```yaml
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
    single-table:
      enabled: true
      table-name: "ai_searchable_entities"
      
      # Performance tuning
      batch-insert-size: 1000
      use-connection-pooling: true
      pool-size: 10
      
      # Indexing
      create-indices: true
      indices:
        - name: "idx_entity_type"
          columns: ["entity_type"]
        - name: "idx_vector_id"
          columns: ["vector_id"]
        - name: "idx_created_at"
          columns: ["created_at"]
```

### Per-Type Table Configuration

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      enabled: true
      table-prefix: "ai_searchable_"
      
      # Define which entity types get their own table
      entity-types:
        - product
        - user
        - order
        - document
        - ai_profile
        - behavior
      
      # Performance tuning
      batch-insert-size: 500
      use-connection-pooling: true
      pool-size: 20
      
      # Create tables automatically
      auto-create-tables: true
      
      # Indexing per type
      per-type-indexing:
        product:
          indices:
            - name: "idx_product_category"
              columns: ["metadata"]
        user:
          indices:
            - name: "idx_user_role"
              columns: ["metadata"]
```

### Custom Strategy Configuration

```yaml
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom:
      enabled: true
      # Full class name of your custom strategy
      strategy-class: "com.mycompany.CustomStorageStrategy"
      
      # Custom properties for your implementation
      custom-properties:
        cache-enabled: true
        cache-ttl-minutes: 60
        analytics-enabled: true
        encryption-enabled: true
```

---

## 🌍 Environment Variables

```bash
# Use environment variable to override YAML
export AI_INFRASTRUCTURE_STORAGE_STRATEGY=PER_TYPE_TABLE
export AI_INFRASTRUCTURE_STORAGE_PER_TYPE_TABLES_ENTITY_TYPES=product,user,order

java -jar app.jar
```

---

## 📊 Profile-Specific Configuration

### Development (application-dev.yml)

```yaml
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
    single-table:
      batch-insert-size: 100  # Small batches for dev
      pool-size: 5
```

### Staging (application-staging.yml)

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      batch-insert-size: 500
      pool-size: 10
```

### Production (application-prod.yml)

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      batch-insert-size: 1000
      pool-size: 50
      auto-create-tables: false  # Create manually
```

---

## 🔄 Strategy Selection Examples

### Example 1: Startup (MVP)

```yaml
# Minimal config - single table, simple setup
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
```

### Example 2: Growing Company

```yaml
# Starting to scale - still single table, optimize
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
    single-table:
      batch-insert-size: 1000
      pool-size: 20
      create-indices: true
```

### Example 3: Enterprise

```yaml
# Large scale - separate tables per type
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      entity-types: [product, user, order, document, ai_profile, behavior]
      batch-insert-size: 2000
      pool-size: 50
      auto-create-tables: false
```

### Example 4: Multi-Tenant SaaS

```yaml
# Custom strategy for tenant isolation
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom:
      strategy-class: "com.mycompany.TenantIsolatedStorageStrategy"
      custom-properties:
        tenant-aware: true
        partition-by-tenant: true
```

---

## ✅ Validation Checklist

Before deploying, verify:

- [ ] Strategy is set correctly
- [ ] Entity types match your domain
- [ ] Connection pool size appropriate
- [ ] Batch insert size optimized
- [ ] Indices created (if applicable)
- [ ] Health checks pass
- [ ] Performance benchmarks acceptable

---

## 🔍 Monitoring Strategy Health

```java
// In your monitoring/health endpoint
@GetMapping("/health/storage")
public ResponseEntity<Map<String, Object>> getStorageHealth(
    @Autowired AISearchableService service) {
    
    return ResponseEntity.ok(Map.of(
        "strategy_info", service.getStorageStrategyInfo(),
        "timestamp", LocalDateTime.now()
    ));
}
```

---

## 🚀 Switching Strategies

To switch strategies **without downtime**:

1. **Set new strategy in configuration**
2. **Run data migration (if needed)**
3. **Deploy with new config**
4. **Monitor health checks**
5. **Rollback capability ready**

See STRATEGY_MIGRATION_GUIDE.md for detailed migration steps.

---

This configuration approach ensures flexibility while maintaining simplicity!


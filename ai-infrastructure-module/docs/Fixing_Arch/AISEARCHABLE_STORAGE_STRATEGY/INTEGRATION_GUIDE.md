# 🔌 Integration Guide: Adding Auto-Table Creation to Codebase

**How to integrate the AUTO_TABLE_CREATION solution into the actual Java codebase.**

---

## 📍 File Locations

Where to place each component:

```
ai-infrastructure-module/
├── ai-infrastructure-core/
│   ├── src/main/java/com/ai/infrastructure/
│   │   ├── storage/
│   │   │   ├── strategy/
│   │   │   │   ├── AISearchableEntityStorageStrategy.java (EXISTS)
│   │   │   │   ├── impl/
│   │   │   │   │   ├── SingleTableStorageStrategy.java (EXISTS)
│   │   │   │   │   ├── PerTypeTableStorageStrategy.java (UPDATE)
│   │   │   │   │   ├── PerTypeRepositoryFactory.java (NEW)
│   │   │   │   │   └── DynamicPerTypeRepository.java (NEW)
│   │   │   ├── auto/
│   │   │   │   └── PerTypeTableAutoCreationService.java (NEW)
│   │   │   └── PerTypeRepository.java (NEW INTERFACE)
│   │   └── config/
│   │       └── AIEntityConfigurationService.java (EXISTING)
│   └── resources/
│       └── application.yml (UPDATE)
```

---

## 🚀 Integration Steps

### Step 1: Create Storage Auto Package

```bash
# Create new package for auto-creation
mkdir -p ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/auto
```

### Step 2: Create PerTypeRepository Interface

**File**: `storage/PerTypeRepository.java`

```java
package com.ai.infrastructure.storage;

import com.ai.infrastructure.entity.AISearchableEntity;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for per-type table operations
 */
public interface PerTypeRepository {
    Optional<AISearchableEntity> findByEntityId(String entityId);
    Optional<AISearchableEntity> findByVectorId(String vectorId);
    <S extends AISearchableEntity> S save(S entity);
    void delete(AISearchableEntity entity);
    List<AISearchableEntity> findAll();
    long count();
}
```

### Step 3: Copy Auto-Creation Service

**File**: `storage/auto/PerTypeTableAutoCreationService.java`

Copy the complete implementation from `AUTO_TABLE_CREATION.md` section "Implementation: Auto-Table Creation Service"

Key points:
- Uses `@EventListener(ApplicationReadyEvent.class)`
- Reads from `AIEntityConfigurationService`
- Uses `DataSource` to create tables
- Has proper error handling and logging

### Step 4: Copy Repository Factory

**File**: `storage/strategy/impl/PerTypeRepositoryFactory.java`

Copy the complete implementation from `AUTO_TABLE_CREATION.md` section "Per-Type Repository Factory"

Key points:
- Creates repositories dynamically
- Caches repositories
- Validates entity types are configured
- Returns all repositories when needed

### Step 5: Copy Dynamic Repository

**File**: `storage/strategy/impl/DynamicPerTypeRepository.java`

Copy the complete implementation from `AUTO_TABLE_CREATION.md` section "Dynamic Per-Type Repository Implementation"

Key points:
- Implements `PerTypeRepository` interface
- Works with dynamic table names
- Delegates to `JpaRepository`

### Step 6: Update PerTypeTableStorageStrategy

**File**: `storage/strategy/impl/PerTypeTableStorageStrategy.java`

Replace with updated version from `AUTO_TABLE_CREATION.md` section "Updated Per-Type Storage Strategy"

Key changes:
- Inject `PerTypeRepositoryFactory` instead of hardcoded repositories
- Inject `PerTypeTableAutoCreationService` (autowired but triggered by event)
- Use factory to get repositories per entity type
- All methods use `repositoryFactory.getRepositoryForType()`

### Step 7: Update application.yml

Add configuration section:

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    
    per-type-tables:
      enabled: true
      auto-create-tables: true  # ← ENABLE AUTO-CREATION
      table-prefix: "ai_searchable_"
      
      database:
        engine: "InnoDB"
        charset: "utf8mb4"
        collation: "utf8mb4_unicode_ci"
      
      auto-create-indices: true
      indices:
        - name: "ft_searchable_content"
          type: "FULLTEXT"
          columns: ["searchable_content"]
        - name: "idx_vector_id"
          type: "INDEX"
          columns: ["vector_id"]
        - name: "idx_created_at"
          type: "INDEX"
          columns: ["created_at"]
```

### Step 8: Update Spring Auto-Configuration

**File**: `config/AISearchableStorageStrategyAutoConfiguration.java`

Add beans:

```java
@Configuration
@EnableConfigurationProperties(AIStorageStrategyProperties.class)
public class AISearchableStorageStrategyAutoConfiguration {
    
    @Bean
    public PerTypeTableAutoCreationService perTypeTableAutoCreationService(
            AIEntityConfigurationService configService,
            DataSource dataSource) {
        return new PerTypeTableAutoCreationService(configService, dataSource);
    }
    
    @Bean
    public PerTypeRepositoryFactory perTypeRepositoryFactory(
            AIEntityConfigurationService configService) {
        return new PerTypeRepositoryFactory(configService);
    }
    
    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "PER_TYPE_TABLE"
    )
    public AISearchableEntityStorageStrategy perTypeTableStorageStrategy(
            PerTypeRepositoryFactory repositoryFactory,
            PerTypeTableAutoCreationService autoCreationService) {
        return new PerTypeTableStorageStrategy(repositoryFactory, autoCreationService);
    }
}
```

---

## 🔄 Integration Checklist

- [ ] Create `storage/auto/` package
- [ ] Create `PerTypeRepository.java` interface
- [ ] Copy `PerTypeTableAutoCreationService.java`
- [ ] Copy `PerTypeRepositoryFactory.java`
- [ ] Copy `DynamicPerTypeRepository.java`
- [ ] Update `PerTypeTableStorageStrategy.java`
- [ ] Add dependencies to `pom.xml` (if needed)
- [ ] Update `application.yml`
- [ ] Update Auto-Configuration class
- [ ] Test auto-table creation
- [ ] Run integration tests
- [ ] Verify logs show tables created
- [ ] Check database for created tables

---

## 🧪 Testing Auto-Table Creation

### Integration Test

```java
@SpringBootTest
@ActiveProfiles("test")
public class PerTypeTableAutoCreationIntegrationTest {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private AIEntityConfigurationService configService;
    
    @Test
    public void testTablesAreCreatedAtStartup() throws SQLException {
        // Get all configured entity types
        List<AIEntityConfig> configs = configService.getAllEntityConfigs();
        
        // Verify each table exists
        for (AIEntityConfig config : configs) {
            String tableName = "ai_searchable_" + config.getEntityType();
            assertTrue(tableExists(tableName), "Table should exist: " + tableName);
        }
    }
    
    @Test
    public void testIndicesAreCreated() throws SQLException {
        // Verify indices exist for a specific table
        String tableName = "ai_searchable_product";
        
        DatabaseMetaData metadata = dataSource.getConnection().getMetaData();
        ResultSet indices = metadata.getIndexInfo(null, null, tableName, false);
        
        assertTrue(indices.next(), "Indices should exist for table");
    }
    
    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(null, null, tableName, null)) {
                return tables.next();
            }
        }
    }
}
```

### Manual Testing

```bash
# 1. Start application
java -jar app.jar

# 2. Check logs for
# "Starting auto-creation of per-type tables"
# "Successfully created table: ai_searchable_*"

# 3. Connect to database and verify
mysql> SHOW TABLES LIKE 'ai_searchable_%';

# 4. Verify schema
mysql> DESC ai_searchable_product;
```

---

## 🔍 Verification

After integration, verify:

1. **Application Logs**
   ```
   Starting auto-creation of per-type tables for AISearchableEntity
   Successfully created table: ai_searchable_product
   Successfully created table: ai_searchable_user
   Auto-creation of per-type tables completed successfully
   ```

2. **Database Tables**
   ```sql
   SHOW TABLES;
   -- Should show: ai_searchable_product, ai_searchable_user, etc.
   ```

3. **Indices Created**
   ```sql
   SHOW INDEX FROM ai_searchable_product;
   -- Should show: ft_searchable_content, idx_vector_id, idx_created_at
   ```

4. **Health Check**
   ```bash
   curl http://localhost:8080/health
   # Should show storage strategy is healthy
   ```

---

## 📝 Configuration Hierarchy

Users can configure auto-creation at:

1. **application.yml** (default)
   ```yaml
   ai-infrastructure:
     storage:
       per-type-tables:
         auto-create-tables: true
   ```

2. **Environment Variable** (override)
   ```bash
   export AI_INFRASTRUCTURE_STORAGE_PER_TYPE_TABLES_AUTO_CREATE_TABLES=true
   ```

3. **Command Line** (override)
   ```bash
   java -jar app.jar --ai-infrastructure.storage.per-type-tables.auto-create-tables=true
   ```

---

## 🚀 Deployment

### Development
```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      auto-create-tables: true
```

### Staging/Production
```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      auto-create-tables: true  # or false if tables pre-created
```

---

## ⚠️ Important Notes

1. **First Run**: Tables are created at startup, first deployment may take 5-10 seconds longer
2. **Idempotent**: Safe to run multiple times - existing tables are skipped
3. **Error Handling**: Startup fails if tables cannot be created (check database permissions)
4. **Permissions**: Database user must have CREATE TABLE permission
5. **Indices**: Created automatically, can be customized in service

---

## 🆘 Troubleshooting

### Tables Not Created

**Problem**: Startup completes but no tables in database

**Solutions**:
1. Check logs for errors
2. Verify database connection
3. Verify user has CREATE TABLE permission
4. Check `auto-create-tables: true` in configuration

### Permission Denied Error

**Problem**: `Access denied for user ... to database ...`

**Solution**: Grant CREATE permission to database user:
```sql
GRANT CREATE ON database_name.* TO 'user'@'%';
```

### Table Already Exists Error

**Problem**: `Table already exists` error

**Solution**: This is normal if running twice - tables are skipped if they exist

---

**Integration complete! Tables now auto-create at startup! 🎉**



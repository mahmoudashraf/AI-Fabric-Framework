# Auto-Table Creation for Per-Type Storage Strategy

## 🎯 Overview

For the **Per-Type Table Strategy**, tables should be **automatically created** based on entity types defined in `ai-entity-config.yml`. Users should never manually create tables.

---

## 🏗️ Architecture

```
ai-entity-config.yml
    ↓
AIEntityConfigurationLoader (reads YAML)
    ↓
PerTypeTableAutoCreationService (creates tables)
    ↓
Database (tables auto-created with proper schema)
    ↓
PerTypeTableStorageStrategy (uses auto-created tables)
```

---

## 📋 Entity Configuration Example

```yaml
# ai-entity-config.yml
ai-entities:
  product:
    features: ["embedding", "search"]
    enable-search: true
    indexable: true
    
  user:
    features: ["embedding", "search"]
    enable-search: true
    indexable: true
    
  order:
    features: ["embedding", "search"]
    enable-search: true
    indexable: true
    
  document:
    features: ["embedding", "search"]
    enable-search: true
    indexable: true
```

---

## 💻 Implementation: Auto-Table Creation Service

```java
package com.ai.infrastructure.storage.strategy.auto;

import com.ai.infrastructure.config.AIEntityConfigurationService;
import com.ai.infrastructure.dto.AIEntityConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Automatically creates per-type tables based on ai-entity-config.yml
 * 
 * Tables are created once at application startup if they don't exist.
 * Schema includes all necessary columns for AISearchableEntity storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerTypeTableAutoCreationService {
    
    private final AIEntityConfigurationService configService;
    private final DataSource dataSource;
    
    /**
     * Create tables for all configured entity types at application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createTablesForConfiguredEntities() {
        log.info("Starting auto-creation of per-type tables for AISearchableEntity");
        
        List<AIEntityConfig> configs = configService.getAllEntityConfigs();
        
        for (AIEntityConfig config : configs) {
            createTableForEntityType(config.getEntityType());
        }
        
        log.info("Auto-creation of per-type tables completed successfully");
    }
    
    /**
     * Create table for a specific entity type if it doesn't exist
     */
    private void createTableForEntityType(String entityType) {
        String tableName = getTableNameForType(entityType);
        
        if (tableExists(tableName)) {
            log.debug("Table {} already exists, skipping creation", tableName);
            return;
        }
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String createTableSQL = generateCreateTableSQL(tableName);
            statement.execute(createTableSQL);
            
            log.info("Successfully created table: {}", tableName);
            
            // Create indices for the table
            createIndicesForTable(connection, tableName);
            
        } catch (SQLException e) {
            log.error("Failed to create table {}: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("Failed to create table for entity type: " + entityType, e);
        }
    }
    
    /**
     * Generate CREATE TABLE SQL for a specific entity type
     */
    private String generateCreateTableSQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL,
                searchable_content LONGTEXT NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata LONGTEXT,
                ai_analysis LONGTEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                
                UNIQUE KEY uk_entity_id (entity_id),
                INDEX idx_vector_id (vector_id),
                INDEX idx_vector_updated_at (vector_updated_at),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tableName);
    }
    
    /**
     * Create indices for the table to optimize queries
     */
    private void createIndicesForTable(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Full-text search index for searchable_content
            String fullTextIndexSQL = String.format(
                "ALTER TABLE %s ADD FULLTEXT INDEX ft_searchable_content (searchable_content)",
                tableName
            );
            try {
                statement.execute(fullTextIndexSQL);
                log.debug("Created full-text index on {}", tableName);
            } catch (SQLException e) {
                log.warn("Could not create full-text index (may already exist): {}", e.getMessage());
            }
            
            // Metadata JSON index (if using MySQL 5.7+)
            String jsonIndexSQL = String.format(
                "ALTER TABLE %s ADD INDEX idx_metadata (metadata(255))",
                tableName
            );
            try {
                statement.execute(jsonIndexSQL);
                log.debug("Created metadata index on {}", tableName);
            } catch (SQLException e) {
                log.debug("Could not create metadata index: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Check if table exists in the database
     */
    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (var tables = metadata.getTables(null, null, tableName, null)) {
                return tables.next();
            }
        } catch (SQLException e) {
            log.error("Error checking if table exists: {}", tableName, e);
            return false;
        }
    }
    
    /**
     * Get table name for entity type
     */
    private String getTableNameForType(String entityType) {
        return "ai_searchable_" + entityType.toLowerCase();
    }
}
```

---

## 🔌 Per-Type Repository Factory

```java
package com.ai.infrastructure.storage.strategy.impl;

import com.ai.infrastructure.config.AIEntityConfigurationService;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.entity.AISearchableEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.support.RepositoryFactoryBean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates and manages per-type repositories dynamically based on configured entity types
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerTypeRepositoryFactory {
    
    private final AIEntityConfigurationService configService;
    private final Map<String, PerTypeRepository> repositoryCache = new HashMap<>();
    
    /**
     * Get or create repository for entity type
     */
    public PerTypeRepository getRepositoryForType(String entityType) {
        if (repositoryCache.containsKey(entityType)) {
            return repositoryCache.get(entityType);
        }
        
        // Validate entity type is configured
        List<AIEntityConfig> configs = configService.getAllEntityConfigs();
        boolean isConfigured = configs.stream()
            .anyMatch(c -> c.getEntityType().equals(entityType));
        
        if (!isConfigured) {
            throw new IllegalArgumentException(
                "Entity type not configured: " + entityType);
        }
        
        // Create repository dynamically
        PerTypeRepository repository = createRepository(entityType);
        repositoryCache.put(entityType, repository);
        
        log.debug("Created repository for entity type: {}", entityType);
        return repository;
    }
    
    /**
     * Get all repositories for configured entity types
     */
    public Map<String, PerTypeRepository> getAllRepositories() {
        List<AIEntityConfig> configs = configService.getAllEntityConfigs();
        Map<String, PerTypeRepository> allRepos = new HashMap<>();
        
        for (AIEntityConfig config : configs) {
            allRepos.put(config.getEntityType(), getRepositoryForType(config.getEntityType()));
        }
        
        return allRepos;
    }
    
    /**
     * Create repository for specific entity type
     */
    private PerTypeRepository createRepository(String entityType) {
        String tableName = "ai_searchable_" + entityType.toLowerCase();
        return new DynamicPerTypeRepository(tableName);
    }
}
```

---

## 🗄️ Dynamic Per-Type Repository Implementation

```java
package com.ai.infrastructure.storage.strategy.impl;

import com.ai.infrastructure.entity.AISearchableEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Dynamic implementation of per-type repository
 * Uses @Query to dynamically reference the correct table for each entity type
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicPerTypeRepository implements PerTypeRepository {
    
    private final String tableName;
    private final JpaRepository<AISearchableEntity, String> baseRepository;
    
    @Override
    public Optional<AISearchableEntity> findByEntityId(String entityId) {
        return baseRepository.findById(entityId);
    }
    
    @Override
    public Optional<AISearchableEntity> findByVectorId(String vectorId) {
        return baseRepository.findById(vectorId);
    }
    
    @Override
    public <S extends AISearchableEntity> S save(S entity) {
        return baseRepository.save(entity);
    }
    
    @Override
    public void delete(AISearchableEntity entity) {
        baseRepository.delete(entity);
    }
    
    @Override
    public List<AISearchableEntity> findAll() {
        return baseRepository.findAll();
    }
    
    @Override
    public long count() {
        return baseRepository.count();
    }
}
```

---

## ⚙️ Configuration for Auto-Creation

```yaml
# application.yml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    
    # Auto-table creation settings
    per-type-tables:
      enabled: true
      auto-create-tables: true  # ← Enable auto-creation
      table-prefix: "ai_searchable_"
      
      # Entity types are read from ai-entity-config.yml
      # No need to list them here!
      
      # Database settings
      database:
        engine: "InnoDB"
        charset: "utf8mb4"
        collation: "utf8mb4_unicode_ci"
        
      # Auto-indexing
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

---

## 🎯 Updated Per-Type Storage Strategy

```java
package com.ai.infrastructure.storage.strategy.impl;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-Type Table Storage Strategy with Auto-Table Creation
 * 
 * Features:
 * - Reads entity types from ai-entity-config.yml
 * - Automatically creates tables at startup
 * - User never manually creates tables
 * - All configuration is YAML-driven
 */
@Component("perTypeTableStrategy")
@RequiredArgsConstructor
@Slf4j
public class PerTypeTableStorageStrategy implements AISearchableEntityStorageStrategy {
    
    private final PerTypeRepositoryFactory repositoryFactory;
    private final PerTypeTableAutoCreationService autoCreationService;
    
    @Override
    public void save(AISearchableEntity entity) {
        log.debug("Saving entity {} of type {} to auto-created type-specific table", 
            entity.getEntityId(), entity.getEntityType());
        
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(entity.getEntityType());
        repo.save(entity);
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(entityType);
        return repo.findByEntityId(entityId);
    }
    
    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(entityType);
        return repo.findAll();
    }
    
    @Override
    public Optional<AISearchableEntity> findByVectorId(String vectorId) {
        // Search across all type repositories
        Map<String, PerTypeRepository> allRepos = repositoryFactory.getAllRepositories();
        for (PerTypeRepository repo : allRepos.values()) {
            Optional<AISearchableEntity> result = repo.findByVectorId(vectorId);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
    
    @Override
    public void delete(AISearchableEntity entity) {
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(entity.getEntityType());
        repo.delete(entity);
    }
    
    @Override
    public void update(AISearchableEntity entity) {
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(entity.getEntityType());
        repo.save(entity);
    }
    
    @Override
    public boolean isHealthy() {
        try {
            Map<String, PerTypeRepository> allRepos = repositoryFactory.getAllRepositories();
            for (PerTypeRepository repo : allRepos.values()) {
                repo.count();
            }
            log.debug("Per-type table storage strategy health check passed");
            return true;
        } catch (Exception e) {
            log.error("Per-type table strategy health check failed", e);
            return false;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "PER_TYPE_TABLE_AUTO_CREATE";
    }
    
    @Override
    public List<String> getSupportedEntityTypes() {
        return List.copyOf(repositoryFactory.getAllRepositories().keySet());
    }
}
```

---

## ✅ User Experience

### Before (Manual Table Creation)
```sql
-- User has to manually create tables
CREATE TABLE ai_searchable_product (id VARCHAR, ...);
CREATE TABLE ai_searchable_user (id VARCHAR, ...);
CREATE TABLE ai_searchable_order (id VARCHAR, ...);
-- ... repeat for each entity type
```

### After (Auto-Creation via YAML)
```yaml
# User just defines entity types in ai-entity-config.yml
ai-entities:
  product:
    features: ["embedding", "search"]
  user:
    features: ["embedding", "search"]
  order:
    features: ["embedding", "search"]

# Tables are automatically created at startup!
# User doesn't do anything else
```

---

## 🚀 What Happens at Application Startup

1. **Application starts** → Spring initializes
2. **AIEntityConfigurationLoader** reads `ai-entity-config.yml`
3. **PerTypeTableAutoCreationService** kicks in (ApplicationReadyEvent)
4. **For each entity type:**
   - Check if table exists
   - If not: Create table with proper schema
   - Create indices (FULLTEXT, regular indices)
   - Log success
5. **PerTypeTableStorageStrategy** ready to use
6. **User saves entities** → Automatically goes to correct table

---

## 📊 Automatic Table Schema

Each auto-created table includes:

```
ai_searchable_product
├── id (VARCHAR 36, UUID, PRIMARY KEY)
├── entity_type (VARCHAR 50, "product")
├── entity_id (VARCHAR 255, UNIQUE)
├── searchable_content (LONGTEXT)
├── vector_id (VARCHAR 255, INDEXED)
├── vector_updated_at (TIMESTAMP)
├── metadata (LONGTEXT, JSON)
├── ai_analysis (LONGTEXT, JSON)
├── created_at (TIMESTAMP)
├── updated_at (TIMESTAMP)
└── Indices:
    ├── idx_entity_id (UNIQUE)
    ├── idx_vector_id
    ├── idx_vector_updated_at
    ├── idx_created_at
    ├── ft_searchable_content (FULLTEXT)
    └── idx_metadata
```

---

## 💡 Zero Manual Work Required

Users only need:

```yaml
# In application.yml - set strategy
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE

# In ai-entity-config.yml - define entities (already doing this!)
ai-entities:
  product:
    ...
  user:
    ...
```

**That's it!** Tables are auto-created, indices are auto-created, everything works!

---

**Users never worry about tables. Everything is automatic, driven by ai-entity-config.yml!** 🎉



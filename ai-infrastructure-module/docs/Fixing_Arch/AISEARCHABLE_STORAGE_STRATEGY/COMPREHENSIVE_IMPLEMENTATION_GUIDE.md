# 🚀 Comprehensive AISearchableEntity Storage Strategy Implementation Guide

**Clean Architecture: Three Clear Paths - No Confusion**

---

## 📋 Table of Contents

1. [Quick Overview](#quick-overview)
2. [The Three Paths](#the-three-paths)
3. [Complete Code Implementation](#complete-code-implementation)
4. [Configuration Setup](#configuration-setup)
5. [Integration Steps](#integration-steps)
6. [Testing & Verification](#testing--verification)
7. [Troubleshooting](#troubleshooting)
8. [Production Deployment](#production-deployment)

---

## Quick Overview

### 🎯 What This Solution Provides

```
Problem:
  ❌ How to handle table creation for different needs?
  ❌ Should library support all databases or let users choose?
  ❌ How to avoid schema/type mismatch issues?

Solution:
  ✅ Three clear paths - user chooses ONE:
  
     Path 1: SINGLE_TABLE
             Library auto-creates one table
             Simple, fast setup
     
     Path 2: PER_TYPE_TABLE
             Library auto-creates tables per entity type
             Enterprise-ready, scalable
     
     Path 3: CUSTOM
             User implements storage strategy
             Full control, zero type mismatch risk
```

---

## The Three Paths

### 🎯 Decision Tree

```
                   User Strategy Choice
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
    
SINGLE_TABLE        PER_TYPE_TABLE          CUSTOM
┌─────────────────┐ ┌──────────────────┐  ┌──────────────┐
│ One big table   │ │ One per type     │  │ Your choice  │
│ for all types   │ │ from config      │  │              │
│                 │ │                  │  │ Your impl.   │
│ Library         │ │ Library          │  │ Your tables  │
│ auto-creates    │ │ auto-creates all │  │ Your schema  │
│                 │ │                  │  │              │
│ Best for:       │ │ Best for:        │  │ Best for:    │
│ - MVP           │ │ - Enterprise     │  │ - Special    │
│ - < 10M         │ │ - 10M - 1B       │  │ - Custom DB  │
│ - Simple        │ │ - Scalable       │  │ - Full ctrl  │
└─────────────────┘ └──────────────────┘  └──────────────┘
     Config:             Config:             Config:
   Set strategy:       Set strategy:       Set strategy:
   SINGLE_TABLE        PER_TYPE_TABLE      CUSTOM
   Done!               Done!               + Implement
                                          + Create tables
```

---

### Path 1: SINGLE_TABLE (Library Manages)

**For users who want:** Simplicity, quick setup

```yaml
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
```

**What happens:**
1. Application starts
2. Library detects database type from connection (case-insensitive)
3. Library generates database-specific SQL
4. Creates `ai_searchable_entities` table
5. Creates indices
6. System ready!

**Library responsibility:** 
- ✅ Detect database type
- ✅ Generate SQL for that database
- ✅ Create table + indices
- ✅ Schema validation

**User responsibility:**
- ✅ Set `strategy: SINGLE_TABLE`
- ✅ Configure database connection
- ✅ Done!

**Supported databases:**
- ✅ MySQL, MariaDB, Percona
- ✅ PostgreSQL, EnterpriseDB
- ✅ SQL Server, Azure SQL
- ✅ Oracle
- ✅ H2, SQLite
- ✅ DB2, Derby, Sybase

**Best for:**
- MVP / Startups
- < 10M records
- Simple use cases

---

### Path 2: PER_TYPE_TABLE (Library Manages + Scales)

**For users who want:** Enterprise scalability, auto-scaling tables

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
```

**What happens:**
1. Application starts
2. Library reads `ai-entity-config.yml` (your entity types)
3. Library detects database type from connection
4. Library generates database-specific SQL
5. Creates table for each type: `ai_searchable_product`, `ai_searchable_user`, etc.
6. Creates indices per table
7. System ready and scalable!

**Library responsibility:** 
- ✅ Detect database type
- ✅ Read entity types from config
- ✅ Generate SQL for that database
- ✅ Create all tables + indices
- ✅ Schema validation

**User responsibility:**
- ✅ Set `strategy: PER_TYPE_TABLE`
- ✅ Configure database connection
- ✅ Define entity types in `ai-entity-config.yml` (probably already done!)
- ✅ Done!

**Supported databases:**
- ✅ MySQL, MariaDB, Percona
- ✅ PostgreSQL, EnterpriseDB
- ✅ SQL Server, Azure SQL
- ✅ Oracle
- ✅ H2, SQLite
- ✅ DB2, Derby, Sybase

**Best for:**
- Enterprise applications
- 10M - 1B+ records
- Scalable designs
- Multi-tenant systems

---

### Path 3: CUSTOM (User Manages)

**For users who want:** Full control, special storage needs

```yaml
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-strategy-class: "com.mycompany.MyStorageStrategy"
```

**User must:**
1. Implement `AISearchableEntityStorageStrategy` interface
2. Create tables however they want
3. Define schema as needed
4. Handle storage backend (Database, S3, DynamoDB, etc.)

**Example:**

```java
@Component
public class MyStorageStrategy implements AISearchableEntityStorageStrategy {
    
    @Override
    public void save(AISearchableEntity entity) {
        // Your custom logic here
        // Could be: Database, S3, DynamoDB, File system, etc.
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(...) {
        // Your custom logic
    }
    
    // ... implement other methods ...
    
    @Override
    public String getStrategyName() {
        return "MY_CUSTOM_STRATEGY";
    }
}
```

**Library responsibility:** 
- ✅ Define interface
- ✅ Call user's methods
- ✅ Nothing else

**User responsibility:**
- ✅ Implement all interface methods
- ✅ Create/manage tables
- ✅ Define schema
- ✅ Handle storage backend
- ✅ **NO type mismatch risk** (user owns everything!)

**Best for:**
- Special storage needs
- Custom databases
- Non-SQL storage (S3, DynamoDB, etc.)
- Complex requirements
- Multi-storage architectures

---

### 📊 Path Comparison

| Feature | SINGLE_TABLE | PER_TYPE_TABLE | CUSTOM |
|---------|-------------|----------------|--------|
| **Setup** | ✅ Simple | ✅ Simple | ❌ Complex |
| **Auto-create** | ✅ YES | ✅ YES | ❌ NO (User does) |
| **Database support** | ✅ All | ✅ All | ✅ Any |
| **Scalability** | ⭐ Good | ⭐⭐⭐ Great | ⭐ Depends |
| **Schema control** | ❌ Library | ❌ Library | ✅ User |
| **Type mismatch risk** | ⚠️ Low | ⚠️ Low | ✅ None |
| **Best for scale** | < 10M | 10M - 1B+ | Any |
| **Configuration** | 1 line | 1 line | Implement code |
| **Tables created** | Automatic | Automatic | User creates |
| **Library responsibility** | Table + schema | Tables + schema | Interface only |

---

## Complete Code Implementation

### 1️⃣ Storage Strategy Interface (Core)

**File**: `com/ai/infrastructure/storage/strategy/AISearchableEntityStorageStrategy.java`

```java
package com.ai.infrastructure.storage.strategy;

import com.ai.infrastructure.entity.AISearchableEntity;
import java.util.List;
import java.util.Optional;

/**
 * Storage strategy interface for AISearchableEntity.
 * 
 * Users choose one of three implementations:
 * 1. SINGLE_TABLE - Library auto-creates one table
 * 2. PER_TYPE_TABLE - Library auto-creates tables per type
 * 3. CUSTOM - User implements this interface
 */
public interface AISearchableEntityStorageStrategy {
    
    /**
     * Save an AISearchableEntity
     */
    void save(AISearchableEntity entity);
    
    /**
     * Find entity by type and ID
     */
    Optional<AISearchableEntity> findByEntityTypeAndEntityId(
        String entityType, String entityId);
    
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
     * Update entity
     */
    void update(AISearchableEntity entity);
    
    /**
     * Check if strategy is healthy
     */
    boolean isHealthy();
    
    /**
     * Get strategy name (for logging)
     */
    String getStrategyName();
    
    /**
     * Get supported entity types
     */
    List<String> getSupportedEntityTypes();
}
```

---

### 2️⃣ Single Table Strategy (AUTO-CREATE)

**File**: `com/ai/infrastructure/storage/strategy/impl/SingleTableStorageStrategy.java`

```java
package com.ai.infrastructure.storage.strategy.impl;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Single-table storage strategy with auto-table creation.
 * 
 * ✅ Path 1: Simple, one table for all entities
 * ✅ Library auto-detects database and creates table
 * ✅ Best for MVP and < 10M records
 */
@Component("singleTableStrategy")
@RequiredArgsConstructor
@Slf4j
public class SingleTableStorageStrategy implements AISearchableEntityStorageStrategy {
    
    private final AISearchableEntityRepository repository;
    
    @Override
    public void save(AISearchableEntity entity) {
        log.debug("Saving entity {} to single table", entity.getEntityId());
        repository.save(entity);
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(
        String entityType, String entityId) {
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
    public void update(AISearchableEntity entity) {
        repository.save(entity);
    }
    
    @Override
    public boolean isHealthy() {
        try {
            repository.count();
            return true;
        } catch (Exception e) {
            log.error("Health check failed", e);
            return false;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "SINGLE_TABLE";
    }
    
    @Override
    public List<String> getSupportedEntityTypes() {
        return List.of("*");  // Supports all types
    }
}
```

---

### 3️⃣ Per-Type Table Strategy (AUTO-CREATE)

**File**: `com/ai/infrastructure/storage/strategy/impl/PerTypeTableStorageStrategy.java`

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
 * Per-type table storage strategy with auto-table creation.
 * 
 * ✅ Path 2: Enterprise scalable, one table per entity type
 * ✅ Library auto-creates all tables from entity config
 * ✅ Best for enterprise and 10M - 1B+ records
 */
@Component("perTypeTableStrategy")
@RequiredArgsConstructor
@Slf4j
public class PerTypeTableStorageStrategy implements AISearchableEntityStorageStrategy {
    
    private final PerTypeRepositoryFactory repositoryFactory;
    
    @Override
    public void save(AISearchableEntity entity) {
        log.debug("Saving entity {} to type-specific table: {}", 
            entity.getEntityId(), entity.getEntityType());
        
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(
            entity.getEntityType());
        repo.save(entity);
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(
        String entityType, String entityId) {
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
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(
            entity.getEntityType());
        repo.delete(entity);
    }
    
    @Override
    public void update(AISearchableEntity entity) {
        PerTypeRepository repo = repositoryFactory.getRepositoryForType(
            entity.getEntityType());
        repo.save(entity);
    }
    
    @Override
    public boolean isHealthy() {
        try {
            Map<String, PerTypeRepository> allRepos = 
                repositoryFactory.getAllRepositories();
            for (PerTypeRepository repo : allRepos.values()) {
                repo.count();
            }
            return true;
        } catch (Exception e) {
            log.error("Health check failed", e);
            return false;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "PER_TYPE_TABLE";
    }
    
    @Override
    public List<String> getSupportedEntityTypes() {
        return List.copyOf(repositoryFactory.getAllRepositories().keySet());
    }
}
```

---

### 4️⃣ Table Auto-Creation Service (THE MAGIC!)

**File**: `com/ai/infrastructure/storage/auto/TableAutoCreationService.java`

```java
package com.ai.infrastructure.storage.auto;

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
 * ✨ AUTO-TABLE CREATION SERVICE
 * 
 * Automatically creates tables at startup based on database type.
 * Uses case-insensitive matching to handle driver variations.
 * 
 * Supports:
 * - SINGLE_TABLE: Creates one table
 * - PER_TYPE_TABLE: Creates one table per entity type
 * 
 * Supported databases (with variations):
 * 
 * MySQL Family:
 * - MySQL 5.7, 8.0+
 * - MariaDB 10.0+
 * - Percona Server
 * 
 * PostgreSQL Family:
 * - PostgreSQL 9.6+
 * - EnterpriseDB
 * 
 * SQL Server Family:
 * - Microsoft SQL Server 2016+
 * - Azure SQL Database
 * - Azure SQL Edge
 * 
 * Oracle Family:
 * - Oracle Database 11g+
 * 
 * Other Databases:
 * - H2 (in-memory and file-based)
 * - SQLite 3+
 * - IBM DB2
 * - Apache Derby
 * - Sybase Adaptive Server
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TableAutoCreationService {
    
    private final DataSource dataSource;
    private final AIEntityConfigurationService entityConfigService;
    
    /**
     * Detect strategy from configuration
     */
    private final StorageStrategyProvider strategyProvider;
    
    /**
     * Create tables at startup based on chosen strategy
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createTablesAtStartup() {
        String strategy = strategyProvider.getStrategy();
        log.info("🚀 Storage strategy: {}", strategy);
        
        try {
            String dbType = detectDatabaseType();
            log.info("🗄️ Database detected: {}", dbType);
            
            switch (strategy) {
                case "SINGLE_TABLE":
                    createSingleTable(dbType);
                    break;
                case "PER_TYPE_TABLE":
                    createPerTypeTables(dbType);
                    break;
                case "CUSTOM":
                    log.info("⚙️ Using CUSTOM strategy - user implements tables");
                    break;
                default:
                    log.warn("⚠️ Unknown strategy: {}", strategy);
            }
            
            log.info("✅ Table setup completed");
        } catch (Exception e) {
            log.error("❌ Failed to create tables", e);
            throw new RuntimeException("Auto-table creation failed", e);
        }
    }
    
    /**
     * Create single table for SINGLE_TABLE strategy
     */
    private void createSingleTable(String dbType) throws SQLException {
        String tableName = "ai_searchable_entities";
        
        if (tableExists(tableName)) {
            log.info("✓ Table {} already exists", tableName);
            return;
        }
        
        String sql = generateCreateTableSQL(dbType, tableName);
        executeSql(sql);
        log.info("✅ Created table: {}", tableName);
    }
    
    /**
     * Create per-type tables for PER_TYPE_TABLE strategy
     */
    private void createPerTypeTables(String dbType) throws SQLException {
        List<AIEntityConfig> configs = entityConfigService.getAllEntityConfigs();
        log.info("📊 Creating tables for {} entity types", configs.size());
        
        for (AIEntityConfig config : configs) {
            String tableName = "ai_searchable_" + config.getEntityType().toLowerCase();
            
            if (tableExists(tableName)) {
                log.debug("✓ Table {} already exists", tableName);
                continue;
            }
            
            String sql = generateCreateTableSQL(dbType, tableName);
            executeSql(sql);
            log.info("✅ Created table: {}", tableName);
        }
    }
    
    /**
     * Generate database-specific CREATE TABLE SQL
     * Case-insensitive matching for robustness
     */
    private String generateCreateTableSQL(String dbType, String tableName) {
        String type = dbType != null ? dbType.toUpperCase() : "UNKNOWN";
        
        switch (type) {
            case "MYSQL":
                return generateMySQLSQL(tableName);
            case "POSTGRESQL":
                return generatePostgresSQL(tableName);
            case "SQLSERVER":
                return generateSQLServerSQL(tableName);
            case "ORACLE":
                return generateOracleSQL(tableName);
            case "H2":
                return generateH2SQL(tableName);
            case "SQLITE":
                return generateSQLiteSQL(tableName);
            case "DB2":
                return generateDB2SQL(tableName);
            case "DERBY":
                return generateDerbySQL(tableName);
            case "SYBASE":
                return generateSybaseSQL(tableName);
            default:
                throw new UnsupportedOperationException(
                    "Auto-create not supported for: " + dbType + 
                    "\n\nSupported databases: MySQL, PostgreSQL, SQL Server, Oracle, H2, SQLite, DB2, Apache Derby, Sybase" +
                    "\n\nFor unsupported databases, use CUSTOM strategy:\n" +
                    "1. Set strategy: CUSTOM\n" +
                    "2. Implement AISearchableEntityStorageStrategy\n" +
                    "3. Create tables yourself with your database-specific schema");
        }
    }
    
    // ============ DATABASE-SPECIFIC SQL GENERATORS ============
    
    private String generateMySQLSQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content LONGTEXT NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata LONGTEXT,
                ai_analysis LONGTEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                
                INDEX idx_entity_type (entity_type),
                INDEX idx_vector_id (vector_id),
                INDEX idx_created_at (created_at),
                FULLTEXT INDEX ft_searchable_content (searchable_content)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tableName);
    }
    
    private String generatePostgresSQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content TEXT NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata TEXT,
                ai_analysis TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            CREATE INDEX ft_searchable_content ON %s USING GIN(to_tsvector('english', searchable_content));
            """, tableName, tableName, tableName, tableName, tableName);
    }
    
    private String generateSQLServerSQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content NVARCHAR(MAX) NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at DATETIME2 NULL,
                metadata NVARCHAR(MAX),
                ai_analysis NVARCHAR(MAX),
                created_at DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
                updated_at DATETIME2 NOT NULL DEFAULT GETUTCDATE()
            );
            
            CREATE NONCLUSTERED INDEX idx_entity_type ON %s(entity_type);
            CREATE NONCLUSTERED INDEX idx_vector_id ON %s(vector_id);
            CREATE NONCLUSTERED INDEX idx_created_at ON %s(created_at);
            """, tableName, tableName, tableName, tableName);
    }
    
    private String generateOracleSQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR2(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR2(50) NOT NULL,
                entity_id VARCHAR2(255) NOT NULL UNIQUE,
                searchable_content CLOB NOT NULL,
                vector_id VARCHAR2(255),
                vector_updated_at TIMESTAMP NULL,
                metadata CLOB,
                ai_analysis CLOB,
                created_at TIMESTAMP NOT NULL DEFAULT SYSDATE,
                updated_at TIMESTAMP NOT NULL DEFAULT SYSDATE
            );
            
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            """, tableName, tableName, tableName, tableName);
    }
    
    private String generateH2SQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content CLOB NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata CLOB,
                ai_analysis CLOB,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            """, tableName, tableName, tableName, tableName);
    }
    
    private String generateSQLiteSQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content TEXT NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata TEXT,
                ai_analysis TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            """, tableName, tableName, tableName, tableName);
    }
    
    private String generateDB2SQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content CLOB NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata CLOB,
                ai_analysis CLOB,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT TIMESTAMP
            );
            
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            """, tableName, tableName, tableName, tableName);
    }
    
    private String generateDerbySQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content CLOB NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata CLOB,
                ai_analysis CLOB,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT TIMESTAMP
            );
            
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            """, tableName, tableName, tableName, tableName);
    }
    
    private String generateSybaseSQL(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content TEXT NOT NULL,
                vector_id VARCHAR(255),
                vector_updated_at DATETIME NULL,
                metadata TEXT,
                ai_analysis TEXT,
                created_at DATETIME NOT NULL DEFAULT GETDATE(),
                updated_at DATETIME NOT NULL DEFAULT GETDATE()
            );
            
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            """, tableName, tableName, tableName, tableName);
    }
    
    // ============ UTILITY METHODS ============
    
    /**
     * Detect database type from connection metadata
     * Case-insensitive matching to handle driver variations
     */
    private String detectDatabaseType() {
        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            return normalizeDatabaseType(productName);
        } catch (SQLException e) {
            log.error("Could not detect database type", e);
            return "UNKNOWN";
        }
    }
    
    /**
     * Normalize database product name to internal type
     * Case-insensitive matching to handle driver variations
     */
    private String normalizeDatabaseType(String productName) {
        if (productName == null) {
            return "UNKNOWN";
        }
        
        String normalized = productName.toUpperCase();
        
        if (normalized.contains("MYSQL") || normalized.contains("MARIADB") || 
            normalized.contains("PERCONA")) {
            return "MYSQL";
        } else if (normalized.contains("POSTGRES") || normalized.contains("ENTERPRISEDB")) {
            return "POSTGRESQL";
        } else if (normalized.contains("SQL SERVER") || normalized.contains("MSSQL") ||
                   normalized.contains("MICROSOFT SQL") || normalized.contains("AZURE SQL")) {
            return "SQLSERVER";
        } else if (normalized.contains("ORACLE")) {
            return "ORACLE";
        } else if (normalized.contains("H2")) {
            return "H2";
        } else if (normalized.contains("SQLITE")) {
            return "SQLITE";
        } else if (normalized.contains("DB2") || normalized.contains("INFORMIX")) {
            return "DB2";
        } else if (normalized.contains("DERBY")) {
            return "DERBY";
        } else if (normalized.contains("SYBASE") || normalized.contains("ADAPTIVE SERVER")) {
            return "SYBASE";
        }
        
        return "UNKNOWN";
    }
    
    /**
     * Check if table already exists
     */
    private boolean tableExists(String tableName) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (var tables = meta.getTables(null, null, tableName, null)) {
                return tables.next();
            }
        }
    }
    
    /**
     * Execute SQL statement
     */
    private void executeSql(String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
```

---

### 5️⃣ AISearchableService (Uses Strategy)

**File**: `com/ai/infrastructure/service/AISearchableService.java`

```java
package com.ai.infrastructure.service;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service using pluggable storage strategy.
 * Works with SINGLE_TABLE, PER_TYPE_TABLE, or CUSTOM strategies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AISearchableService {
    
    private final AISearchableEntityStorageStrategy storageStrategy;
    private final ObjectMapper objectMapper;
    
    public void indexEntity(String entityType, String entityId, 
        String content, Map<String, Object> metadata) {
        
        log.debug("Indexing entity {} using strategy: {}",
            entityId, storageStrategy.getStrategyName());
        
        AISearchableEntity entity = AISearchableEntity.builder()
            .entityType(entityType)
            .entityId(entityId)
            .searchableContent(content)
            .metadata(convertToJson(metadata))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        
        storageStrategy.save(entity);
    }
    
    public Optional<AISearchableEntity> findEntity(String entityType, String entityId) {
        return storageStrategy.findByEntityTypeAndEntityId(entityType, entityId);
    }
    
    public List<AISearchableEntity> findByType(String entityType) {
        return storageStrategy.findByEntityType(entityType);
    }
    
    public Optional<AISearchableEntity> findByVectorId(String vectorId) {
        return storageStrategy.findByVectorId(vectorId);
    }
    
    public String getStorageStrategyInfo() {
        return String.format("Strategy: %s | Healthy: %b",
            storageStrategy.getStrategyName(),
            storageStrategy.isHealthy());
    }
    
    private String convertToJson(Map<String, Object> obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

---

### 6️⃣ Auto-Configuration

**File**: `com/ai/infrastructure/config/AISearchableStorageStrategyAutoConfiguration.java`

```java
package com.ai.infrastructure.config;

import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.storage.strategy.impl.SingleTableStorageStrategy;
import com.ai.infrastructure.storage.strategy.impl.PerTypeTableStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for storage strategies.
 * 
 * Users choose ONE strategy via configuration:
 * - strategy: SINGLE_TABLE (auto-creates one table)
 * - strategy: PER_TYPE_TABLE (auto-creates tables per type)
 * - strategy: CUSTOM (user provides implementation)
 */
@Configuration
@Slf4j
public class AISearchableStorageStrategyAutoConfiguration {
    
    /**
     * SINGLE_TABLE strategy
     */
    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "SINGLE_TABLE",
        matchIfMissing = true)
    public AISearchableEntityStorageStrategy singleTableStrategy(
        SingleTableStorageStrategy strategy) {
        log.info("📌 Using SINGLE_TABLE strategy - auto-creates one table");
        return strategy;
    }
    
    /**
     * PER_TYPE_TABLE strategy
     */
    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "PER_TYPE_TABLE")
    public AISearchableEntityStorageStrategy perTypeTableStrategy(
        PerTypeTableStorageStrategy strategy) {
        log.info("📌 Using PER_TYPE_TABLE strategy - auto-creates tables per entity type");
        return strategy;
    }
}
```

---

## Configuration Setup

### Path 1: SINGLE_TABLE (Simple & Automatic)

**File**: `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_db
    username: root
    password: password

ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE
    # That's it! Library handles everything
```

**What happens:**
- ✅ Application starts
- ✅ Library detects MySQL from connection
- ✅ Creates `ai_searchable_entities` table
- ✅ Creates indices
- ✅ Ready to use!

---

### Path 2: PER_TYPE_TABLE (Scalable & Automatic)

**File**: `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_db
    username: user
    password: password

ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    # Library reads entity types from ai-entity-config.yml
    # Creates one table per type
    # That's it! Scalable!
```

**What happens:**
- ✅ Application starts
- ✅ Library detects PostgreSQL from connection
- ✅ Reads entity types: product, user, order, etc.
- ✅ Creates tables: ai_searchable_product, ai_searchable_user, etc.
- ✅ Creates indices per table
- ✅ Ready to scale!

---

### Path 3: CUSTOM (Full User Control)

**User's Custom Implementation:**

```java
@Component
public class MyStorageStrategy implements AISearchableEntityStorageStrategy {
    
    // User implements all methods
    @Override
    public void save(AISearchableEntity entity) {
        // Your logic: Database, S3, DynamoDB, etc.
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(...) {
        // Your logic
    }
    
    // ... other methods ...
}
```

**Configuration:**

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:orcl
    username: user
    password: password

ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-strategy-class: "com.mycompany.MyStorageStrategy"
    # User MUST provide custom-strategy-class
    # User creates and manages tables
    # User has full control
```

---

## Step-by-Step Integration

### ✅ For SINGLE_TABLE

1. **Copy 3 files**
   - `AISearchableEntityStorageStrategy.java`
   - `SingleTableStorageStrategy.java`
   - `TableAutoCreationService.java`

2. **Update application.yml**
   ```yaml
   ai-infrastructure:
     storage:
       strategy: SINGLE_TABLE
   ```

3. **Start application**
   - Tables auto-created!

### ✅ For PER_TYPE_TABLE

1. **Copy 3 files**
   - `AISearchableEntityStorageStrategy.java`
   - `PerTypeTableStorageStrategy.java`
   - `TableAutoCreationService.java`

2. **Update application.yml**
   ```yaml
   ai-infrastructure:
     storage:
       strategy: PER_TYPE_TABLE
   ```

3. **Start application**
   - Tables per entity type auto-created!

### ✅ For CUSTOM

1. **Implement AISearchableEntityStorageStrategy**
   ```java
   @Component
   public class MyStrategy implements AISearchableEntityStorageStrategy {
       // Your implementation
   }
   ```

2. **Create tables yourself**
   - However you want
   - Any database
   - Any schema

3. **Configure**
   ```yaml
   ai-infrastructure:
     storage:
       strategy: CUSTOM
       custom-strategy-class: "com.mycompany.MyStrategy"
   ```

---

## Testing & Verification

### Verify SINGLE_TABLE or PER_TYPE_TABLE

```bash
# 1. Check logs at startup:
✅ "Storage strategy: SINGLE_TABLE"
✅ "Database detected: MYSQL"
✅ "Table setup completed"

# 2. Verify tables exist
mysql> SHOW TABLES LIKE 'ai_searchable%';

# 3. Verify schema
mysql> DESC ai_searchable_entities;

# 4. Test insert
mysql> INSERT INTO ai_searchable_entities VALUES (...);
```

### Verify CUSTOM

```bash
# 1. Check logs at startup:
✅ "Using CUSTOM strategy"

# 2. Check your custom implementation
# Verify your tables are used
```

---

## Summary

### 🎯 Three Clear Paths - User Chooses One

| Path | Configuration | Auto-Create | User Work |
|------|--------------|-------------|-----------|
| **1. SINGLE_TABLE** | `strategy: SINGLE_TABLE` | ✅ YES | None |
| **2. PER_TYPE_TABLE** | `strategy: PER_TYPE_TABLE` | ✅ YES | None |
| **3. CUSTOM** | `strategy: CUSTOM` + class | ❌ NO | Implement + create |

### ✨ Key Benefits

✅ **No confusion** - One choice, clear path  
✅ **Simple setup** - SINGLE_TABLE or PER_TYPE_TABLE = done!  
✅ **Enterprise ready** - PER_TYPE_TABLE scales 10M - 1B+  
✅ **Full flexibility** - CUSTOM for anything  
✅ **Database agnostic** - Auto-detect supports 9 database types  
✅ **Production ready** - Auto-table creation included  

---

**🎉 Choose your path: SINGLE_TABLE (simple), PER_TYPE_TABLE (scalable), or CUSTOM (flexible)!**

# 🗄️ Database Compatibility & User-Provided Schema Guide

**CRITICAL DECISION**: Auto-generated SQL vs. User-Provided Tables

---

## 🚨 The Issue with Current Auto-Generated SQL

### Current SQL (MySQL-Specific)

```sql
CREATE TABLE ai_searchable_product (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    searchable_content LONGTEXT NOT NULL,           -- ❌ MySQL only!
    vector_id VARCHAR(255),
    vector_updated_at TIMESTAMP NULL,
    metadata LONGTEXT,                              -- ❌ MySQL only!
    ai_analysis LONGTEXT,                           -- ❌ MySQL only!
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,    -- ❌ Varies!
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,  -- ❌ MySQL only!
    
    UNIQUE KEY uk_entity_id (entity_id),            -- ❌ MySQL syntax!
    INDEX idx_vector_id (vector_id),                -- ❌ MySQL syntax!
    INDEX idx_vector_updated_at (vector_updated_at),
    INDEX idx_created_at (created_at),
    FULLTEXT INDEX ft_searchable_content (searchable_content)  -- ❌ MySQL only!
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci  -- ❌ MySQL only!
```

### ❌ Compatibility Issues

| Database | Issue |
|----------|-------|
| **MySQL** | ✅ Works perfectly |
| **PostgreSQL** | ❌ `LONGTEXT` → `TEXT`, `TIMESTAMP` defaults differ, `FULLTEXT` different, `ENGINE`, `CHARSET` invalid |
| **SQL Server** | ❌ `LONGTEXT` → `NVARCHAR(MAX)`, `TIMESTAMP` → `datetime2`, different index syntax |
| **Oracle** | ❌ `VARCHAR(255)` → `VARCHAR2(255)`, `LONGTEXT` → `CLOB`, completely different |
| **H2/SQLite** | ❌ Different syntax, no `FULLTEXT`, limited features |

---

## ✅ SOLUTION: Make Auto-Creation Optional

### Recommended Approach

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      auto-create-tables: false  # ← User can disable!
      
      # Option 1: User provides pre-created tables
      # User creates tables manually before deployment
      
      # Option 2: User provides schema templates
      schema-template: "classpath:db/ai-searchable-schema.sql"
      
      # Option 3: User provides table names only
      # Framework uses existing tables
      custom-table-names:
        product: "my_product_search_table"
        user: "my_user_search_table"
        order: "my_order_search_table"
```

---

## 🏗️ Recommended Architecture

### Option 1: User Provides Pre-Created Tables (Recommended)

```
User Responsibility:
├─ Create tables using their database's SQL
├─ Create indices as needed
└─ Manage schema lifecycle

Library Responsibility:
├─ Read table names from config
├─ Use existing tables
└─ No auto-creation
```

**Configuration:**

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      auto-create-tables: false              # Disable auto-creation!
      table-mapping:
        product: "ai_searchable_product"     # User-created table
        user: "ai_searchable_user"
        order: "ai_searchable_order"
```

**User Setup (One-time):**

```bash
# PostgreSQL example
psql -U user -d ai_db -f schema/ai-searchable-schema-postgres.sql

# SQL Server example
sqlcmd -S server -U user -i schema\ai-searchable-schema-sqlserver.sql

# Oracle example
sqlplus user@db @schema/ai-searchable-schema-oracle.sql
```

### Option 2: Library Provides Multi-Database Templates

```
Library Provides:
├─ schema/ai-searchable-schema-mysql.sql
├─ schema/ai-searchable-schema-postgres.sql
├─ schema/ai-searchable-schema-sqlserver.sql
├─ schema/ai-searchable-schema-oracle.sql
└─ schema/ai-searchable-schema-h2.sql

User Does:
├─ Choose their database
├─ Run appropriate SQL script
├─ Update table names in config
└─ Deploy
```

**User Configuration:**

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      auto-create-tables: false
      schema-resource: "classpath:db/schema-postgres.sql"  # User selects!
      table-prefix: "ai_searchable_"
```

### Option 3: Auto-Create with Database Detection (Advanced)

```
Detect Database Type at Runtime:
├─ Check DatabaseMetaData.getDatabaseProductName()
├─ Generate database-specific SQL
└─ Execute appropriate schema
```

**Implementation:**

```java
private String generateCreateTableSQL(String tableName) {
    String dbType = detectDatabaseType();
    
    switch (dbType.toUpperCase()) {
        case "MYSQL":
            return generateMySQLSchema(tableName);
        case "POSTGRESQL":
            return generatePostgresSchema(tableName);
        case "MICROSOFT SQL SERVER":
            return generateSQLServerSchema(tableName);
        case "ORACLE":
            return generateOracleSchema(tableName);
        default:
            throw new UnsupportedDatabaseException(
                "Auto-creation not supported for: " + dbType);
    }
}

private String detectDatabaseType() {
    try (Connection conn = dataSource.getConnection()) {
        return conn.getMetaData().getDatabaseProductName();
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}
```

---

## 📋 Database-Specific SQL Templates

### MySQL Schema (Current)

```sql
CREATE TABLE ai_searchable_product (
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
    
    INDEX idx_vector_id (vector_id),
    INDEX idx_vector_updated_at (vector_updated_at),
    INDEX idx_created_at (created_at),
    FULLTEXT INDEX ft_searchable_content (searchable_content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### PostgreSQL Schema

```sql
CREATE TABLE ai_searchable_product (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(255) NOT NULL UNIQUE,
    searchable_content TEXT NOT NULL,                    -- TEXT instead of LONGTEXT
    vector_id VARCHAR(255),
    vector_updated_at TIMESTAMP NULL,
    metadata TEXT,                                      -- TEXT instead of LONGTEXT
    ai_analysis TEXT,                                   -- TEXT instead of LONGTEXT
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_ai_searchable_product PRIMARY KEY (id)
);

-- Create indices (PostgreSQL syntax)
CREATE INDEX idx_vector_id ON ai_searchable_product(vector_id);
CREATE INDEX idx_vector_updated_at ON ai_searchable_product(vector_updated_at);
CREATE INDEX idx_created_at ON ai_searchable_product(created_at);
CREATE INDEX idx_entity_id ON ai_searchable_product(entity_id);

-- Full-text search for PostgreSQL
CREATE INDEX ft_searchable_content ON ai_searchable_product 
    USING GIN(to_tsvector('english', searchable_content));
```

### SQL Server Schema

```sql
CREATE TABLE ai_searchable_product (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(255) NOT NULL UNIQUE,
    searchable_content NVARCHAR(MAX) NOT NULL,          -- NVARCHAR(MAX) for Unicode
    vector_id VARCHAR(255),
    vector_updated_at DATETIME2 NULL,                   -- DATETIME2 for precision
    metadata NVARCHAR(MAX),
    ai_analysis NVARCHAR(MAX),
    created_at DATETIME2 NOT NULL DEFAULT GETUTCDATE(), -- SQL Server default
    updated_at DATETIME2 NOT NULL DEFAULT GETUTCDATE()
);

-- Create indices (SQL Server syntax)
CREATE NONCLUSTERED INDEX idx_vector_id ON ai_searchable_product(vector_id);
CREATE NONCLUSTERED INDEX idx_vector_updated_at ON ai_searchable_product(vector_updated_at);
CREATE NONCLUSTERED INDEX idx_created_at ON ai_searchable_product(created_at);
CREATE NONCLUSTERED INDEX idx_entity_id ON ai_searchable_product(entity_id);

-- Full-text search for SQL Server
CREATE FULLTEXT CATALOG ft_catalog AS DEFAULT;
CREATE FULLTEXT INDEX ON ai_searchable_product(searchable_content)
    KEY INDEX pk_ai_searchable_product
    ON ft_catalog;
```

### Oracle Schema

```sql
CREATE TABLE ai_searchable_product (
    id VARCHAR2(36) PRIMARY KEY NOT NULL,
    entity_type VARCHAR2(50) NOT NULL,
    entity_id VARCHAR2(255) NOT NULL UNIQUE,
    searchable_content CLOB NOT NULL,                    -- CLOB for large text
    vector_id VARCHAR2(255),
    vector_updated_at TIMESTAMP NULL,
    metadata CLOB,
    ai_analysis CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT SYSDATE,
    updated_at TIMESTAMP NOT NULL DEFAULT SYSDATE
);

-- Create indices (Oracle syntax)
CREATE INDEX idx_vector_id ON ai_searchable_product(vector_id);
CREATE INDEX idx_vector_updated_at ON ai_searchable_product(vector_updated_at);
CREATE INDEX idx_created_at ON ai_searchable_product(created_at);
CREATE INDEX idx_entity_id ON ai_searchable_product(entity_id);

-- For full-text search in Oracle, use Oracle Text:
CREATE INDEX ft_searchable_content ON ai_searchable_product(searchable_content)
    INDEXTYPE IS CTXSYS.CONTEXT;
```

### H2/SQLite Schema

```sql
CREATE TABLE ai_searchable_product (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(255) NOT NULL UNIQUE,
    searchable_content CLOB NOT NULL,                    -- CLOB or TEXT
    vector_id VARCHAR(255),
    vector_updated_at TIMESTAMP NULL,
    metadata CLOB,
    ai_analysis CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indices (H2/SQLite syntax)
CREATE INDEX idx_vector_id ON ai_searchable_product(vector_id);
CREATE INDEX idx_vector_updated_at ON ai_searchable_product(vector_updated_at);
CREATE INDEX idx_created_at ON ai_searchable_product(created_at);
CREATE INDEX idx_entity_id ON ai_searchable_product(entity_id);

-- Full-text search not available in SQLite (limited FTS5)
-- H2 has limited FTS support
```

---

## 🎯 Recommended Implementation

### Best Practice: User-Provided Tables

**Why?**

✅ Database agnostic  
✅ User has control over schema  
✅ No dependency on library knowing all databases  
✅ Users can optimize for their specific database  
✅ Easier to maintain schema versions  
✅ Production-ready (DBA-approved schemas)  

**How:**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PerTypeTableAutoCreationService {
    
    private final AIEntityConfigurationService configService;
    private final DataSource dataSource;
    
    // New property to control auto-creation
    @Value("${ai-infrastructure.storage.per-type-tables.auto-create-tables:false}")
    private boolean autoCreateTablesEnabled;
    
    @EventListener(ApplicationReadyEvent.class)
    public void createTablesForConfiguredEntities() {
        // Only run if enabled!
        if (!autoCreateTablesEnabled) {
            log.info("Auto-table creation is DISABLED. " +
                "User must create tables manually. " +
                "See: classpath:db/ai-searchable-schema-*.sql");
            return;
        }
        
        // Only for MySQL (for safety)
        String dbType = detectDatabaseType();
        if (!isMySQLCompatible(dbType)) {
            log.warn("Auto-table creation only supports MySQL. " +
                "Detected: {}. Please create tables manually using: " +
                "classpath:db/ai-searchable-schema-{}.sql",
                dbType, dbType.toLowerCase());
            return;
        }
        
        // ... auto-create logic ...
    }
    
    private String detectDatabaseType() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            log.error("Could not detect database type", e);
            return "UNKNOWN";
        }
    }
    
    private boolean isMySQLCompatible(String dbType) {
        return dbType.contains("MySQL") || dbType.contains("MariaDB");
    }
}
```

**Configuration:**

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      auto-create-tables: false  # ← DISABLED by default!
      table-mapping:
        product: "ai_searchable_product"
        user: "ai_searchable_user"
        order: "ai_searchable_order"
```

**User Setup (One-time):**

```bash
# User chooses their database and runs appropriate script
cat classpath:db/ai-searchable-schema-postgres.sql | psql -U user -d ai_db
```

---

## 📋 Proposed Configuration Options

```yaml
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE
    per-type-tables:
      # OPTION 1: Disable auto-creation (RECOMMENDED)
      auto-create-tables: false
      
      # OPTION 2: Enable only for MySQL
      auto-create-tables: true
      auto-create-supported-databases: ["MySQL", "MariaDB"]
      
      # OPTION 3: User provides table names
      table-mapping:
        product: "my_product_table"
        user: "my_user_table"
      
      # OPTION 4: User provides schema file
      schema-resource: "classpath:db/ai-searchable-schema.sql"
```

---

## ✅ Recommendation Summary

### What Should We Do?

**1. Make auto-creation optional** ✅

```yaml
auto-create-tables: false  # Default to OFF for safety
```

**2. Provide database-specific SQL templates** ✅

```
src/main/resources/db/
├─ ai-searchable-schema-mysql.sql
├─ ai-searchable-schema-postgres.sql
├─ ai-searchable-schema-sqlserver.sql
├─ ai-searchable-schema-oracle.sql
└─ README.md (which to use)
```

**3. Document in library README** ✅

```markdown
## Database Setup

### Option 1: Auto-Create (MySQL only)
Set `auto-create-tables: true`

### Option 2: Manual Setup (All databases)
1. Choose your database script
2. Run: `psql -U user -d db < schema-postgres.sql`
3. Set table names in config
4. Deploy
```

**4. Update service to warn about compatibility** ✅

```java
// Warn if non-MySQL and auto-create enabled
if (autoCreateTablesEnabled && !isMySQLCompatible(dbType)) {
    log.warn("⚠️ Auto-table creation only supports MySQL. " +
        "Run: classpath:db/ai-searchable-schema-" + dbType + ".sql");
}
```

---

## 🔄 Updated PerTypeTableAutoCreationService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PerTypeTableAutoCreationService {
    
    private final AIEntityConfigurationService configService;
    private final DataSource dataSource;
    
    @Value("${ai-infrastructure.storage.per-type-tables.auto-create-tables:false}")
    private boolean autoCreateTablesEnabled;
    
    @Value("${ai-infrastructure.storage.per-type-tables.auto-create-only-mysql:true}")
    private boolean onlyCreateForMySQL;
    
    @EventListener(ApplicationReadyEvent.class)
    public void createTablesForConfiguredEntities() {
        // Default: off for safety and multi-database support
        if (!autoCreateTablesEnabled) {
            log.info("""
                ℹ️ Auto-table creation is DISABLED.
                
                To enable (MySQL only):
                  ai-infrastructure.storage.per-type-tables.auto-create-tables: true
                
                For other databases, use these scripts:
                  - PostgreSQL: classpath:db/ai-searchable-schema-postgres.sql
                  - SQL Server: classpath:db/ai-searchable-schema-sqlserver.sql
                  - Oracle: classpath:db/ai-searchable-schema-oracle.sql
                  - H2/SQLite: classpath:db/ai-searchable-schema-h2.sql
                
                Then set table names in config.
                """);
            return;
        }
        
        // Warn if not MySQL
        String dbType = detectDatabaseType();
        if (onlyCreateForMySQL && !isMySQLCompatible(dbType)) {
            log.warn("""
                ⚠️ WARNING: Auto-table creation is enabled but detected: {}
                
                Auto-creation only supports MySQL/MariaDB.
                
                For {}, please:
                1. Run: classpath:db/ai-searchable-schema-{}.sql
                2. Then deploy with auto-create-tables: false
                
                Proceeding with caution... tables will attempt to create but may fail.
                """, dbType, dbType, dbType.toLowerCase());
        }
        
        try {
            List<AIEntityConfig> configs = configService.getAllEntityConfigs();
            log.info("🚀 Starting auto-creation ({}): {} entity types",
                dbType, configs.size());
            
            for (AIEntityConfig config : configs) {
                createTableForEntityType(config.getEntityType(), dbType);
            }
            
            log.info("✅ Auto-creation completed successfully");
        } catch (Exception e) {
            log.error("❌ Auto-creation failed", e);
            throw new RuntimeException("Auto-table creation failed", e);
        }
    }
    
    private String detectDatabaseType() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            log.error("Could not detect database type", e);
            return "UNKNOWN";
        }
    }
    
    private boolean isMySQLCompatible(String dbType) {
        return dbType != null && 
               (dbType.contains("MySQL") || dbType.contains("MariaDB"));
    }
    
    // ... rest of implementation ...
}
```

---

## 📚 Documentation to Create

Create file: `DATABASE_COMPATIBILITY_GUIDE.md`

Explain:
1. Current MySQL-only auto-creation
2. Options for other databases
3. How to set up for PostgreSQL/SQL Server/Oracle
4. Configuration for user-provided tables
5. Best practices for production

---

## 🎯 Final Recommendation

### ✅ Make This Change

**In COMPREHENSIVE_IMPLEMENTATION_GUIDE.md:**

1. **Add warning** about MySQL-only compatibility
2. **Change default** to `auto-create-tables: false`
3. **Provide database-specific SQL** templates
4. **Show how to use** pre-created tables

**Code change:**

```java
// Line ~390 in PerTypeTableAutoCreationService
@EventListener(ApplicationReadyEvent.class)
public void createTablesForConfiguredEntities() {
    if (!autoCreateTablesEnabled) {
        log.info("ℹ️ Auto-table creation disabled. " +
            "Use provided SQL scripts or pre-create tables.");
        return;
    }
    // ... rest ...
}
```

**Configuration:**

```yaml
ai-infrastructure:
  storage:
    per-type-tables:
      auto-create-tables: false  # OFF by default for safety!
```

This makes it:
✅ Database-agnostic  
✅ User-controlled  
✅ Production-safe  
✅ Multi-database friendly  

---

Should I create the updated guide with this correction? 🤔



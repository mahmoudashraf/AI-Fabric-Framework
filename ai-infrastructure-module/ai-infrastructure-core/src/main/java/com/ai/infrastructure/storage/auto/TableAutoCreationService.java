package com.ai.infrastructure.storage.auto;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.storage.StorageStrategyProvider;
import com.ai.infrastructure.storage.strategy.impl.PerTypeRepositoryFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TableAutoCreationService {

    private final DataSource dataSource;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final StorageStrategyProvider strategyProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void createTablesAtStartup() {
        String strategy = strategyProvider.getStrategy();
        if ("CUSTOM".equalsIgnoreCase(strategy)) {
            log.info("Storage strategy CUSTOM selected. Skipping auto table creation.");
            return;
        }

        try {
            String dbType = detectDatabaseType();
            log.info("Detected database type: {}", dbType);

            createIndexingQueueTable(dbType);

            switch (strategy.toUpperCase()) {
                case "SINGLE_TABLE" -> createSingleTable(dbType);
                case "PER_TYPE_TABLE" -> createPerTypeTables(dbType);
                default -> log.warn("Unknown storage strategy '{}'. Skipping table creation.", strategy);
            }
        } catch (Exception ex) {
            log.error("Failed to auto-create AISearchableEntity tables", ex);
            throw new IllegalStateException("Auto-table creation failed", ex);
        }
    }

    private void createSingleTable(String dbType) throws SQLException {
        String tableName = "ai_searchable_entities";
        if (tableExists(tableName)) {
            log.debug("Table {} already exists", tableName);
            return;
        }
        executeSql(generateCreateTableSQL(dbType, tableName));
        log.info("Created AISearchableEntity table {}", tableName);
    }

    private void createPerTypeTables(String dbType) throws SQLException {
        List<String> entityTypes = List.copyOf(entityConfigurationLoader.getSupportedEntityTypes());
        log.info("Creating per-type tables for {} entity types", entityTypes.size());
        for (String entityType : entityTypes) {
            String tableName = PerTypeRepositoryFactory.toTableName(entityType);
            if (tableExists(tableName)) {
                log.debug("Table {} already exists", tableName);
                continue;
            }
            executeSql(generateCreateTableSQL(dbType, tableName));
            log.info("Created AISearchableEntity table {}", tableName);
        }
    }

    private String generateCreateTableSQL(String dbType, String tableName) {
        String normalized = dbType != null ? dbType.toUpperCase() : "UNKNOWN";
        return switch (normalized) {
            case "MYSQL" -> mysqlSql(tableName);
            case "POSTGRESQL" -> postgresSql(tableName);
            case "SQLSERVER" -> sqlServerSql(tableName);
            case "ORACLE" -> oracleSql(tableName);
            case "H2" -> h2Sql(tableName);
            case "SQLITE" -> sqliteSql(tableName);
            case "DB2" -> db2Sql(tableName);
            case "DERBY" -> derbySql(tableName);
            case "SYBASE" -> sybaseSql(tableName);
            default -> throw new UnsupportedOperationException("Database " + dbType + " not supported for auto-create. Use CUSTOM strategy.");
        };
    }

    private void createIndexingQueueTable(String dbType) throws SQLException {
        String tableName = "ai_indexing_queue";
        if (tableExists(tableName)) {
            log.debug("Indexing queue table {} already exists", tableName);
            return;
        }
        executeSql(generateCreateIndexingQueueSQL(dbType, tableName));
        log.info("Created AI indexing queue table {}", tableName);
    }

    private String generateCreateIndexingQueueSQL(String dbType, String tableName) {
        String normalized = dbType != null ? dbType.toUpperCase() : "UNKNOWN";
        return switch (normalized) {
            case "MYSQL" -> """
                CREATE TABLE %s (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    entity_type VARCHAR(128) NOT NULL,
                    entity_id VARCHAR(255),
                    entity_class VARCHAR(256) NOT NULL,
                    operation VARCHAR(32) NOT NULL,
                    strategy VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    priority VARCHAR(32) NOT NULL,
                    priority_weight INT NOT NULL,
                    generate_embedding BOOLEAN NOT NULL,
                    index_for_search BOOLEAN NOT NULL,
                    enable_analysis BOOLEAN NOT NULL,
                    remove_from_search BOOLEAN NOT NULL,
                    cleanup_embeddings BOOLEAN NOT NULL,
                    payload LONGTEXT NOT NULL,
                    max_retries INT NOT NULL,
                    retry_count INT NOT NULL,
                    error_message LONGTEXT,
                    dead_letter_reason LONGTEXT,
                    processing_node VARCHAR(64),
                    requested_at TIMESTAMP NOT NULL,
                    scheduled_for TIMESTAMP NOT NULL,
                    started_at TIMESTAMP NULL,
                    completed_at TIMESTAMP NULL,
                    visibility_timeout_until TIMESTAMP NULL,
                    updated_at TIMESTAMP NULL,
                    created_at TIMESTAMP NULL,
                    INDEX idx_ai_queue_status_strategy (status, strategy),
                    INDEX idx_ai_queue_scheduled (scheduled_for),
                    INDEX idx_ai_queue_entity (entity_type, entity_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """.formatted(tableName);
            case "POSTGRESQL", "H2", "SQLITE" -> """
                CREATE TABLE %s (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    entity_type VARCHAR(128) NOT NULL,
                    entity_id VARCHAR(255),
                    entity_class VARCHAR(256) NOT NULL,
                    operation VARCHAR(32) NOT NULL,
                    strategy VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    priority VARCHAR(32) NOT NULL,
                    priority_weight INT NOT NULL,
                    generate_embedding BOOLEAN NOT NULL,
                    index_for_search BOOLEAN NOT NULL,
                    enable_analysis BOOLEAN NOT NULL,
                    remove_from_search BOOLEAN NOT NULL,
                    cleanup_embeddings BOOLEAN NOT NULL,
                    payload TEXT NOT NULL,
                    max_retries INT NOT NULL,
                    retry_count INT NOT NULL,
                    error_message TEXT,
                    dead_letter_reason TEXT,
                    processing_node VARCHAR(64),
                    requested_at TIMESTAMP NOT NULL,
                    scheduled_for TIMESTAMP NOT NULL,
                    started_at TIMESTAMP NULL,
                    completed_at TIMESTAMP NULL,
                    visibility_timeout_until TIMESTAMP NULL,
                    updated_at TIMESTAMP NULL,
                    created_at TIMESTAMP NULL
                );
                CREATE INDEX idx_ai_queue_status_strategy ON %s(status, strategy);
                CREATE INDEX idx_ai_queue_scheduled ON %s(scheduled_for);
                CREATE INDEX idx_ai_queue_entity ON %s(entity_type, entity_id);
                """.formatted(tableName, tableName, tableName, tableName);
            default -> throw new UnsupportedOperationException("Database " + dbType + " not supported for indexing queue auto-create. Use CUSTOM strategy.");
        };
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (var tables = meta.getTables(null, null, tableName, null)) {
                if (tables.next()) {
                    return true;
                }
            }
            try (var tables = meta.getTables(null, null, tableName.toUpperCase(), null)) {
                return tables.next();
            }
        }
    }

    private void executeSql(String sql) throws SQLException {
        String[] statements = sql.split(";");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String statement : statements) {
                if (statement == null || statement.trim().isEmpty()) {
                    continue;
                }
                try {
                    stmt.execute(statement);
                } catch (SQLException ex) {
                    String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                    if (message.contains("already exists")) {
                        log.debug("Skipping statement because object already exists: {}", message);
                        continue;
                    }
                    throw ex;
                }
            }
        }
    }

    private String detectDatabaseType() {
        try (Connection conn = dataSource.getConnection()) {
            return normalizeDatabaseType(conn.getMetaData().getDatabaseProductName());
        } catch (SQLException ex) {
            log.warn("Could not detect database type", ex);
            return "UNKNOWN";
        }
    }

    private String normalizeDatabaseType(String productName) {
        if (productName == null) {
            return "UNKNOWN";
        }
        String normalized = productName.toUpperCase();
        if (normalized.contains("MYSQL") || normalized.contains("MARIADB") || normalized.contains("PERCONA")) {
            return "MYSQL";
        }
        if (normalized.contains("POSTGRES")) {
            return "POSTGRESQL";
        }
        if (normalized.contains("SQL SERVER") || normalized.contains("MSSQL") || normalized.contains("AZURE SQL")) {
            return "SQLSERVER";
        }
        if (normalized.contains("ORACLE")) {
            return "ORACLE";
        }
        if (normalized.contains("H2")) {
            return "H2";
        }
        if (normalized.contains("SQLITE")) {
            return "SQLITE";
        }
        if (normalized.contains("DB2")) {
            return "DB2";
        }
        if (normalized.contains("DERBY")) {
            return "DERBY";
        }
        if (normalized.contains("SYBASE")) {
            return "SYBASE";
        }
        return "UNKNOWN";
    }

    private String mysqlSql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content LONGTEXT,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata LONGTEXT,
                ai_analysis LONGTEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_entity_type (entity_type),
                INDEX idx_vector_id (vector_id),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """.formatted(tableName);
    }

    private String postgresSql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content TEXT,
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
            """.formatted(tableName, tableName, tableName, tableName);
    }

    private String sqlServerSql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content NVARCHAR(MAX),
                vector_id VARCHAR(255),
                vector_updated_at DATETIME2 NULL,
                metadata NVARCHAR(MAX),
                ai_analysis NVARCHAR(MAX),
                created_at DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
                updated_at DATETIME2 NOT NULL DEFAULT GETUTCDATE()
            );
            CREATE INDEX idx_entity_type ON %s(entity_type);
            CREATE INDEX idx_vector_id ON %s(vector_id);
            CREATE INDEX idx_created_at ON %s(created_at);
            """.formatted(tableName, tableName, tableName, tableName);
    }

    private String oracleSql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR2(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR2(50) NOT NULL,
                entity_id VARCHAR2(255) NOT NULL UNIQUE,
                searchable_content CLOB,
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
            """.formatted(tableName, tableName, tableName, tableName);
    }

    private String h2Sql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content CLOB,
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
            """.formatted(tableName, tableName, tableName, tableName);
    }

    private String sqliteSql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content TEXT,
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
            """.formatted(tableName, tableName, tableName, tableName);
    }

    private String db2Sql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content CLOB,
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
            """.formatted(tableName, tableName, tableName, tableName);
    }

    private String derbySql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content CLOB,
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
            """.formatted(tableName, tableName, tableName, tableName);
    }

    private String sybaseSql(String tableName) {
        return """
            CREATE TABLE %s (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content TEXT,
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
            """.formatted(tableName, tableName, tableName, tableName);
    }
}

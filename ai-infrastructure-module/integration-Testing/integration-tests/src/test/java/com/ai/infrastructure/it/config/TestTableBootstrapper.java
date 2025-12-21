package com.ai.infrastructure.it.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.storage.strategy.impl.PerTypeRepositoryFactory;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures per-entity AI searchable tables exist in H2 for integration tests.
 *
 * Some integration tests clear vectors before any data is indexed. If the
 * per-type tables do not exist yet, those cleanups fail with "table not found".
 * We proactively create the tables for all configured entity types.
 */
@Component
@Profile("disabled-test-table-bootstrapper")
@RequiredArgsConstructor
@Slf4j
public class TestTableBootstrapper {

    private final JdbcTemplate jdbcTemplate;
    private final AIEntityConfigurationLoader configLoader;

    @PostConstruct
    public void createPerTypeTablesIfMissing() {
        createIndexingQueueIfAbsent();

        Set<String> entityTypes = configLoader.getSupportedEntityTypes();
        if (entityTypes.isEmpty()) {
            log.warn("No entity types found in AI entity config; skipping table bootstrap.");
            return;
        }

        log.info("Bootstrapping AI searchable tables for {} entity types", entityTypes.size());
        entityTypes.forEach(this::createTableIfAbsent);
    }

    private void createTableIfAbsent(String entityType) {
        String tableName = PerTypeRepositoryFactory.toTableName(entityType);
        // H2 supports IF NOT EXISTS for both tables and indexes.
        String ddl = """
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                entity_type VARCHAR(50) NOT NULL,
                entity_id VARCHAR(255) NOT NULL UNIQUE,
                searchable_content CLOB,
                vector_id VARCHAR(255),
                vector_updated_at TIMESTAMP NULL,
                metadata CLOB,
                ai_analysis CLOB,
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_%s_entity_type ON %s(entity_type);
            CREATE INDEX IF NOT EXISTS idx_%s_vector_id ON %s(vector_id);
            CREATE INDEX IF NOT EXISTS idx_%s_created_at ON %s(created_at);
            """.formatted(tableName, tableName, tableName, tableName, tableName, tableName, tableName);

        try {
            jdbcTemplate.execute(ddl);
            log.debug("Ensured AI searchable table exists: {}", tableName);
        } catch (Exception ex) {
            // If the table already exists with a slightly different schema, don't fail the context.
            log.warn("Skipping bootstrap for table {} due to existing definition: {}", tableName, ex.getMessage());
        }
    }

    private void createIndexingQueueIfAbsent() {
        // Minimal schema to satisfy integration tests that enqueue/dequeue work items.
        String ddl = """
            CREATE TABLE IF NOT EXISTS ai_indexing_queue (
                id IDENTITY PRIMARY KEY,
                entity_type VARCHAR(128) NOT NULL,
                entity_id VARCHAR(255) NOT NULL,
                status VARCHAR(64) NOT NULL,
                priority INTEGER DEFAULT 0,
                requested_at TIMESTAMP,
                scheduled_for TIMESTAMP,
                processing_started_at TIMESTAMP,
                processed_at TIMESTAMP,
                updated_at TIMESTAMP,
                payload CLOB,
                error_message CLOB
            );
            CREATE INDEX IF NOT EXISTS idx_ai_idx_queue_entity ON ai_indexing_queue(entity_type, entity_id);
            CREATE INDEX IF NOT EXISTS idx_ai_idx_queue_status ON ai_indexing_queue(status, priority, scheduled_for);
            """;

        try {
            jdbcTemplate.execute(ddl);
            log.debug("Ensured AI indexing queue table exists");
        } catch (Exception ex) {
            log.warn("Skipping bootstrap for ai_indexing_queue due to existing definition: {}", ex.getMessage());
        }
    }
}

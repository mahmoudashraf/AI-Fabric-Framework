package com.ai.infrastructure.it.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.storage.strategy.impl.PerTypeRepositoryFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "ai-infrastructure.storage.strategy=PER_TYPE_TABLE")
class PerTypeTableStorageStrategyIntegrationTest {

    @Autowired
    private AISearchableEntityStorageStrategy storageStrategy;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clean() {
        storageStrategy.deleteAll();
    }

    @Test
    void perTypeStrategyCreatesTablesAndPersistsEntities() throws Exception {
        assertEquals("PER_TYPE_TABLE", storageStrategy.getStrategyName());
        assertTrue(storageStrategy.isHealthy());

        for (String entityType : storageStrategy.getSupportedEntityTypes()) {
            assertTrue(tableExists(PerTypeRepositoryFactory.toTableName(entityType)));
        }

        AISearchableEntity product = AISearchableEntity.builder()
            .entityType("product")
            .entityId("prod-42")
            .searchableContent("AI enabled laptop")
            .vectorId("vector-prod-42")
            .vectorUpdatedAt(LocalDateTime.now())
            .metadata("{\"category\":\"electronics\",\"owner\":\"user-7\"}")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        storageStrategy.save(product);

        AISearchableEntity persisted = storageStrategy
            .findByEntityTypeAndEntityId("product", "prod-42")
            .orElseThrow();

        assertEquals("vector-prod-42", persisted.getVectorId());
        assertNotNull(persisted.getVectorUpdatedAt());

        List<AISearchableEntity> metadataMatches = storageStrategy.findByMetadataContainingSnippet("user-7");
        assertFalse(metadataMatches.isEmpty());
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
                if (rs.next()) {
                    return true;
                }
            }
            try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), null)) {
                return rs.next();
            }
        }
    }
}

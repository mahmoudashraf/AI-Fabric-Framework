package com.ai.infrastructure.it.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "ai-infrastructure.storage.strategy=SINGLE_TABLE")
class SingleTableStorageStrategyIntegrationTest {

    @Autowired
    private AISearchableEntityStorageStrategy storageStrategy;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clean() {
        storageStrategy.deleteAll();
    }

    @Test
    void singleTableStrategyPersistsAndFindsEntities() throws Exception {
        assertEquals("SINGLE_TABLE", storageStrategy.getStrategyName());
        assertTrue(storageStrategy.isHealthy());
        assertTrue(tableExists("ai_searchable_entities"));

        AISearchableEntity entity = AISearchableEntity.builder()
            .entityType("test-product")
            .entityId("product-1")
            .searchableContent("AI powered device")
            .vectorId("vec-123")
            .metadata("{\"category\":\"electronics\"}")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        storageStrategy.save(entity);

        AISearchableEntity persisted = storageStrategy
            .findByEntityTypeAndEntityId("test-product", "product-1")
            .orElseThrow();

        assertEquals("vec-123", persisted.getVectorId());
        assertNotNull(persisted.getUpdatedAt());
        assertTrue(storageStrategy.countByVectorIdIsNotNull() >= 1);
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

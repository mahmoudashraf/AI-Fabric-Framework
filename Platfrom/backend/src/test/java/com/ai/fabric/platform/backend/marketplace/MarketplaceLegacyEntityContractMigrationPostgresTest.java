package com.ai.fabric.platform.backend.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MarketplaceLegacyEntityContractMigrationPostgresTest {

    private static final String MIGRATION_RESOURCE =
        "db/migration/V130__normalize_restored_marketplace_entity_contracts_v04.sql";
    private static final Set<String> LEGACY_FIELDS = Set.of(
        "entity-type",
        "features",
        "auto-process",
        "enable-search",
        "enable-recommendations",
        "auto-embedding",
        "indexable",
        "embeddable-fields",
        "crud-operations",
        "description"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void migratesDynamicLegacyEntityTypesAndLeavesV04EntriesUntouched() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            createSchema(connection);
            String legacyManifest = legacyManifest();
            String validManifest = validV04Manifest();
            insertVersion(connection, "legacy", "mkp-data-produs-safe-knowledge", "0.1.0", legacyManifest);
            insertVersion(connection, "valid", "mkp-data-valid", "1.0.0", validManifest);

            executeMigration(connection);

            JsonNode migrated = manifest(connection, "legacy");
            JsonNode entities = migrated.at("/contributions/entityConfig/ai-entities");
            assertThat(iterableFieldNames(entities))
                .containsExactlyInAnyOrder("service-module", "package-template");
            entities.forEach(this::assertV04Entity);
            assertThat(manifest(connection, "valid")).isEqualTo(objectMapper.readTree(validManifest));

            JsonNode firstPass = migrated.deepCopy();
            executeMigration(connection);
            assertThat(manifest(connection, "legacy")).isEqualTo(firstPass);
        }
    }

    private void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                create table platform_marketplace_plugin_versions (
                    id varchar(64) primary key,
                    plugin_id varchar(128) not null,
                    version varchar(64) not null,
                    status varchar(64) not null,
                    manifest_json text not null
                )
                """);
        }
    }

    private void insertVersion(Connection connection,
                               String id,
                               String pluginId,
                               String version,
                               String manifest) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into platform_marketplace_plugin_versions (
                id,
                plugin_id,
                version,
                status,
                manifest_json
            ) values (?, ?, ?, 'PUBLISHED', ?)
            """)) {
            statement.setString(1, id);
            statement.setString(2, pluginId);
            statement.setString(3, version);
            statement.setString(4, manifest);
            statement.executeUpdate();
        }
    }

    private void executeMigration(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(readMigration());
        }
    }

    private JsonNode manifest(Connection connection, String id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "select manifest_json from platform_marketplace_plugin_versions where id = ?"
        )) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return objectMapper.readTree(result.getString(1));
            }
        }
    }

    private void assertV04Entity(JsonNode entity) {
        Set<String> fields = new HashSet<>();
        entity.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).doesNotContainAnyElementsOf(LEGACY_FIELDS);
        assertThat(entity.at("/indexing/enabled").asBoolean()).isTrue();
        assertThat(entity.at("/indexing/max-characters").asInt()).isEqualTo(8000);
        assertThat(entity.at("/analysis/enabled").asBoolean()).isFalse();
        assertThat(entity.at("/analysis/after").isArray()).isTrue();
        assertThat(entity.at("/searchable-fields/0/name").asText()).isEqualTo("content");
        assertThat(entity.at("/metadata-fields/2/name").asText()).isEqualTo("tenantId");
    }

    private Set<String> iterableFieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing migration resource " + MIGRATION_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String legacyManifest() {
        return """
            {
              "contributions": {
                "entityConfig": {
                  "ai-entities": {
                    "service-module": {
                      "entity-type": "service-module",
                      "description": "Service module knowledge.",
                      "features": ["embedding", "search"],
                      "auto-process": false,
                      "enable-search": true,
                      "auto-embedding": true,
                      "indexable": true
                    },
                    "package-template": {
                      "entity-type": "package-template",
                      "description": "Package template knowledge.",
                      "features": ["embedding", "search"],
                      "auto-process": false,
                      "enable-search": true,
                      "auto-embedding": true,
                      "indexable": true
                    }
                  }
                }
              }
            }
            """;
    }

    private String validV04Manifest() {
        return """
            {
              "contributions": {
                "entityConfig": {
                  "ai-entities": {
                    "document": {
                      "indexing": {
                        "enabled": true,
                        "max-characters": 4000
                      },
                      "analysis": {
                        "enabled": false,
                        "after": []
                      },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "max-length": 4000,
                          "priority": 100,
                          "required": true
                        }
                      ]
                    }
                  }
                }
              }
            }
            """;
    }
}

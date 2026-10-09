package com.ai.fabric.platform.backend.aiworkspace;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class AIWorkspaceServerOwnedRetrievalRoutingMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void removesBrowserOwnedRetrievalSpacesFromDealershipInstallationsOnly() throws Exception {
        migrateTo("153");

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into platform_consumers (
                    id, customer_id, consumer_id, display_name, status, created_at, updated_at
                ) values (
                    'consumer-workspace-migration', 'cust-internal', 'workspace-migration',
                    'Workspace migration test', 'ACTIVE', current_timestamp, current_timestamp
                )
                """);
            statement.executeUpdate(installationInsert(
                "workspace-dealership",
                "installation-dealership",
                "dealership",
                "1.0.0"
            ));
            statement.executeUpdate(installationInsert(
                "workspace-generic",
                "installation-generic",
                "generic-support",
                "1.0.0"
            ));
        }

        migrateTo("154");

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                select experience_pack_version,
                       configuration_json::jsonb ? 'knowledge' as has_knowledge,
                       configuration_json::jsonb #> '{knowledge,retrievalVectorSpaces}' as retrieval_spaces,
                       configuration_json::jsonb #>> '{knowledge,inventoryVectorSpace}' as inventory_space,
                       row_version
                from ai_workspace_installations
                where id = 'workspace-dealership'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("experience_pack_version")).isEqualTo("1.1.0");
                assertThat(result.getBoolean("has_knowledge")).isTrue();
                assertThat(result.getString("retrieval_spaces")).isNull();
                assertThat(result.getString("inventory_space")).isEqualTo("dealer-vehicle");
                assertThat(result.getLong("row_version")).isEqualTo(1L);
            }

            try (ResultSet result = statement.executeQuery("""
                select experience_pack_version,
                       configuration_json::jsonb #> '{knowledge,retrievalVectorSpaces}' as retrieval_spaces,
                       row_version
                from ai_workspace_installations
                where id = 'workspace-generic'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("experience_pack_version")).isEqualTo("1.0.0");
                assertThat(result.getString("retrieval_spaces")).contains("dealer-vehicle", "document");
                assertThat(result.getLong("row_version")).isZero();
            }
        }
    }

    private void migrateTo(String target) {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(target))
            .load()
            .migrate();
    }

    private String installationInsert(String id,
                                      String installationId,
                                      String packCode,
                                      String packVersion) {
        return """
            insert into ai_workspace_installations (
                id, installation_id, customer_id, consumer_entity_id, display_name, status,
                experience_pack_code, experience_pack_version, connection_mode,
                connection_profile_code, connection_profile_version,
                connection_configuration_json, allowed_origins_json, configuration_json,
                created_at, updated_at, row_version
            ) values (
                '%s', '%s', 'cust-internal', 'consumer-workspace-migration', 'Workspace test', 'ACTIVE',
                '%s', '%s', 'PUBLIC_RUNTIME_ANONYMOUS', 'public-runtime-anonymous', '1.0.0',
                '{}', '[\"https://example.test\"]',
                '{\"knowledge\":{\"inventoryVectorSpace\":\"dealer-vehicle\",\"retrievalVectorSpaces\":[\"dealer-vehicle\",\"document\"]},\"branding\":{\"title\":\"Test\"}}',
                current_timestamp, current_timestamp, 0
            )
            """.formatted(id, installationId, packCode, packVersion);
    }
}

package com.ai.fabric.platform.backend.aiworkspace;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class AIWorkspaceStandaloneDeliveryMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingInstallationsAndEnforcesDomainOwnership() throws Exception {
        migrateTo("157");
        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into platform_consumers (
                    id, customer_id, consumer_id, display_name, status, created_at, updated_at
                ) values (
                    'consumer-standalone-migration', 'cust-internal', 'standalone-migration',
                    'Standalone migration test', 'ACTIVE', current_timestamp, current_timestamp
                )
                """);
            statement.executeUpdate(installationInsert(
                "workspace-existing", "installation-existing", null));
        }

        migrateTo("158");

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                select delivery_mode, interaction_policy, auth_profile_code,
                       auth_profile_version, auth_configuration_json
                from ai_workspace_installations
                where id = 'workspace-existing'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("delivery_mode")).isEqualTo("EMBEDDED");
                assertThat(result.getString("interaction_policy")).isEqualTo("PUBLIC");
                assertThat(result.getString("auth_profile_code")).isEqualTo("none");
                assertThat(result.getString("auth_profile_version")).isEqualTo("1.0.0");
                assertThat(result.getString("auth_configuration_json")).isEqualTo("{}");
            }

            statement.executeUpdate(installationInsert(
                "workspace-standalone", "installation-standalone", "northfield"));
            assertThatThrownBy(() -> statement.executeUpdate(installationInsert(
                "workspace-duplicate", "installation-duplicate", "NORTHFIELD")))
                .isInstanceOf(SQLException.class);

            statement.executeUpdate("""
                insert into ai_workspace_domains (
                    id, installation_entity_id, hostname, domain_type, status,
                    verification_token, created_at, updated_at, row_version
                ) values (
                    'domain-northfield', 'workspace-standalone', 'northfield.apps.loomai.test',
                    'PLATFORM_DEFAULT', 'VERIFIED', 'verification-token',
                    current_timestamp, current_timestamp, 0
                )
                """);
            statement.executeUpdate("delete from ai_workspace_installations where id = 'workspace-standalone'");
            try (ResultSet result = statement.executeQuery(
                "select count(*) from ai_workspace_domains where id = 'domain-northfield'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
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

    private String installationInsert(String id, String installationId, String appSlug) {
        String newColumns = appSlug == null
            ? ""
            : ", delivery_mode, app_slug, interaction_policy, auth_profile_code, auth_profile_version, auth_configuration_json";
        String newValues = appSlug == null
            ? ""
            : ", 'STANDALONE', '" + appSlug + "', 'PUBLIC', 'none', '1.0.0', '{}'";
        return """
            insert into ai_workspace_installations (
                id, installation_id, customer_id, consumer_entity_id, display_name, status,
                experience_pack_code, experience_pack_version, connection_mode,
                connection_profile_code, connection_profile_version,
                connection_configuration_json, allowed_origins_json, configuration_json,
                created_at, updated_at, row_version%s
            ) values (
                '%s', '%s', 'cust-internal', 'consumer-standalone-migration', 'Workspace test', 'DRAFT',
                'generic-workspace', '1.0.0', 'PUBLIC_RUNTIME_ANONYMOUS',
                'runtime-anonymous-direct', '1.0.0', '{}', '["https://example.test"]', '{}',
                current_timestamp, current_timestamp, 0%s
            )
            """.formatted(newColumns, id, installationId, newValues);
    }
}

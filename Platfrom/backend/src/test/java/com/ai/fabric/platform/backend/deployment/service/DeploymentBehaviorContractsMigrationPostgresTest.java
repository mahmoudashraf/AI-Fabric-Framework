package com.ai.fabric.platform.backend.deployment.service;

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
class DeploymentBehaviorContractsMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void productionMigrationsCreateBehaviorCompositionAndMarketplaceContracts() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("133"))
            .load();

        flyway.migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(columnExists(statement, "platform_deployments", "behavior_type")).isTrue();
            assertThat(columnExists(statement, "platform_deployment_drafts", "behavior_config_json")).isTrue();
            assertThat(columnExists(statement, "platform_deployment_versions", "composition_provenance_json")).isTrue();
            assertThat(columnExists(statement, "deployment_source_artifacts", "capability_manifest_hash")).isTrue();

            try (ResultSet result = statement.executeQuery("""
                select plugin_type, count(*)
                from platform_marketplace_plugins
                where id in (
                    'mkp-specialist-deployment-intelligence',
                    'mkp-specialist-smart-brain-analysis',
                    'mkp-template-conversational-assistant',
                    'mkp-template-agentic-specialist-team',
                    'mkp-template-smart-brain'
                )
                group by plugin_type
                order by plugin_type
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("SPECIALIST");
                assertThat(result.getInt(2)).isEqualTo(2);
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("TEMPLATE");
                assertThat(result.getInt(2)).isEqualTo(3);
                assertThat(result.next()).isFalse();
            }

            try (ResultSet result = statement.executeQuery("""
                select count(*)
                from platform_marketplace_plugin_versions
                where id in (
                    'mkv-specialist-deployment-intelligence-v1',
                    'mkv-specialist-smart-brain-analysis-v1',
                    'mkv-template-conversational-assistant-v1',
                    'mkv-template-agentic-specialist-team-v1',
                    'mkv-template-smart-brain-v1'
                )
                  and status = 'PUBLISHED'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(5);
            }
        }
    }

    private boolean columnExists(Statement statement, String table, String column) throws Exception {
        try (ResultSet result = statement.executeQuery("""
            select exists (
                select 1
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = '%s'
                  and column_name = '%s'
            )
            """.formatted(table, column))) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }
}

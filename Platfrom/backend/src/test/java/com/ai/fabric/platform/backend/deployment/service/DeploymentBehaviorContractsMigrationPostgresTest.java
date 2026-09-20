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
            .target(MigrationVersion.fromVersion("137"))
            .load();

        flyway.migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(columnExists(statement, "platform_deployments", "behavior_type")).isTrue();
            assertThat(columnExists(statement, "platform_deployment_drafts", "behavior_config_json")).isTrue();
            assertThat(columnExists(statement, "platform_deployment_versions", "composition_provenance_json")).isTrue();
            assertThat(columnExists(statement, "deployment_source_artifacts", "capability_manifest_hash")).isTrue();
            assertThat(columnExists(statement, "platform_deployment_behavior_readiness", "material_hash")).isTrue();
            assertThat(columnExists(statement, "platform_deployment_behavior_readiness", "source_capability_manifest_hash")).isTrue();

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

            try (ResultSet result = statement.executeQuery("""
                select id, manifest_json
                from platform_marketplace_plugin_versions
                where id in (
                    'mkv-template-conversational-assistant-v101',
                    'mkv-template-agentic-specialist-team-v101',
                    'mkv-template-smart-brain-v101'
                )
                order by id
                """)) {
                assertVerifiedTemplateVersion(result, "mkv-template-agentic-specialist-team-v101");
                assertVerifiedTemplateVersion(result, "mkv-template-conversational-assistant-v101");
                assertVerifiedTemplateVersion(result, "mkv-template-smart-brain-v101");
                assertThat(result.next()).isFalse();
            }

            try (ResultSet result = statement.executeQuery("""
                select id, version, manifest_json, bundle_sha256
                from platform_marketplace_plugin_versions
                where id = 'mkv-specialist-deployment-intelligence-v101'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("1.0.1");
                assertThat(result.getString("manifest_json"))
                    .contains("sha256:ab1a1185dbe5f8ba5dc6c67c10c196bd9a569f211c537a39efb2d47fef05a025");
                assertThat(result.getString("bundle_sha256"))
                    .isEqualTo("sha256:ab1a1185dbe5f8ba5dc6c67c10c196bd9a569f211c537a39efb2d47fef05a025");
                assertThat(result.next()).isFalse();
            }

            try (ResultSet result = statement.executeQuery("""
                select id, version, manifest_json
                from platform_marketplace_plugin_versions
                where id = 'mkv-template-agentic-specialist-team-v102'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("1.0.2");
                assertThat(result.getString("manifest_json"))
                    .contains("\"security\": {\"authzMode\": \"ALLOW_VERIFIED\"}")
                    .contains("mkp-specialist-deployment-intelligence@1.0.1");
                assertThat(result.next()).isFalse();
            }

            try (ResultSet result = statement.executeQuery("""
                select id, environment_name, source_strategy, resource_defaults_json
                from deployment_target_profiles
                where id in (
                    'dtp-coolify-staging-behavior',
                    'dtp-coolify-production-behavior'
                )
                order by id
                """)) {
                assertBehaviorRuntimeProfile(result, "dtp-coolify-production-behavior", "production");
                assertBehaviorRuntimeProfile(result, "dtp-coolify-staging-behavior", "staging");
                assertThat(result.next()).isFalse();
            }
        }
    }

    private void assertBehaviorRuntimeProfile(ResultSet result,
                                              String expectedId,
                                              String expectedEnvironment) throws Exception {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("id")).isEqualTo(expectedId);
        assertThat(result.getString("environment_name")).isEqualTo(expectedEnvironment);
        assertThat(result.getString("source_strategy")).isEqualTo("IMAGE_SOURCE");
        assertThat(result.getString("resource_defaults_json"))
            .contains("\"runtimeDatabaseMode\":\"COOLIFY_POSTGRES\"")
            .contains("\"promotionChannel\":\"" + expectedEnvironment + "\"");
    }

    private void assertVerifiedTemplateVersion(ResultSet result, String expectedId) throws Exception {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("id")).isEqualTo(expectedId);
        assertThat(result.getString("manifest_json"))
            .contains("\"version\": \"1.0.1\"")
            .contains("\"security\": {\"authzMode\": \"ALLOW_VERIFIED\"}");
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

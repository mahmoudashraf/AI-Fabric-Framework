package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DeploymentTemplateDefaultsMigrationPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void backfillsInitialSettingsAndRemovesApplyApprovalWithoutChangingPublishedHistory() throws Exception {
        migrate("212");
        try (Connection connection = POSTGRES.createConnection(""); Statement sql = connection.createStatement()) {
            sql.executeUpdate("""
                INSERT INTO platform_customer_tenants
                    (id,customer_id,name,slug,status,created_at,updated_at)
                VALUES ('tenant-reset','cust-internal','Template reset','template-reset','ACTIVE',current_timestamp,current_timestamp)
                """);
            sql.executeUpdate("""
                INSERT INTO platform_deployments
                    (id,name,environment_name,template_id,status,customer_id,tenant_id,
                     approval_required_for_apply,approval_required_for_delete,created_at,updated_at)
                VALUES ('dep-template-reset','Template reset','production','custom-start-from-scratch','ACTIVE',
                    'cust-internal','tenant-reset',true,true,current_timestamp,current_timestamp)
                """);
            sql.executeUpdate("""
                INSERT INTO platform_deployment_drafts
                    (id,deployment_id,revision_number,status,actions_config_json,entity_config_json,routing_config_json,
                     provider_config_json,security_config_json,prompt_config_json,created_at,updated_at)
                VALUES ('initial','dep-template-reset',1,'PUBLISHED','{}','{}','{}',
                    '{"openaiModel":"initial-model"}','{}','{"instructions":"Initial template"}',current_timestamp,current_timestamp),
                    ('current','dep-template-reset',2,'MODIFIED','{}','{}','{}',
                    '{"openaiModel":"custom-model"}','{}','{"instructions":"Customer edits"}',current_timestamp,current_timestamp)
                """);
        }
        migrate("213");
        try (Connection connection = POSTGRES.createConnection(""); Statement sql = connection.createStatement()) {
            try (var result = sql.executeQuery("""
                SELECT template_defaults_json,approval_required_for_apply,approval_required_for_delete
                FROM platform_deployments WHERE id='dep-template-reset'
                """)) {
                assertThat(result.next()).isTrue();
                var defaults = new ObjectMapper().readTree(result.getString(1));
                assertThat(defaults.path("providerConfig").path("openaiModel").asText()).isEqualTo("initial-model");
                assertThat(defaults.path("promptConfig").path("instructions").asText()).isEqualTo("Initial template");
                assertThat(result.getBoolean(2)).isFalse();
                assertThat(result.getBoolean(3)).isTrue();
            }
            try (var result = sql.executeQuery("SELECT provider_config_json FROM platform_deployment_drafts WHERE id='current'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).contains("custom-model");
            }
        }
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}

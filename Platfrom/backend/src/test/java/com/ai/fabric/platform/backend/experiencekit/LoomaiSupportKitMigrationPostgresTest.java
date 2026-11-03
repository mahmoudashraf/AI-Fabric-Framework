package com.ai.fabric.platform.backend.experiencekit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LoomaiSupportKitMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void v229OnlyAddsTheReviewedInstanceTemplateToTheSupportKitMapping() throws Exception {
        migrate("228");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("""
            INSERT INTO platform_customers(id,name,slug,status,created_at,updated_at)
            VALUES ('owned-customer','Existing owner','owned-customer','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at)
            VALUES ('owned-tenant','owned-customer','Existing tenant','owned-tenant','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_deployments(id,customer_id,tenant_id,name,environment_name,template_id,status,created_at,updated_at)
            VALUES ('owned-deployment','owned-customer','owned-tenant','Existing support','staging',
                'custom-start-from-scratch','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_vectorization_source_connections(id,deployment_id,customer_id,tenant_id,name,
                adapter_type,auth_mode,status,connection_config_json,secret_references_json,discovery_summary_json,created_at,updated_at)
            VALUES ('owned-s3','owned-deployment','owned-customer','owned-tenant','Approved support documents',
                'S3','ACCESS_KEY','ACTIVE','{"bucket":"owned-support","prefix":"approved/"}',
                '{"accessKey":"OWNED_STORAGE_ACCESS","secretKey":"OWNED_STORAGE_SECRET"}','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_marketplace_dataset_documents(id,dataset_handle_id,document_id,entity_type,content_fingerprint,dataset_hash,metadata_json)
            VALUES ('approved-guide','owned-dataset','approved/guide.md','support-document','unchanged-content',
                'unchanged-dataset','{"publicationStatus":"PUBLIC_APPROVED"}')
            """);
        var before = jdbc.queryForList("SELECT * FROM platform_experience_kit_definitions ORDER BY code");
        var preserved = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("platform_vectorization_source_connections", "platform_marketplace_dataset_documents",
            "platform_customers", "platform_customer_tenants", "platform_deployments", "platform_customer_experience_kits",
            "ai_workspace_installations", "platform_marketplace_plugin_versions", "platform_deployment_marketplace_plugin_installs")) {
            preserved.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }

        migrate("229");

        var after = jdbc.queryForList("SELECT * FROM platform_experience_kit_definitions ORDER BY code");
        assertThat(after).hasSameSizeAs(before);
        for (int index = 0; index < before.size(); index++) {
            var expected = new LinkedHashMap<>(before.get(index));
            if ("ai-support-experience".equals(expected.get("code"))) {
                assertThat(new ObjectMapper().readTree((String) after.get(index).get("template_plugin_ids_json")))
                    .isEqualTo(new ObjectMapper().valueToTree(List.of("mkp-template-ai-support-workspace", "mkp-template-loomai-support-workspace")));
                expected.put("template_plugin_ids_json", after.get(index).get("template_plugin_ids_json"));
            }
            assertThat(after.get(index)).isEqualTo(expected);
        }
        preserved.forEach((table, rows) -> assertThat(jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"))
            .as("V229 preserves %s", table).isEqualTo(rows));
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(target).load().migrate();
    }
}

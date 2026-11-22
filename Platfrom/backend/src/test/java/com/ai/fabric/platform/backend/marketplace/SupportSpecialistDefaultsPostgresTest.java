package com.ai.fabric.platform.backend.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SupportSpecialistDefaultsPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void addsReusableAndInternalProfilesWithoutRepinningHistoryOffersOrExistingDeployments() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("244").load().migrate();
        var jdbc = new JdbcTemplate(source); var json = new ObjectMapper();
        // These product templates are normally published through Marketplace after bootstrap.
        for (String id : java.util.List.of("mkp-template-ai-support-workspace", "mkp-template-loomai-support-workspace", "mkp-action-loomai-support-diagnostics")) {
            boolean diagnostic = id.startsWith("mkp-action");
            jdbc.update("INSERT INTO platform_marketplace_plugins (id,slug,display_name,plugin_type,publisher_slug,publisher_display_name,short_description,status,created_at,updated_at) VALUES (?,?,?,?,'loom','Loom AI','Test published product','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id,id,diagnostic?"ACTION":"TEMPLATE");
            String manifest = diagnostic ? "{\"pluginType\":\"ACTION\",\"version\":\"1.0.4\"}" : """
                {"pluginType":"TEMPLATE","version":"1.4.0","contributions":{"template":{
                  "deploymentBehavior":{"type":"CONVERSATIONAL","executionExtensions":[]},
                  "requiredPluginRefs":["mkp-data-ai-support-documents@1.2.0","mkp-action-loomai-support-diagnostics@1.0.3"],
                  "providerConfig":{"preserved":true},"promptConfig":{"systemPrompt":"Approved support policy"}}}}
                """;
            jdbc.update("INSERT INTO platform_marketplace_plugin_versions (id,plugin_id,version,release_channel,status,manifest_json,submitted_by_publisher_id,created_at,published_at) VALUES (?,?,?,'GA','PUBLISHED',?,'mpub-loom',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)","fixture-"+id,id,diagnostic?"1.0.4":"1.4.0",manifest);
        }
        var before = jdbc.queryForList("SELECT id,manifest_json,bundle_sha256 FROM platform_marketplace_plugin_versions ORDER BY id");
        var offers = jdbc.queryForList("SELECT id,definition_json,fingerprint FROM platform_customer_offers ORDER BY id");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        for (var row : before) assertThat(jdbc.queryForMap("SELECT id,manifest_json,bundle_sha256 FROM platform_marketplace_plugin_versions WHERE id=?",row.get("id"))).isEqualTo(row);
        assertThat(jdbc.queryForList("SELECT id,definition_json,fingerprint FROM platform_customer_offers ORDER BY id")).isEqualTo(offers);
        var added = jdbc.queryForList("SELECT plugin_id,manifest_json FROM platform_marketplace_plugin_versions WHERE id LIKE 'mkv-support245-%'");
        assertThat(added).hasSize(3);
        for (var row : added) {
            var manifest = json.readTree(row.get("manifest_json").toString());
            if (manifest.path("pluginType").asText().equals("TEMPLATE")) {
                var specialists = manifest.at("/contributions/template/deploymentBehavior/specialistAssistance");
                assertThat(specialists.path("enabled").asBoolean()).isTrue();
                assertThat(specialists.path("areas")).hasSize(5); assertThat(specialists.path("resolutions")).hasSize(5);
                if (row.get("plugin_id").equals("mkp-template-ai-support-workspace"))
                    for (var area : specialists.path("areas")) assertThat(area.path("readActions")).isEmpty();
            } else assertThat(manifest.path("contributions").path("actions")).hasSize(6);
        }
    }
}

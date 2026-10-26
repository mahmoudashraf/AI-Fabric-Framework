package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.marketplace.entity.*;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class DeskIntegrationMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void migratesAndPublishesDeskWithoutADependencyDatabaseSpecOrChangingHistoricalVersions() throws Exception {
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target("215").load();
        flyway.migrate();
        var mapper = new ObjectMapper();
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("select manifest_json from platform_marketplace_plugin_versions where id='mkv-integration-loomai-desk-v1'")) {
                assertThat(row.next()).isTrue();
                var plugin = new MarketplacePluginEntity(); plugin.setId("mkp-integration-loomai-desk"); plugin.setPluginType("INTEGRATION");
                var version = new MarketplacePluginVersionEntity(); version.setPluginId(plugin.getId()); version.setVersion("1.0.0"); version.setManifestJson(row.getString(1));
                var parsed = new MarketplaceManifestService(mapper).parseAndValidate(plugin, version);
                assertThat(parsed.contributions().conversationChannel().contractVersion()).isEqualTo("LOOMAI_CONVERSATION_CHANNEL_V1");
                assertThat(parsed.contributions().conversationChannel().bindingIdField()).isEqualTo("bindingId");
            }
            try (var rows = statement.executeQuery("select manifest_json from platform_marketplace_plugin_versions where id like 'mkv-desk-%'")) {
                int count = 0;
                while (rows.next()) {
                    count++;
                    var template = mapper.readTree(rows.getString(1)).path("contributions").path("template");
                    assertThat(template.path("requiredPluginRefs").toString()).doesNotContain("mkp-integration-basic-loomai-inbox");
                    assertThat(template.path("requiredPluginInstallDefaults").toString()).doesNotContain("MANAGED_INBOX");
                    assertThat(template.path("deploymentBehavior").path("defaultExecutionExtensions").toString()).doesNotContain("CONVERSATION_HANDOVER");
                }
                assertThat(count).isPositive();
            }
            try (var row = statement.executeQuery("select count(*) from platform_marketplace_plugin_versions where id='mkv-integration-basic-loomai-inbox-v1-3'")) {
                row.next(); assertThat(row.getInt(1)).isEqualTo(1);
            }
            try (var row = statement.executeQuery("select count(*) from deployment_managed_dependency_specs where template_version_id like 'mkv-desk-%'")) {
                row.next(); assertThat(row.getInt(1)).isZero();
            }
        }
    }
}

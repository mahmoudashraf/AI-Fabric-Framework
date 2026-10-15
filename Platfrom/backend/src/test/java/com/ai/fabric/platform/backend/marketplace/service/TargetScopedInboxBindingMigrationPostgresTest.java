package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class TargetScopedInboxBindingMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketplaceManifestService manifests = new MarketplaceManifestService(mapper);

    @Test
    void publishesTargetScopedInboxAndDealershipTemplateVersions() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("176"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            Seed inbox = seed(connection, "mkv-integration-basic-loomai-inbox-v1-1");
            Seed template = seed(connection, "mkv-template-simulated-dealership-experience-v1-1");

            var parsed = manifests.parseAndValidate(plugin(inbox), version(inbox));
            assertThat(parsed.contributions().conversationChannel().bindingId()).isBlank();
            assertThat(parsed.contributions().conversationChannel().bindingIdField()).isEqualTo("bindingId");

            JsonNode templateManifest = mapper.readTree(template.manifestJson());
            JsonNode contribution = templateManifest.path("contributions").path("template");
            assertThat(contribution.path("requiredPluginRefs"))
                .extracting(JsonNode::asText)
                .contains("mkp-integration-basic-loomai-inbox@1.1.0")
                .doesNotContain("mkp-integration-basic-loomai-inbox@1.0.0");
            assertThat(contribution.path("requiredPluginInstallDefaults")
                .path("mkp-integration-basic-loomai-inbox@1.1.0")
                .path("config").path("bindingId").asText())
                .isEqualTo("${MANAGED_INBOX_BINDING_ID}");
        }
    }

    private Seed seed(Connection connection, String versionId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            select p.id, p.plugin_type, v.id, v.version, v.manifest_json
              from platform_marketplace_plugin_versions v
              join platform_marketplace_plugins p on p.id = v.plugin_id
             where v.id = ?
            """)) {
            statement.setString(1, versionId);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                return new Seed(row.getString(1), row.getString(2), row.getString(3),
                    row.getString(4), row.getString(5));
            }
        }
    }

    private MarketplacePluginEntity plugin(Seed seed) {
        var plugin = new MarketplacePluginEntity();
        plugin.setId(seed.pluginId());
        plugin.setDisplayName(seed.pluginId());
        plugin.setPluginType(seed.pluginType());
        return plugin;
    }

    private MarketplacePluginVersionEntity version(Seed seed) {
        var version = new MarketplacePluginVersionEntity();
        version.setId(seed.versionId());
        version.setPluginId(seed.pluginId());
        version.setVersion(seed.version());
        version.setManifestJson(seed.manifestJson());
        return version;
    }

    private record Seed(String pluginId, String pluginType, String versionId,
                        String version, String manifestJson) { }
}

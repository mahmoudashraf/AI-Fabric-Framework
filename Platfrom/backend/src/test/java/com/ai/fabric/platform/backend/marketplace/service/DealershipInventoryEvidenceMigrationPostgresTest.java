package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DealershipInventoryEvidenceMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishesEvidenceRevisionsWithoutChangingExistingAuthorityOrHistory() throws Exception {
        migrate("238");
        var before = rows("platform_marketplace_plugin_versions");
        Map<String, Map<String, String>> untouched = new LinkedHashMap<>();
        for (String table : List.of("platform_marketplace_plugins", "platform_deployments", "platform_deployment_versions",
            "platform_customer_offers", "deployment_external_integration_bindings", "deployment_managed_dependency_specs")) {
            untouched.put(table, rows(table));
        }
        migrate("239");
        var after = rows("platform_marketplace_plugin_versions");
        assertThat(after).hasSize(before.size() + 7).containsAllEntriesOf(before);
        for (var entry : untouched.entrySet()) assertThat(rows(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());

        int checked = 0;
        for (var entry : after.entrySet()) {
            if (before.containsKey(entry.getKey())) continue;
            JsonNode row = mapper.readTree(entry.getValue());
            JsonNode manifest = mapper.readTree(row.path("manifest_json").asText());
            var plugin = new MarketplacePluginEntity();
            plugin.setId(row.path("plugin_id").asText());
            plugin.setPluginType(manifest.path("pluginType").asText());
            plugin.setDisplayName(manifest.path("displayName").asText());
            var version = new MarketplacePluginVersionEntity();
            version.setId(entry.getKey()); version.setPluginId(plugin.getId());
            version.setVersion(row.path("version").asText()); version.setStatus("PUBLISHED");
            version.setManifestJson(manifest.toString());
            new MarketplaceManifestService(mapper).parseAndValidate(plugin, version);
            assertThat(manifest.toString()).doesNotContain("batteryRangeTestBasis", "WLTP", "NEDC", "DemoInventorySeeder");
            assertThat(row.path("review_notes").asText()).contains("Optional facts remain optional");
            if (plugin.getPluginType().equals("DATA")) {
                for (JsonNode dataset : manifest.at("/contributions/datasets")) {
                    JsonNode mapping = dataset.at("/syncConnector/httpSource/mapping");
                    for (String section : List.of("contentFields", "entityFields")) {
                        assertThat(mapping.path(section).path("electricRangeMiles").asText()).isEqualTo("/vehicle/batteryRangeMiles");
                        assertThat(mapping.path(section).path("seats").asText()).isEqualTo("/vehicle/seats");
                        assertThat(mapping.path(section).path("location").asText()).isEqualTo("/advertiser/location/town");
                        assertThat(mapping.path(section).path("sourceUpdatedAt").asText()).isEqualTo("/metadata/lastUpdated");
                    }
                    assertThat(dataset.at("/syncConnector/httpSource/sourceId").asText()).isNotBlank();
                }
            } else if (plugin.getPluginType().equals("ACTION")) {
                for (JsonNode action : manifest.at("/contributions/actions")) {
                    assertThat(action.path("readOnly").asBoolean()).isTrue();
                    assertThat(action.at("/route/sourceProjection/requireSuccessfulSync").asBoolean()).isTrue();
                    assertThat(action.at("/route/sourceProjection/outputFields")).extracting(JsonNode::asText)
                        .contains("features", "electricRangeMiles", "seats", "location", "reservationStatus", "sourceUpdatedAt");
                    for (String kind : List.of("lists", "objects")) for (JsonNode fact : action.at("/llmFacts/" + kind)) {
                        assertThat(fact.path("includeFields")).extracting(JsonNode::asText)
                            .contains("features", "electricRangeMiles", "seats", "location", "sourceUpdatedAt");
                    }
                    if (action.at("/route/response/pinnedTargetsFromCollection").isObject()) {
                        assertThat(action.at("/route/response/pinnedTargetsFromCollection/contentFields/location").asText()).isEqualTo("/location");
                    }
                }
            } else {
                assertThat(plugin.getPluginType()).isEqualTo("TEMPLATE");
                assertThat(manifest.at("/contributions/template/requiredPluginRefs")).extracting(JsonNode::asText)
                    .doesNotContain("mkp-data-dealership-external-stock-v1@1.0.0", "mkp-data-simulated-dealership-stock-v1@1.1.0");
            }
            checked++;
        }
        assertThat(checked).isEqualTo(7);
        migrate("239");
        assertThat(rows("platform_marketplace_plugin_versions")).isEqualTo(after);
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(target)).load().migrate();
    }

    private Map<String, String> rows(String table) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery("select id, row_to_json(t)::text from " + table + " t order by id")) {
            while (rows.next()) result.put(rows.getString(1), rows.getString(2));
        }
        return result;
    }
}

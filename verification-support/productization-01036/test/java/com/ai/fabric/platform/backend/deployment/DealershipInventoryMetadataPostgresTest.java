package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DealershipInventoryMetadataPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String OLD = "mkv-action-dealership-external-discovery-v1-0";
    static final String REVISED = "mkv-action-dealership-external-discovery-v1-1";
    static final List<String> BODY_TYPES = List.of("SUV", "Estate", "Hatchback", "Saloon", "Crossover");
    static String history;
    static String plugins;

    @BeforeAll static void migrate() throws Exception {
        migrateTo("207");
        history = text("select json_agg(row_to_json(v) order by id)::text from platform_marketplace_plugin_versions v");
        plugins = text("select json_agg(row_to_json(p) order by id)::text from platform_marketplace_plugins p");
        migrateTo("208");
    }

    @Test void newVersionPreservesHistoryAndRequiresExplicitTemplateAdoption() throws Exception {
        assertThat(text("select json_agg(row_to_json(v) order by id)::text from platform_marketplace_plugin_versions v where id <> '" + REVISED + "'"))
            .isEqualTo(history);
        assertThat(text("select json_agg(row_to_json(p) order by id)::text from platform_marketplace_plugins p"))
            .isEqualTo(plugins);
        assertThat(text("select version from platform_marketplace_plugin_versions where id='" + REVISED + "'"))
            .isEqualTo("1.1.0");
        assertThat(text("select bundle_sha256 = encode(sha256(convert_to(manifest_json::jsonb::text, 'UTF8')), 'hex') from platform_marketplace_plugin_versions where id='" + REVISED + "'"))
            .isEqualTo("t");
        for (String template : List.of("mkv-template-dealership-external-systems-v1-0",
            "mkv-template-dealership-external-systems-managed-inbox-v1-0")) {
            assertThat(manifest(template).at("/contributions/template/requiredPluginRefs"))
                .extracting(JsonNode::asText)
                .contains("mkp-action-dealership-external-discovery-v1@1.0.0")
                .doesNotContain("mkp-action-dealership-external-discovery-v1@1.1.0");
        }
    }

    @Test void onlySearchDescriptionsAndBodyEnumChangeAndProductionManifestStillParses() throws Exception {
        var revised = manifest(REVISED);
        var expected = (ObjectNode) manifest(OLD).deepCopy();
        expected.put("version", "1.1.0");
        var expectedSearch = (ObjectNode) search(expected);
        var actualSearch = search(revised);
        expectedSearch.set("description", actualSearch.path("description"));
        for (String name : List.of("make", "fuelType", "bodyType")) {
            ((ObjectNode) param(expectedSearch, name)).set("description", param(actualSearch, name).path("description"));
        }
        ((ObjectNode) param(expectedSearch, "bodyType")).set("allowedValues", MAPPER.valueToTree(BODY_TYPES));
        assertThat(revised).isEqualTo(expected);

        var plugin = new MarketplacePluginEntity();
        plugin.setId(revised.path("pluginId").asText());
        plugin.setPluginType("ACTION");
        var version = new MarketplacePluginVersionEntity();
        version.setId(REVISED);
        version.setPluginId(plugin.getId());
        version.setVersion("1.1.0");
        version.setManifestJson(revised.toString());
        assertThat(new MarketplaceManifestService(MAPPER).parseAndValidate(plugin, version).manifest())
            .isEqualTo(revised);

        var compiler = new DeploymentMarketplaceDraftCompilerService(
            null, null, null, null, null, null, null, null, null, null, null, MAPPER, null);
        var install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("metadata-contract-test");
        install.setConfigJson("{}");
        install.setSecretRefsJson("{}");
        ObjectNode compiled = ReflectionTestUtils.invokeMethod(compiler, "compileActionContribution",
            actualSearch, install, plugin, version);
        assertThat(compiled).isNotNull();
        assertThat(compiled.path("name").asText()).isEqualTo("dealership_search_inventory");
        assertThat(compiled.path("params")).isEqualTo(actualSearch.path("params"));
        assertThat(compiled.path("description")).isEqualTo(actualSearch.path("description"));
        assertThat(compiled.at("/route/source-projection/filters/2/operator").asText()).isEqualTo("EQUALS_IGNORE_CASE");
        assertThat(compiled.at("/route/source-projection/source-ref").asText()).isEqualTo("dealership-external-stock-source");
    }

    @Test void categoricalSchemaAndGuidanceDoNotTurnFamilySuitabilityIntoABodyFilter() throws Exception {
        var action = search(manifest(REVISED));
        var body = param(action, "bodyType");
        assertThat(body.path("required").asBoolean()).isFalse();
        assertThat(body.path("type").asText()).isEqualTo("STRING");
        assertThat(body.path("allowedValues")).extracting(JsonNode::asText).containsExactlyElementsOf(BODY_TYPES);
        assertThat(body.path("allowedValues")).extracting(JsonNode::asText)
            .doesNotContain("family", "Family", "family-friendly", "practical", "spacious", "commuting", "");
        assertThat(body.path("description").asText()).contains("Omit when no supported body style was stated",
            "never infer SUV", "not body styles", "ask for clarification", "bound external stock provider");
        assertThat(action.path("description").asText()).contains("only explicit constraints",
            "assess them only from returned vehicle facts", "disclose missing evidence",
            "Never invent a body style or silently relax an explicit filter",
            "use fuelType Electric and maxPriceGbp 40000; omit bodyType and make unless explicitly stated");
        assertThat(param(action, "make").path("description").asText())
            .contains("Omit when no manufacturer was stated", "do not send an empty string");
        assertThat(param(action, "fuelType").path("allowedValues")).extracting(JsonNode::asText)
            .containsExactly("Electric", "Petrol", "Diesel", "Hybrid", "Plug-in hybrid");
        assertThat(param(action, "maxPriceGbp").path("min").asInt()).isEqualTo(1);
        assertThat(param(action, "limit").path("max").asInt()).isEqualTo(24);
        assertThat(action.at("/route/sourceProjection/filters/2/operator").asText()).isEqualTo("EQUALS_IGNORE_CASE");
        assertThat(action.at("/route/sourceProjection/requireSuccessfulSync").asBoolean()).isTrue();
        assertThat(action.at("/route/sourceProjection/maxStalenessSeconds").asInt()).isEqualTo(1800);
    }

    @Test void unversionedDemoActionUsesTheSameCategoricalContract() throws Exception {
        var yaml = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(
            Path.of(System.getProperty("repoRoot"),
                "product-demos/autotrader-dealership-demo/backend/deployment/runtime/ai-actions.yml")));
        var demo = yaml.path("actions").get(0);
        var action = search(manifest(REVISED));
        assertThat(demo.path("name").asText()).isEqualTo("dealership_search_inventory");
        assertThat(demo.path("description")).isEqualTo(action.path("description"));
        for (String name : List.of("make", "fuelType", "bodyType")) {
            assertThat(param(demo, name)).isEqualTo(param(action, name));
        }
    }

    static JsonNode search(JsonNode manifest) {
        for (JsonNode action : manifest.at("/contributions/actions")) {
            if ("dealership_search_inventory".equals(action.path("actionId").asText())) return action;
        }
        throw new AssertionError("Missing inventory action");
    }

    static JsonNode param(JsonNode action, String name) {
        for (JsonNode param : action.path("params")) {
            if (name.equals(param.path("name").asText())) return param;
        }
        throw new AssertionError("Missing parameter: " + name);
    }

    static JsonNode manifest(String id) throws Exception {
        return MAPPER.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='" + id + "'"));
    }

    static void migrateTo(String version) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate();
    }

    static String text(String query) throws Exception {
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}

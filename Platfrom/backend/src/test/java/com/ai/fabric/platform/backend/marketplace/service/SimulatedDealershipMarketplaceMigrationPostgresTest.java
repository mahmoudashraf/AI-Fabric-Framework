package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SimulatedDealershipMarketplaceMigrationPostgresTest {

    private static final String DATA = "mkp-data-simulated-dealership-stock-v1";
    private static final String DISCOVERY = "mkp-action-simulated-dealership-discovery-v1";
    private static final String ENGAGEMENT = "mkp-action-dealership-engagement-v1";
    private static final String TEMPLATE = "mkp-template-simulated-dealership-experience-v1";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketplaceManifestService manifests = new MarketplaceManifestService(mapper);
    private final DeploymentMarketplaceDraftCompilerService compiler =
        new DeploymentMarketplaceDraftCompilerService(
            null, null, null, null, null, null, null, null, null, null, null, mapper
        );

    @Test
    void publishesHonestManagedSimulatedPackageAndKeepsRealProviderDraftsReserved() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("174"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            Map<String, Seed> seeds = readSeeds(statement);
            assertThat(seeds.keySet()).containsExactlyInAnyOrder(DATA, DISCOVERY, ENGAGEMENT, TEMPLATE);
            seeds.values().forEach(seed -> assertThat(parse(seed).pluginType()).isEqualTo(seed.pluginType()));

            JsonNode data = mapper.readTree(seeds.get(DATA).manifestJson());
            JsonNode profile = data.path("contributions").path("datasets").path(0)
                .path("syncConnector").path("connectionProfile");
            assertThat(profile.path("managedBaseUrlField").asText()).isEqualTo("providerBaseUrl");
            assertThat(profile.path("managedAllowedHostField").asText()).isEqualTo("providerHost");
            assertThat(profile.path("auth").path("managedTokenBaseUrlField").asText())
                .isEqualTo("providerBaseUrl");
            assertThat(data.path("description").asText())
                .contains("not an Auto Trader service or endorsement");

            JsonNode discovery = mapper.readTree(seeds.get(DISCOVERY).manifestJson());
            assertThat(discovery.path("contributions").path("actions"))
                .extracting(action -> action.path("actionId").asText())
                .containsExactly(
                    "dealership_search_inventory",
                    "dealership_get_vehicle",
                    "dealership_compare_vehicles"
                );
            for (JsonNode action : discovery.path("contributions").path("actions")) {
                assertThat(action.path("route").path("sourceProjection").isObject()).isTrue();
                assertThat(action.path("route").path("authz").path("enabled").asBoolean()).isFalse();
            }
            assertCompiledDiscoveryRoutes(seeds.get(DISCOVERY), discovery);

            JsonNode engagement = mapper.readTree(seeds.get(ENGAGEMENT).manifestJson());
            for (JsonNode action : engagement.path("contributions").path("actions")) {
                assertThat(action.path("requiresConfirmation").asBoolean()).isTrue();
                assertThat(action.path("route").path("managedUrlField").asText())
                    .isEqualTo("receiverActionUrl");
                assertThat(action.path("route").path("managedHeaderSecretRefFields")
                    .path("X-DEALERSHIP-INTERNAL-KEY").asText()).isEqualTo("receiverApiKey");
            }
            assertCompiledEngagementRoutes(seeds.get(ENGAGEMENT), engagement);

            JsonNode template = mapper.readTree(seeds.get(TEMPLATE).manifestJson())
                .path("contributions").path("template");
            assertThat(template.path("requiredPluginRefs"))
                .extracting(JsonNode::asText)
                .contains(
                    DATA + "@1.0.0",
                    DISCOVERY + "@1.0.0",
                    ENGAGEMENT + "@1.0.0",
                    "mkp-data-dealership-knowledge-v1@1.0.0",
                    "mkp-action-conversation-handover@1.0.0",
                    "mkp-integration-basic-loomai-inbox@1.0.0"
                );
            JsonNode defaults = template.path("requiredPluginInstallDefaults");
            assertThat(defaults.path(DATA + "@1.0.0").path("config").path("providerBaseUrl").asText())
                .isEqualTo("${MANAGED_SIMULATOR_BASE_URL}");
            assertThat(defaults.path(ENGAGEMENT + "@1.0.0").path("config").path("receiverActionUrl").asText())
                .isEqualTo("${MANAGED_RECEIVER_ACTION_URL}");
            assertThat(defaults.path("mkp-data-dealership-knowledge-v1@1.0.0")
                .path("config").has("documentStorageBindingRef")).isFalse();

            assertThat(countVersions(statement, "mkp-data-autotrader-dealership-stock-v1")).isZero();
            assertThat(countVersions(statement, "mkp-action-autotrader-dealership-discovery-v1")).isZero();
            assertThat(countVersions(statement, "mkp-template-autotrader-dealership-concierge-v1")).isZero();
        }
    }

    private void assertCompiledDiscoveryRoutes(Seed seed, JsonNode manifest) {
        MarketplacePluginEntity plugin = plugin(seed);
        MarketplacePluginVersionEntity version = version(seed);
        DeploymentMarketplacePluginInstallEntity install = install(seed, "{}", "{}");
        for (JsonNode action : manifest.path("contributions").path("actions")) {
            ObjectNode compiled = compiler.compileActionContribution(action, install, plugin, version);
            assertThat(compiled.path("route").has("sourceProjection")).isFalse();
            assertThat(compiled.path("route").path("source-projection").isObject()).isTrue();
            assertThat(compiled.path("route").path("authz").path("enabled").asBoolean()).isFalse();
        }
    }

    private void assertCompiledEngagementRoutes(Seed seed, JsonNode manifest) {
        MarketplacePluginEntity plugin = plugin(seed);
        MarketplacePluginVersionEntity version = version(seed);
        DeploymentMarketplacePluginInstallEntity install = install(
            seed,
            "{\"receiverActionUrl\":\"${MANAGED_RECEIVER_ACTION_URL}\"}",
            "{\"receiverApiKey\":\"MANAGED_RECEIVER_API_KEY\"}"
        );
        for (JsonNode action : manifest.path("contributions").path("actions")) {
            ObjectNode compiled = compiler.compileActionContribution(action, install, plugin, version);
            JsonNode route = compiled.path("route");
            assertThat(route.path("url").asText()).isEqualTo("${MANAGED_RECEIVER_ACTION_URL}");
            assertThat(route.path("headers").path("X-DEALERSHIP-INTERNAL-KEY").asText())
                .isEqualTo("${MANAGED_RECEIVER_API_KEY}");
            assertThat(route.path("response").has("successHttpStatus")).isFalse();
            assertThat(route.path("response").path("success-http-status").path(0).asInt()).isEqualTo(200);
        }
    }

    private MarketplaceManifestService.ParsedMarketplaceManifest parse(Seed seed) {
        return manifests.parseAndValidate(plugin(seed), version(seed));
    }

    private MarketplacePluginEntity plugin(Seed seed) {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId(seed.pluginId());
        plugin.setDisplayName(seed.pluginId());
        plugin.setPluginType(seed.pluginType());
        return plugin;
    }

    private MarketplacePluginVersionEntity version(Seed seed) {
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId(seed.versionId());
        version.setPluginId(seed.pluginId());
        version.setVersion(seed.version());
        version.setManifestJson(seed.manifestJson());
        return version;
    }

    private DeploymentMarketplacePluginInstallEntity install(Seed seed, String configJson, String secretRefsJson) {
        DeploymentMarketplacePluginInstallEntity install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("mpi-" + seed.pluginId());
        install.setDeploymentId("dep-simulated-dealership-test");
        install.setPluginId(seed.pluginId());
        install.setPluginVersionId(seed.versionId());
        install.setStatus("ENABLED");
        install.setConfigJson(configJson);
        install.setSecretRefsJson(secretRefsJson);
        return install;
    }

    private Map<String, Seed> readSeeds(Statement statement) throws Exception {
        Map<String, Seed> result = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery("""
            select p.id as plugin_id, p.plugin_type, v.id as version_id, v.version, v.manifest_json
              from platform_marketplace_plugins p
              join platform_marketplace_plugin_versions v on v.plugin_id = p.id
             where p.id in (
               'mkp-data-simulated-dealership-stock-v1',
               'mkp-action-simulated-dealership-discovery-v1',
               'mkp-action-dealership-engagement-v1',
               'mkp-template-simulated-dealership-experience-v1'
             )
               and p.status = 'ACTIVE'
               and v.status = 'PUBLISHED'
             order by p.id
            """)) {
            while (rows.next()) {
                Seed seed = new Seed(
                    rows.getString("plugin_id"),
                    rows.getString("plugin_type"),
                    rows.getString("version_id"),
                    rows.getString("version"),
                    rows.getString("manifest_json")
                );
                result.put(seed.pluginId(), seed);
            }
        }
        return result;
    }

    private int countVersions(Statement statement, String pluginId) throws Exception {
        try (ResultSet rows = statement.executeQuery(
            "select count(*) from platform_marketplace_plugin_versions where plugin_id = '" + pluginId + "'"
        )) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private record Seed(String pluginId, String pluginType, String versionId,
                        String version, String manifestJson) { }
}

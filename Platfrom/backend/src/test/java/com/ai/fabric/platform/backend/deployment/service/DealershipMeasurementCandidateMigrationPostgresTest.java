package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class DealershipMeasurementCandidateMigrationPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String OLD_TEMPLATE = "mkv-template-simulated-dealership-experience-v1-5";
    private static final String NEW_TEMPLATE = "mkv-template-simulated-dealership-experience-v1-6";
    private static final Set<String> TARGETS = Set.of("dtp-coolify-staging-behavior", "dtp-coolify-production-behavior");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void migrationPreservesPublishedHistoryAndChangesOnlyCandidateCapAndTwoTargetCpuFields() throws Exception {
        migrate("190");
        Map<String, JsonNode> before = profiles();
        String oldRow = text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='" + OLD_TEMPLATE + "'");
        JsonNode oldManifest = mapper.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='" + OLD_TEMPLATE + "'"));
        migrate("191");

        assertThat(text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='" + OLD_TEMPLATE + "'"))
            .isEqualTo(oldRow);
        JsonNode candidate = mapper.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='" + NEW_TEMPLATE + "'"));
        ObjectNode expected = oldManifest.deepCopy();
        expected.put("version", "1.6.0");
        ((ObjectNode) expected.path("contributions").path("template").path("providerConfig")).put("openaiMaxTokens", 2048);
        assertThat(candidate).isEqualTo(expected);
        JsonNode config = candidate.path("contributions").path("template").path("providerConfig");
        assertThat(config.path("openaiMaxTokens").isIntegralNumber()).isTrue();
        assertThat(ManagedDeploymentProfileCatalog.openAiMaxTokens(config)).isEqualTo(2048);
        assertThat(ManagedDeploymentProfileCatalog.openAiModel(config)).isEqualTo("gpt-4o-mini");
        assertThat(ManagedDeploymentProfileCatalog.openAiEmbeddingModel(config)).isEqualTo("text-embedding-3-small");
        assertThat(ManagedDeploymentProfileCatalog.providerEnableFallback(config)).isFalse();
        assertCompiledCap(candidate);
        assertThat(text("select review_notes from platform_marketplace_plugin_versions where id='" + NEW_TEMPLATE + "'"))
            .contains("not measured or approved for production");

        Map<String, JsonNode> after = profiles();
        assertThat(after.keySet()).isEqualTo(before.keySet());
        assertThat(before.keySet()).containsAll(TARGETS);
        for (var entry : before.entrySet()) {
            ObjectNode wanted = entry.getValue().deepCopy();
            if (TARGETS.contains(entry.getKey())) {
                assertThat(wanted.path("connectorLimitsCpus").asText()).isEqualTo("0.5");
                assertThat(wanted.path("vectorizationRunnerLimitsCpus").asText()).isEqualTo("0.5");
                wanted.put("connectorLimitsCpus", "0.25");
                wanted.put("vectorizationRunnerLimitsCpus", "0.25");
            }
            assertThat(after.get(entry.getKey())).as(entry.getKey()).isEqualTo(wanted);
        }
        assertThat(text("select count(*)::text from provider_usage_grants")).isEqualTo("0");
        assertThat(text("select count(*)::text from provider_usage_allocations")).isEqualTo("0");
    }

    @Test
    void genericCapacityReadbackEnforcesQuarterCoreReceiverAndDatabaseWithoutReducingMemory() {
        var appSettings = new CoolifyApplicationRuntimeSettings(null, null, null, null,
            "768M", null, null, "0.250", null);
        var appRaw = mapper.createObjectNode().put("limits_memory", "768M").put("limits_cpus", "0.25");
        var app = new CoolifyApplicationSummary("app", "receiver", "https://receiver.example", "running:healthy",
            "image", "tag", appRaw);
        CoolifyApplicationCapacity.requireReadback(app, appSettings, "CANDIDATE_CAPACITY_DRIFT");
        appRaw.put("limits_cpus", "0.5");
        assertThatThrownBy(() -> CoolifyApplicationCapacity.requireReadback(app, appSettings, "CANDIDATE_CAPACITY_DRIFT"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("CANDIDATE_CAPACITY_DRIFT");

        var databaseSettings = CoolifyDatabaseCapacity.of(250, 1024);
        assertThat(databaseSettings.limitsCpus()).isEqualTo("0.250");
        assertThat(CoolifyDatabaseCapacity.cpuMillicores(databaseSettings)).isEqualTo(250);
        assertThat(CoolifyDatabaseCapacity.memoryBytes(databaseSettings)).isEqualTo(1024L * 1024 * 1024);
        var databaseRaw = mapper.createObjectNode().put("limits_memory", "1024M").put("limits_cpus", "0.25");
        var database = new CoolifyDatabaseSummary("db", "receiver-db", "running", "postgresql", "user", "database", databaseRaw);
        CoolifyDatabaseCapacity.requireReadback(database, databaseSettings, "CANDIDATE_DATABASE_DRIFT");
        databaseRaw.put("limits_cpus", "0.5");
        assertThatThrownBy(() -> CoolifyDatabaseCapacity.requireReadback(database, databaseSettings, "CANDIDATE_DATABASE_DRIFT"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("CANDIDATE_DATABASE_DRIFT");
    }

    private void migrate(String version) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate();
    }

    private void assertCompiledCap(JsonNode candidate) {
        var plugin = new MarketplacePluginEntity();
        plugin.setId("mkp-template-simulated-dealership-experience-v1");
        plugin.setDisplayName("Dealership staging measurement candidate");
        plugin.setPluginType("TEMPLATE");
        var version = new MarketplacePluginVersionEntity();
        version.setId(NEW_TEMPLATE);
        version.setPluginId(plugin.getId());
        version.setVersion("1.6.0");
        version.setStatus("PUBLISHED");
        version.setManifestJson(candidate.toString());
        var manifests = new MarketplaceManifestService(mapper);
        JsonNode parsed = manifests.parseAndValidate(plugin, version).manifest();
        assertThat(parsed.path("contributions").path("template").path("providerConfig")
            .path("openaiMaxTokens").asInt()).isEqualTo(2048);
        var compiler = new DeploymentMarketplaceDraftCompilerService(
            null, null, null, null, null, null, manifests, null, null, null, null, mapper);
        ObjectNode baseline = mapper.createObjectNode().put("openaiModel", "gpt-4o-mini")
            .put("openaiEmbeddingModel", "text-embedding-3-small").put("enableFallback", false)
            .put("openaiMaxTokens", 1000);
        JsonNode before = baseline.deepCopy();
        JsonNode compiled = compiler.compileTemplateProviderBaseline(plugin, version, baseline);
        assertThat(compiled.path("openaiMaxTokens").isIntegralNumber()).isTrue();
        assertThat(ManagedDeploymentProfileCatalog.openAiMaxTokens(compiled)).isEqualTo(2048);
        assertThat(ManagedDeploymentProfileCatalog.openAiModel(compiled)).isEqualTo("gpt-4o-mini");
        assertThat(ManagedDeploymentProfileCatalog.openAiEmbeddingModel(compiled)).isEqualTo("text-embedding-3-small");
        assertThat(ManagedDeploymentProfileCatalog.providerEnableFallback(compiled)).isFalse();
        assertThat(baseline).isEqualTo(before);
    }

    private Map<String, JsonNode> profiles() throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select id, resource_defaults_json from deployment_target_profiles order by id")) {
            while (rows.next()) result.put(rows.getString(1), mapper.readTree(rows.getString(2)));
        }
        return result;
    }

    private String text(String sql) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            String result = rows.getString(1);
            assertThat(rows.next()).isFalse();
            return result;
        }
    }
}

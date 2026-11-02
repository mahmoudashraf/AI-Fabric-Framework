package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.service.DeploymentAiStateStorageSelection;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ClientDealershipTemplateMigrationPostgresTest {
    private static final String PLUGIN = "mkp-template-dealership-external-systems-v1";
    private static final String VERSION = "mkv-template-dealership-external-systems-v1-2";
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishesOneNormalRevisionWithoutChangingHistoryOrGrantingResources() throws Exception {
        migrate("220");
        var plugins = rows("platform_marketplace_plugins");
        var versions = rows("platform_marketplace_plugin_versions");
        var targets = rows("deployment_target_profiles");
        var dependencies = rows("deployment_managed_dependency_specs");
        migrate("221");
        var after = rows("platform_marketplace_plugin_versions");
        assertThat(after).hasSize(versions.size() + 1).containsAllEntriesOf(versions);
        assertThat(rows("platform_marketplace_plugins")).isEqualTo(plugins);
        assertThat(rows("deployment_target_profiles")).isEqualTo(targets);
        assertThat(rows("deployment_managed_dependency_specs")).isEqualTo(dependencies);

        JsonNode row = mapper.readTree(after.get(VERSION));
        assertThat(row.path("plugin_id").asText()).isEqualTo(PLUGIN);
        assertThat(row.path("version").asText()).isEqualTo("1.2.0");
        assertThat(row.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(row.path("review_notes").asText()).contains("Hosted provider", "not upgrade restrictions");
        JsonNode manifest = mapper.readTree(row.path("manifest_json").asText());
        var plugin = new MarketplacePluginEntity();
        plugin.setId(PLUGIN); plugin.setPluginType("TEMPLATE"); plugin.setDisplayName("External dealership");
        var version = new MarketplacePluginVersionEntity();
        version.setId(VERSION); version.setPluginId(PLUGIN); version.setVersion("1.2.0");
        version.setStatus("PUBLISHED"); version.setManifestJson(manifest.toString());
        var manifests = new MarketplaceManifestService(mapper);
        var parsed = manifests.parseAndValidate(plugin, version);
        assertThat(parsed.contributions().templateRequiredPluginRefs()).containsExactly(
            "mkp-data-dealership-external-stock-v1@1.0.0",
            "mkp-action-dealership-external-discovery-v1@1.1.0",
            "mkp-data-dealership-knowledge-v1@1.0.0");
        JsonNode template = manifest.at("/contributions/template");
        assertThat(template.at("/deploymentBehavior/defaultExecutionExtensions")).isEmpty();
        assertThat(template.at("/deploymentBehavior/allowedExecutionExtensions"))
            .extracting(JsonNode::asText).containsExactly("CONVERSATION_HANDOVER");
        assertThat(template.path("recommendedPluginIds")).extracting(JsonNode::asText)
            .containsExactly("mkp-integration-loomai-desk");
        assertThat(template.path("requiredPluginInstallDefaults").size()).isEqualTo(1);
        assertThat(template.at("/requiredPluginInstallDefaults/mkp-data-dealership-knowledge-v1@1.0.0/config/publicContentApprovalConfirmed").asBoolean())
            .isFalse();
        assertThat(manifest.toString()).doesNotContain("MANAGED_SIMULATOR", "MANAGED_RECEIVER", "MANAGED_INBOX",
            "receiverActionUrl", "dealership_request_callback", "dealership_request_test_drive", "basic-loomai-inbox",
            "aiFabricFrameworkVersion", "sourceArtifactId", "specialistAssistance");

        var compiler = new DeploymentMarketplaceDraftCompilerService(
            null, null, null, null, null, null, manifests, null, null, null, null, mapper, null);
        var base = mapper.createObjectNode().put("vectorStrategy", "lucene").put("ownerSetting", "retained");
        var provider = compiler.compileTemplateProviderBaseline(plugin, version, base);
        assertThat(base.toString()).isEqualTo("{\"vectorStrategy\":\"lucene\",\"ownerSetting\":\"retained\"}");
        assertThat(provider.path("ownerSetting").asText()).isEqualTo("retained");
        // SQL mode belongs to the existing typed customer bootstrap setting, not the template manifest.
        assertThat(DeploymentAiStateStorageSelection.selected(provider)).isNull();
        assertThat(ManagedDeploymentProfileCatalog.milvusExternalExisting(provider)).isTrue();
        assertThat(provider.path("milvusExternalBindingRequired").asBoolean()).isTrue();
        assertThat(provider.path("milvusAuthenticationType").asText()).isEqualTo("TOKEN");
        assertThat(ManagedDeploymentProfileCatalog.dedicatedEmbeddingServiceRequested(provider)).isFalse();
        assertThat(ManagedDeploymentProfileCatalog.openAiModel(provider)).isEqualTo("gpt-5.4-mini");
        assertThat(provider.at("/orchestrationModeOverrides/executor/readActionResolution/maxPlannerContextChars").asInt()).isEqualTo(12000);
        assertThat(provider.at("/orchestrationModeOverrides/executor/rag/maxSpaces").asInt()).isEqualTo(2);
        assertThat(provider.has("milvusHost")).isFalse();
        assertThat(provider.has("zillizCloudProjectId")).isFalse();
        var prompts = compiler.compileTemplatePromptBaseline(plugin, version, mapper.createObjectNode());
        assertThat(prompts.has("ragSimilarityThreshold")).isFalse();
        assertThat(prompts.path("ragMaxDocumentsUsedForContext").asInt()).isEqualTo(8);
        assertThat(prompts.path("ragMaxContextChars").asInt()).isEqualTo(8000);
        for (String table : java.util.List.of("platform_deployments", "platform_customer_offers",
            "deployment_provider_resource_handles", "deployment_external_integration_bindings",
            "deployment_managed_dependency_graphs", "platform_deployment_releases")) {
            assertThat(rows(table)).as(table).isEmpty();
        }
        migrate("221");
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

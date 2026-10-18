package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ProductizedDealershipProtectedActionMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketplaceManifestService manifests = new MarketplaceManifestService(mapper);

    @Test
    void publishesProtectedTestDriveDurableDealershipTemplateAndDisablesProductionHostedCustomerStaging()
        throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("186"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            Seed engagement = seed(connection, "mkv-action-dealership-engagement-v1-1");
            manifests.parseAndValidate(plugin(engagement), version(engagement));
            JsonNode actions = mapper.readTree(engagement.manifestJson())
                .path("contributions").path("actions");
            assertThat(action(actions, "dealership_request_callback").path("anonymousAllowed").asBoolean())
                .isTrue();
            assertThat(action(actions, "dealership_request_test_drive").path("anonymousAllowed").asBoolean(true))
                .isFalse();

            Seed template = seed(connection, "mkv-template-simulated-dealership-experience-v1-2");
            manifests.parseAndValidate(plugin(template), version(template));
            JsonNode contribution = mapper.readTree(template.manifestJson())
                .path("contributions").path("template");
            assertThat(contribution.path("requiredPluginRefs"))
                .extracting(JsonNode::asText)
                .contains("mkp-action-dealership-engagement-v1@1.1.0",
                    "mkp-integration-basic-loomai-inbox@1.1.0")
                .doesNotContain("mkp-action-dealership-engagement-v1@1.0.0");
            assertThat(contribution.path("requiredPluginInstallDefaults")
                .path("mkp-action-dealership-engagement-v1@1.1.0")
                .path("config").path("receiverActionUrl").asText())
                .isEqualTo("${MANAGED_RECEIVER_ACTION_URL}");

            Seed refreshedTemplate = seed(connection, "mkv-template-simulated-dealership-experience-v1-3");
            manifests.parseAndValidate(plugin(refreshedTemplate), version(refreshedTemplate));
            assertThat(refreshedTemplate.version()).isEqualTo("1.3.0");
            JsonNode refreshedContribution = mapper.readTree(refreshedTemplate.manifestJson())
                .path("contributions").path("template");
            assertThat(refreshedContribution.path("requiredPluginRefs"))
                .isEqualTo(contribution.path("requiredPluginRefs"));

            Seed durableTemplate = seed(connection, "mkv-template-simulated-dealership-experience-v1-4");
            manifests.parseAndValidate(plugin(durableTemplate), version(durableTemplate));
            assertThat(durableTemplate.version()).isEqualTo("1.4.0");
            JsonNode durableManifest = mapper.readTree(durableTemplate.manifestJson());
            Seed currentTemplate = seed(connection, "mkv-template-simulated-dealership-experience-v1-5");
            manifests.parseAndValidate(plugin(currentTemplate), version(currentTemplate));
            assertThat(currentTemplate.version()).isEqualTo("1.5.0");
            JsonNode currentManifest = mapper.readTree(currentTemplate.manifestJson());
            assertThat(currentManifest.path("compatibility")).isEqualTo(durableManifest.path("compatibility"));
            assertThat(durableManifest.path("compatibility").path("supportedDeploymentTargets"))
                .extracting(JsonNode::asText)
                .containsExactly("dev-openai-milvus");
            JsonNode durableContribution = durableManifest.path("contributions").path("template");
            assertThat(durableContribution.path("templateId").asText()).isEqualTo("dev-openai-milvus");
            assertThat(durableContribution.path("providerConfig").path("vectorStrategy").asText())
                .isEqualTo("milvus");
            assertThat(durableContribution.path("providerConfig").path("vectorProvisioningMode").asText())
                .isEqualTo("PLATFORM_MANAGED");
            assertThat(durableContribution.path("providerConfig").path("vectorStoragePosture").asText())
                .isEqualTo("DEDICATED");
            assertThat(durableContribution.path("requiredPluginRefs"))
                .extracting(JsonNode::asText)
                .containsExactly(
                    "mkp-data-simulated-dealership-stock-v1@1.1.0",
                    "mkp-action-simulated-dealership-discovery-v1@1.1.0",
                    "mkp-action-dealership-engagement-v1@1.2.0",
                    "mkp-data-dealership-knowledge-v1@1.0.0",
                    "mkp-action-conversation-handover@1.1.0",
                    "mkp-integration-basic-loomai-inbox@1.2.0"
                )
                .doesNotContain(
                    "mkp-data-simulated-dealership-stock-v1@1.0.0",
                    "mkp-action-simulated-dealership-discovery-v1@1.0.0",
                    "mkp-action-dealership-engagement-v1@1.1.0",
                    "mkp-action-conversation-handover@1.0.0",
                    "mkp-integration-basic-loomai-inbox@1.1.0"
                );
            JsonNode durableDefaults = durableContribution.path("requiredPluginInstallDefaults");
            assertThat(durableDefaults.has("mkp-data-simulated-dealership-stock-v1@1.1.0")).isTrue();
            assertThat(durableDefaults.has("mkp-action-dealership-engagement-v1@1.2.0")).isTrue();
            assertThat(durableDefaults.has("mkp-integration-basic-loomai-inbox@1.2.0")).isTrue();
            assertThat(durableDefaults.has("mkp-data-simulated-dealership-stock-v1@1.0.0")).isFalse();
            assertThat(durableDefaults.has("mkp-action-dealership-engagement-v1@1.1.0")).isFalse();
            assertThat(durableDefaults.has("mkp-integration-basic-loomai-inbox@1.1.0")).isFalse();

            Seed canonicalInbox = seed(connection, "mkv-integration-basic-loomai-inbox-v1-3");
            manifests.parseAndValidate(plugin(canonicalInbox), version(canonicalInbox));
            JsonNode canonicalChannel = mapper.readTree(canonicalInbox.manifestJson())
                .path("contributions").path("conversationChannel");
            assertThat(canonicalChannel.path("requiredRuntimeCapabilityIds"))
                .extracting(JsonNode::asText)
                .contains("canonical-conversation-orchestration-state");
            assertThat(canonicalChannel.path("requiredMigrationIds"))
                .extracting(JsonNode::asText)
                .contains("loomai-conversation-orchestration-state-v1");

            Seed canonicalTemplate = seed(connection, "mkv-template-simulated-dealership-experience-v1-5");
            manifests.parseAndValidate(plugin(canonicalTemplate), version(canonicalTemplate));
            JsonNode canonicalContribution = mapper.readTree(canonicalTemplate.manifestJson())
                .path("contributions").path("template");
            assertThat(canonicalContribution.path("requiredPluginRefs"))
                .extracting(JsonNode::asText)
                .contains("mkp-integration-basic-loomai-inbox@1.3.0")
                .doesNotContain("mkp-integration-basic-loomai-inbox@1.2.0");
            assertThat(canonicalContribution.path("requiredPluginInstallDefaults")
                .has("mkp-integration-basic-loomai-inbox@1.3.0")).isTrue();

            for (String versionId : List.of(
                "mkv-data-simulated-dealership-stock-v1-1",
                "mkv-action-simulated-dealership-discovery-v1-1",
                "mkv-action-dealership-engagement-v1-2",
                "mkv-action-conversation-handover-v1-1",
                "mkv-integration-basic-loomai-inbox-v1-2"
            )) {
                Seed compatibleDependency = seed(connection, versionId);
                manifests.parseAndValidate(plugin(compatibleDependency), version(compatibleDependency));
                assertThat(mapper.readTree(compatibleDependency.manifestJson())
                    .path("compatibility").path("supportedDeploymentTargets"))
                    .extracting(JsonNode::asText)
                    .containsExactly("custom-start-from-scratch", "dev-openai-milvus");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                "select active from deployment_target_profiles where id = 'dtp-coolify-prod-staging'")) {
                try (ResultSet row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getBoolean("active")).isFalse();
                }
            }
        }
    }

    private JsonNode action(JsonNode actions, String id) {
        for (JsonNode action : actions) {
            if (id.equals(action.path("actionId").asText())) return action;
        }
        throw new AssertionError("Missing action " + id);
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

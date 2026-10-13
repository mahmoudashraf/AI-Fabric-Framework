package com.ai.fabric.platform.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Testcontainers(disabledWithoutDocker = true)
class ConversationHandoverMarketplaceMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final MarketplaceManifestService manifestService =
        new MarketplaceManifestService(new ObjectMapper());

    @Test
    void productionMigrationPublishesValidBoundedConversationHandoverProducts() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("155"))
            .load()
            .migrate();
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("157"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            Map<String, Seed> seeds = readSeeds(statement);
            assertThat(seeds.keySet()).containsExactlyInAnyOrder(
                "mkp-action-conversation-handover",
                "mkp-integration-basic-loomai-inbox",
                "mkp-template-conversation-handover"
            );

            var action = parse(seeds.get("mkp-action-conversation-handover"));
            var actionContribution = action.manifest()
                .path("contributions")
                .path("actions")
                .path(0);
            assertThat(action.pluginType()).isEqualTo("ACTION");
            assertThat(actionContribution.path("actionId").asText())
                .isEqualTo("request_conversation_handover");
            assertThat(actionContribution.path("adapterType").asText())
                .isEqualTo("conversation-control");
            assertThat(actionContribution.path("operation").asText())
                .isEqualTo("REQUEST_HANDOVER");
            assertThat(actionContribution.path("terminal").asBoolean()).isTrue();
            assertThat(actionContribution.has("endpointUrl")).isFalse();

            var integration = parse(seeds.get("mkp-integration-basic-loomai-inbox"));
            var channel = integration.manifest()
                .path("contributions")
                .path("conversationChannel");
            assertThat(integration.pluginType()).isEqualTo("INTEGRATION");
            assertThat(channel.path("contractVersion").asText())
                .isEqualTo("LOOMAI_CONVERSATION_CHANNEL_V1");
            assertThat(channel.path("adapterImplementationId").asText())
                .isEqualTo("loomai-basic-inbox");
            assertThat(channel.path("compatibleBehaviorTypes"))
                .extracting(value -> value.asText())
                .containsExactly("CONVERSATIONAL");
            assertThat(channel.path("configuration").path("activeIdleAutoResume").asBoolean())
                .isFalse();
            assertThat(channel.path("configuration").path("intake").path("anonymousAllowed").asBoolean())
                .isTrue();
            assertThat(seeds.get("mkp-integration-basic-loomai-inbox").manifestJson())
                .doesNotContain("apiKeyValue", "signingSecretValue", "encryptionSecretValue");

            var template = parse(seeds.get("mkp-template-conversation-handover"));
            var templateContribution = template.manifest().path("contributions").path("template");
            assertThat(template.pluginType()).isEqualTo("TEMPLATE");
            assertThat(template.manifest().path("version").asText()).isEqualTo("1.0.1");
            assertThat(templateContribution.path("deploymentBehavior").path("type").asText())
                .isEqualTo("CONVERSATIONAL");
            assertThat(templateContribution.path("deploymentBehavior").path("defaultExecutionExtensions"))
                .extracting(value -> value.asText())
                .containsExactly("CONVERSATION_HANDOVER");
            assertThat(templateContribution.path("requiredPluginRefs"))
                .extracting(value -> value.asText())
                .containsExactlyInAnyOrder(
                    "mkp-action-conversation-handover@1.0.0",
                    "mkp-integration-basic-loomai-inbox@1.0.0"
                );
            assertThat(templateContribution.path("security").path("publicRuntimeBootstrapEnabled").asBoolean())
                .isTrue();
            assertThat(templateContribution.path("security").path("publicRuntimeAcceptedIssuers").asText())
                .isEqualTo("runtime-public-bootstrap");
            assertThat(templateContribution.path("security").path("publicRuntimeAcceptedAudiences").asText())
                .isEqualTo("loomai-ai-workspace");
        }
    }

    private MarketplaceManifestService.ParsedMarketplaceManifest parse(Seed seed) {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId(seed.pluginId());
        plugin.setPluginType(seed.pluginType());
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId(seed.versionId());
        version.setPluginId(seed.pluginId());
        version.setVersion(seed.version());
        version.setManifestJson(seed.manifestJson());
        return manifestService.parseAndValidate(plugin, version);
    }

    private Map<String, Seed> readSeeds(Statement statement) throws Exception {
        Map<String, Seed> result = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery("""
            select p.id as plugin_id,
                   p.plugin_type,
                   v.id as version_id,
                   v.version,
                   v.manifest_json
              from platform_marketplace_plugins p
              join platform_marketplace_plugin_versions v on v.plugin_id = p.id
             where p.id in (
               'mkp-action-conversation-handover',
               'mkp-integration-basic-loomai-inbox',
               'mkp-template-conversation-handover'
             )
               and v.status = 'PUBLISHED'
             order by p.id, v.version
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

    private record Seed(
        String pluginId,
        String pluginType,
        String versionId,
        String version,
        String manifestJson
    ) {}
}

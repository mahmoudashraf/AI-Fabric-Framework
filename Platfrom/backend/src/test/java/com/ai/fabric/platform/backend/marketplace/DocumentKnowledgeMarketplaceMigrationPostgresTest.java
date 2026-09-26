package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DocumentKnowledgeMarketplaceMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final MarketplaceManifestService manifestService =
        new MarketplaceManifestService(new ObjectMapper());

    @Test
    void productionMigrationsCreateScopedDocumentContractAndValidPublishedProducts() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("148"))
            .load()
            .migrate();
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("149"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(columnExists(statement, "platform_marketplace_plugin_datasets", "source_connector_config_json"))
                .isTrue();
            assertThat(columnExists(statement, "platform_marketplace_plugin_datasets", "document_policy_json"))
                .isTrue();
            assertThat(columnExists(statement, "platform_marketplace_dataset_handles", "scope_key"))
                .isTrue();
            assertThat(scopedHandleConstraintExists(statement)).isTrue();

            Map<String, ParsedSeed> seeds = readSeeds(statement);
            assertThat(seeds.keySet()).containsExactlyInAnyOrder(
                "mkp-data-document-knowledge-s3",
                "mkp-data-document-knowledge-mounted-demo",
                "mkp-template-document-knowledge-assistant"
            );

            var s3 = parse(seeds.get("mkp-data-document-knowledge-s3"));
            assertThat(seeds.get("mkp-data-document-knowledge-s3").version()).isEqualTo("1.1.0");
            assertThat(s3.manifest().path("compatibility").path("supportedDeploymentTargets"))
                .extracting(value -> value.asText())
                .contains("dev-openai-pinecone")
                .doesNotContain("dev-openai-lucene", "dev-openai-memory");
            assertThat(s3.datasets()).singleElement().satisfies(dataset -> {
                assertThat(dataset.ingestionMode()).isEqualTo("EXTERNAL_DOCUMENT_STORAGE");
                assertThat(dataset.storageScope()).isEqualTo("CUSTOMER_MANAGED");
                assertThat(dataset.sharingScope()).isEqualTo("DEPLOYMENT_ONLY");
                assertThat(dataset.connectorType()).isEqualTo("S3_COMPATIBLE_OBJECT_STORAGE");
                assertThat(dataset.sourceConnector().path("deleteSourceOnRemoval").asBoolean()).isFalse();
                assertThat(dataset.documentPolicy().path("allowedMediaTypes"))
                    .extracting(value -> value.asText())
                    .containsExactlyInAnyOrder("text/plain", "application/json");
            });

            var mounted = parse(seeds.get("mkp-data-document-knowledge-mounted-demo"));
            assertThat(mounted.datasets()).singleElement()
                .satisfies(dataset -> assertThat(dataset.connectorType()).isEqualTo("MOUNTED_FOLDER"));

            var template = parse(seeds.get("mkp-template-document-knowledge-assistant"));
            assertThat(template.pluginType()).isEqualTo("TEMPLATE");
            assertThat(seeds.get("mkp-template-document-knowledge-assistant").version()).isEqualTo("1.1.0");
            assertThat(template.manifest().path("compatibility").path("supportedDeploymentTargets"))
                .extracting(value -> value.asText())
                .containsExactly("dev-openai-pinecone");
            assertThat(template.manifest().path("contributions").path("template").path("templateId").asText())
                .isEqualTo("dev-openai-pinecone");
            assertThat(template.manifest().path("contributions").path("template").path("requiredPluginRefs"))
                .extracting(value -> value.asText())
                .containsExactly("mkp-data-document-knowledge-s3@1.1.0");
        }
    }

    private MarketplaceManifestService.ParsedMarketplaceManifest parse(ParsedSeed seed) {
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

    private Map<String, ParsedSeed> readSeeds(Statement statement) throws Exception {
        Map<String, ParsedSeed> result = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery("""
            select p.id as plugin_id,
                   p.plugin_type,
                   v.id as version_id,
                   v.version,
                   v.manifest_json
            from platform_marketplace_plugins p
            join platform_marketplace_plugin_versions v on v.plugin_id = p.id
            where p.id in (
                'mkp-data-document-knowledge-s3',
                'mkp-data-document-knowledge-mounted-demo',
                'mkp-template-document-knowledge-assistant'
            )
              and v.status = 'PUBLISHED'
            order by p.id
            """)) {
            while (rows.next()) {
                ParsedSeed seed = new ParsedSeed(
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

    private boolean scopedHandleConstraintExists(Statement statement) throws Exception {
        try (ResultSet result = statement.executeQuery("""
            select pg_get_constraintdef(oid) as definition
            from pg_constraint
            where conname = 'uq_marketplace_dataset_handle_scope'
            """)) {
            return result.next()
                && result.getString("definition").contains("plugin_id, tenant_id, dataset_id, scope_key");
        }
    }

    private boolean columnExists(Statement statement, String table, String column) throws Exception {
        try (ResultSet result = statement.executeQuery("""
            select exists (
                select 1
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = '%s'
                  and column_name = '%s'
            )
            """.formatted(table, column))) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private record ParsedSeed(
        String pluginId,
        String pluginType,
        String versionId,
        String version,
        String manifestJson
    ) {
    }
}

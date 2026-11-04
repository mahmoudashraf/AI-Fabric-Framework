package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import db.migration.V231__customer_scoped_document_contract_revisions;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.configuration.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentKnowledgeModeNeutralMigrationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> DOCUMENT_VERSIONS = Map.of(
        "mkp-data-document-knowledge-s3", "1.2.0",
        "mkp-data-dealership-knowledge-v1", "1.1.0",
        "mkp-data-ai-support-documents", "1.1.0"
    );

    @Test
    void portableMigrationPublishesNeutralRevisionsAndPreservesApiPublishedHistory() throws Exception {
        try (var connection = h2()) {
            prepareH2(connection);
            seedApiProducts(connection);
            verifyRevisions(connection);
        }
    }

    @Test
    void freshControlPlaneGetsTheNewSupportManifestWithoutInventingLegacyHistory() throws Exception {
        try (var connection = h2()) {
            prepareH2(connection);
            migrate(connection, new V231__customer_scoped_document_contract_revisions());
            assertThat(manifest(connection, "mkp-data-ai-support-documents", "1.1.0"))
                .isEqualTo(productManifest("support-documents.1.1.0.manifest.json"));
            try (var rows = connection.createStatement().executeQuery("""
                select count(*) from platform_marketplace_plugin_versions
                where plugin_id = 'mkp-data-ai-support-documents' and version = '1.0.0'
                """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    static Connection h2() throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:doc231_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
    }

    static void prepareH2(Connection connection) throws Exception {
        for (String script : List.of("V25__marketplace_catalog_foundation.sql",
            "V30__marketplace_publishers_and_review_workflow.sql",
            "V148__document_knowledge_marketplace_products.sql",
            "V149__document_knowledge_durable_vector_template.sql")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + script));
        }
        ObjectNode dealership = (ObjectNode) manifest(connection, "mkp-data-document-knowledge-s3", "1.1.0");
        dealership.put("pluginId", "mkp-data-dealership-knowledge-v1").put("version", "1.0.0");
        seed(connection, "api-dealership-document-row", dealership);
        ObjectNode template = (ObjectNode) manifest(connection, "mkp-template-document-knowledge-assistant", "1.1.0");
        for (var entry : Map.of("mkp-template-simulated-dealership-experience-v1", "1.10.0",
            "mkp-template-dealership-external-systems-v1", "1.2.0",
            "mkp-template-dealership-external-systems-managed-inbox-v1", "1.1.0").entrySet()) {
            ObjectNode candidate = template.deepCopy().put("pluginId", entry.getKey()).put("version", entry.getValue());
            ObjectNode contribution = (ObjectNode) candidate.at("/contributions/template");
            contribution.set("requiredPluginRefs", MAPPER.createArrayNode().add("mkp-data-dealership-knowledge-v1@1.0.0"));
            contribution.putObject("requiredPluginInstallDefaults").putObject("mkp-data-dealership-knowledge-v1@1.0.0")
                .putObject("config").put("publicContentApprovalConfirmed", false);
            seed(connection, "desk-api-" + UUID.randomUUID(), candidate);
        }
    }

    static void seedApiProducts(Connection connection) throws Exception {
        seed(connection, "api-support-document-row", productManifest("support-documents.manifest.json"));
        seed(connection, "api-support-ticket-row", productManifest("support-tickets.1.0.0.manifest.json"));
        seed(connection, "api-support-diagnostics-row", productManifest("loomai-support-diagnostics.1.0.3.manifest.json"));
        seed(connection, "api-support-template-row", productManifest("ai-support-workspace.1.1.0.manifest.json"));
        seed(connection, "api-loomai-support-template-row", productManifest("loomai-support-workspace.1.1.0.manifest.json"));
    }

    static void verifyRevisions(Connection connection) throws Exception {
        Map<String, List<String>> history = snapshot(connection);
        migrate(connection, new V231__customer_scoped_document_contract_revisions());
        assertThat(snapshot(connection)).containsAllEntriesOf(history);
        for (var entry : DOCUMENT_VERSIONS.entrySet()) {
            JsonNode manifest = manifest(connection, entry.getKey(), entry.getValue());
            MarketplacePluginEntity plugin = new MarketplacePluginEntity();
            plugin.setId(entry.getKey());
            plugin.setPluginType("DATA");
            MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
            version.setPluginId(plugin.getId());
            version.setVersion(entry.getValue());
            version.setManifestJson(manifest.toString());
            var parsed = new MarketplaceManifestService(MAPPER).parseAndValidate(plugin, version);
            assertThat(parsed.datasets()).singleElement().satisfies(dataset -> {
                assertThat(dataset.storageScope()).isEqualTo("CUSTOMER_SCOPED");
                assertThat(dataset.sharingScope()).isEqualTo("DEPLOYMENT_ONLY");
                assertThat(dataset.ingestionMode()).isEqualTo("EXTERNAL_DOCUMENT_STORAGE");
                assertThat(dataset.updateStrategy()).isEqualTo("VERSIONED_REPLACE");
                assertThat(dataset.sourceConnector().path("storageOwnership").asText()).isEqualTo("BOUND_CONNECTION");
                assertThat(dataset.sourceConnector().path("allowedOperations")).extracting(JsonNode::asText)
                    .containsExactly("LIST", "READ", "HEAD");
                assertThat(dataset.sourceConnector().path("deleteSourceOnRemoval").booleanValue()).isFalse();
            });
            String oldVersion = entry.getKey().equals("mkp-data-document-knowledge-s3") ? "1.1.0" : "1.0.0";
            JsonNode previous = manifest(connection, entry.getKey(), oldVersion);
            JsonNode oldDataset = previous.at("/contributions/datasets/0");
            JsonNode currentDataset = manifest.at("/contributions/datasets/0");
            assertThat(currentDataset.path("documentPolicy")).isEqualTo(oldDataset.path("documentPolicy"));
            assertThat(manifest.at("/contributions/knowledgeSources")).isEqualTo(previous.at("/contributions/knowledgeSources"));
            assertThat(oldDataset.path("storageScope").asText()).isEqualTo("CUSTOMER_MANAGED");
            assertThat(oldDataset.at("/sourceConnector/storageOwnership").asText()).isEqualTo("CUSTOMER_MANAGED");
        }
        for (var entry : Map.of("mkp-template-document-knowledge-assistant", "1.2.0",
            "mkp-template-simulated-dealership-experience-v1", "1.11.0",
            "mkp-template-dealership-external-systems-v1", "1.3.0",
            "mkp-template-dealership-external-systems-managed-inbox-v1", "1.2.0",
            "mkp-template-ai-support-workspace", "1.2.0",
            "mkp-template-loomai-support-workspace", "1.2.0").entrySet()) {
            JsonNode current = manifest(connection, entry.getKey(), entry.getValue());
            assertThat(current.path("version").asText()).isEqualTo(entry.getValue());
            assertThat(current.at("/contributions/template/requiredPluginRefs").toString())
                .doesNotContain("mkp-data-document-knowledge-s3@1.1.0", "mkp-data-dealership-knowledge-v1@1.0.0",
                    "mkp-data-ai-support-documents@1.0.0");
            if (entry.getKey().contains("dealership")) {
                assertThat(current.at("/contributions/template/requiredPluginInstallDefaults").toString())
                    .doesNotContain("mkp-data-dealership-knowledge-v1@1.0.0");
            }
        }
        Map<String, List<String>> migrated = snapshot(connection);
        migrate(connection, new V231__customer_scoped_document_contract_revisions());
        assertThat(snapshot(connection)).isEqualTo(migrated);
    }

    private static Map<String, List<String>> snapshot(Connection connection) throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (var rows = connection.createStatement().executeQuery("select * from platform_marketplace_plugin_versions order by id")) {
            while (rows.next()) {
                List<String> fields = new ArrayList<>();
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) fields.add(rows.getString(column));
                result.put(rows.getString("id"), fields);
            }
        }
        return result;
    }

    static JsonNode manifest(Connection connection, String pluginId, String version) throws Exception {
        try (var query = connection.prepareStatement("select manifest_json from platform_marketplace_plugin_versions where plugin_id = ? and version = ?")) {
            query.setString(1, pluginId);
            query.setString(2, version);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).as(pluginId + "@" + version).isTrue();
                return MAPPER.readTree(rows.getString(1));
            }
        }
    }

    static void seed(Connection connection, String id, JsonNode manifest) throws Exception {
        String pluginId = manifest.path("pluginId").asText();
        try (var query = connection.prepareStatement("""
            insert into platform_marketplace_plugins
              (id, slug, display_name, plugin_type, publisher_slug, publisher_display_name, short_description, status, created_at, updated_at)
            select ?, ?, 'API published fixture', ?, 'loom', 'Loom AI', 'API fixture', 'ACTIVE', current_timestamp, current_timestamp
            where not exists (select 1 from platform_marketplace_plugins where id = ?)
            """)) {
            query.setString(1, pluginId);
            query.setString(2, pluginId);
            query.setString(3, manifest.path("pluginType").asText());
            query.setString(4, pluginId);
            query.executeUpdate();
        }
        try (var query = connection.prepareStatement("""
            insert into platform_marketplace_plugin_versions
              (id, plugin_id, version, release_channel, status, manifest_json, created_at, published_at,
               submitted_by_publisher_id, reviewed_by_actor_id, review_notes, bundle_sha256)
            values (?, ?, ?, 'GA', 'PUBLISHED', ?, current_timestamp, current_timestamp, 'mpub-loom', 'reviewer', 'Original review', 'original-hash')
            """)) {
            query.setString(1, id);
            query.setString(2, pluginId);
            query.setString(3, manifest.path("version").asText());
            query.setString(4, manifest.toString());
            query.executeUpdate();
        }
    }

    static void migrate(Connection connection, BaseJavaMigration migration) throws Exception {
        migration.migrate(new Context() {
            public Configuration getConfiguration() { return null; }
            public Connection getConnection() { return connection; }
        });
    }

    private static JsonNode productManifest(String name) throws Exception {
        Path path = Path.of("product-templates/ai-support-workspace/manifests", name);
        if (!Files.exists(path)) path = Path.of("../../product-templates/ai-support-workspace/manifests", name);
        return MAPPER.readTree(Files.readString(path));
    }
}

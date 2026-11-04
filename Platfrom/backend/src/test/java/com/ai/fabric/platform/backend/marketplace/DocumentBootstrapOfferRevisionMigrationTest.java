package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import db.migration.V231__customer_scoped_document_contract_revisions;
import db.migration.V232__mode_neutral_document_bootstrap_offers;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import static com.ai.fabric.platform.backend.marketplace.DocumentKnowledgeModeNeutralMigrationTest.*;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentBootstrapOfferRevisionMigrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CustomerOfferCodec CODEC = new CustomerOfferCodec(JSON);

    @Test
    void freshPublicOfferPinsNewDataWithoutRewritingPublishedOffersOrNestedVersions() throws Exception {
        try (var connection = h2()) {
            prepareH2(connection);
            seedApiProducts(connection);
            verifyPublicOfferRevisions(connection);
        }
    }

    @Test
    void h2DiscoversAndExecutesBothJavaMigrationsThroughTheProductionFlywayLocation() throws Exception {
        try (var connection = h2()) {
            prepareH2(connection);
            seedApiProducts(connection);
            verifyPublicOfferRevisions(connection, true);
        }
    }

    static void verifyPublicOfferRevisions(Connection connection) throws Exception {
        verifyPublicOfferRevisions(connection, false);
    }

    private static void verifyPublicOfferRevisions(Connection connection, boolean discoverWithFlyway) throws Exception {
        ObjectNode publicData = ((ObjectNode) manifest(connection, "mkp-data-document-knowledge-s3", "1.1.0")).deepCopy();
        publicData.put("version", "1.2.0");
        publicData.putObject("metadata").put("version", "supplier-v9").put("storageScope", "CUSTOMER_MANAGED")
            .put("storageOwnership", "CUSTOMER_MANAGED").put("note", "mkp-data-document-knowledge-s3@1.2.0");
        ((ObjectNode) publicData.at("/contributions/knowledgeSources/0")).putObject("filters").put("publicationStatus", "PUBLIC_APPROVED");
        seed(connection, "api-public-document-v1-2", publicData);
        ObjectNode publicTemplate = ((ObjectNode) manifest(connection, "mkp-template-document-knowledge-assistant", "1.1.0")).deepCopy();
        publicTemplate.put("version", "1.2.0");
        publicTemplate.putObject("metadata").put("version", "nested-template-v7").put("note", "mkp-data-document-knowledge-s3@1.1.0");
        ObjectNode template = (ObjectNode) publicTemplate.at("/contributions/template");
        template.set("requiredPluginRefs", JSON.createArrayNode().add("mkp-data-document-knowledge-s3@1.2.0"));
        template.putObject("requiredPluginInstallDefaults").putObject("mkp-data-document-knowledge-s3@1.2.0")
            .putObject("config").put("documentStorageBindingRef", "slot:document-storage").put("publicContentApprovalConfirmed", false);
        seed(connection, "api-public-template-v1-2", publicTemplate);

        // This is the exact native offer schema, without deployment, entitlement or release tables to mutate.
        try (var query = connection.createStatement()) {
            query.execute("""
                create table if not exists platform_customer_offers
                  (id varchar(64) primary key, definition_json text not null, fingerprint varchar(64) not null,
                   review_reference varchar(255) not null, reviewed_by varchar(255) not null, reviewed_at timestamp with time zone not null)
                """);
        }
        seedOffer(connection, "public-knowledge-workspace-v1", publicTemplate);
        ObjectNode dealer = (ObjectNode) manifest(connection, "mkp-template-simulated-dealership-experience-v1", "1.10.0");
        seedOffer(connection, "public-dealership-simulator-v2", dealer);
        seedOffer(connection, "reviewed-support-offer-v1", (ObjectNode) manifest(connection, "mkp-template-ai-support-workspace", "1.1.0"));
        seedOffer(connection, "retired-private-offer", (ObjectNode) manifest(connection, "mkp-template-dealership-external-systems-v1", "1.2.0"));
        try (var query = connection.createStatement()) {
            query.executeUpdate("update platform_marketplace_plugin_versions set status = 'DEPRECATED' where plugin_id = 'mkp-template-dealership-external-systems-v1' and version = '1.2.0'");
        }
        Map<String, List<String>> originalOffers = offers(connection);

        if (discoverWithFlyway) {
            // Earlier production JSONB SQL is covered by PostgreSQL; baseline the native H2 schema fixture.
            var flyway = Flyway.configure().dataSource(connection.getMetaData().getURL(), "sa", "")
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("230").target("232").load();
            flyway.migrate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("232");
        } else {
            migrate(connection, new V231__customer_scoped_document_contract_revisions());
        }
        ObjectNode revisedData = (ObjectNode) manifest(connection, "mkp-data-document-knowledge-s3", "1.3.0");
        assertThat(revisedData.path("metadata")).isEqualTo(publicData.path("metadata"));
        assertThat(revisedData.at("/contributions/knowledgeSources")).isEqualTo(publicData.at("/contributions/knowledgeSources"));
        assertThat(manifest(connection, "mkp-data-document-knowledge-s3", "1.2.0")).isEqualTo(publicData);
        ObjectNode revisedTemplate = (ObjectNode) manifest(connection, publicTemplate.path("pluginId").asText(), "1.3.0");
        assertThat(revisedTemplate.path("metadata")).isEqualTo(publicTemplate.path("metadata"));
        assertThat(revisedTemplate.at("/contributions/template/requiredPluginInstallDefaults/mkp-data-document-knowledge-s3@1.3.0/config/publicContentApprovalConfirmed").asBoolean(true)).isFalse();

        migrate(connection, new V232__mode_neutral_document_bootstrap_offers());
        assertThat(offers(connection)).containsAllEntriesOf(originalOffers);
        try (var rows = connection.createStatement().executeQuery("select count(*) from platform_customer_offers where review_reference like '%source-offer:retired-private-offer'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isZero();
        }
        assertOffer(connection, "public-knowledge-workspace-v2", "mkp-data-document-knowledge-s3", "1.3.0", originalOffers.get("public-knowledge-workspace-v1").getFirst());
        assertOffer(connection, "public-dealership-simulator-v3", "mkp-data-dealership-knowledge-v1", "1.1.0", originalOffers.get("public-dealership-simulator-v2").getFirst());
        try (var rows = connection.createStatement().executeQuery("select id from platform_customer_offers where review_reference like '%source-offer:reviewed-support-offer-v1'")) {
            assertThat(rows.next()).isTrue();
            assertOffer(connection, rows.getString(1), "mkp-data-ai-support-documents", "1.1.0", originalOffers.get("reviewed-support-offer-v1").getFirst());
        }
        Map<String, List<String>> migrated = offers(connection);
        migrate(connection, new V231__customer_scoped_document_contract_revisions());
        migrate(connection, new V232__mode_neutral_document_bootstrap_offers());
        assertThat(offers(connection)).isEqualTo(migrated);
    }

    private static void assertOffer(Connection connection, String id, String documentPlugin, String expectedVersion, String previousJson) throws Exception {
        try (var query = connection.prepareStatement("select definition_json, fingerprint from platform_customer_offers where id = ?")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).as(id).isTrue();
                JsonNode definition = JSON.readTree(rows.getString(1));
                CODEC.read(rows.getString(1), Definition.class);
                assertThat(rows.getString(2)).isEqualTo(CODEC.hash(definition));
                JsonNode template = version(connection, definition.at("/template/versionId").asText());
                assertThat(definition.at("/template/manifestSha256").asText()).isEqualTo(CODEC.hash(template));
                assertThat(template.at("/contributions/template/requiredPluginRefs")).extracting(JsonNode::asText)
                    .contains(documentPlugin + "@" + expectedVersion);
                JsonNode pin = null;
                for (JsonNode entry : definition.path("plugins")) if (documentPlugin.equals(entry.path("pluginId").asText())) pin = entry;
                assertThat(pin).isNotNull();
                JsonNode document = version(connection, pin.path("versionId").asText());
                assertThat(pin.path("manifestSha256").asText()).isEqualTo(CODEC.hash(document));
                assertThat(document.path("version").asText()).isEqualTo(expectedVersion);
                assertThat(document.at("/contributions/datasets/0/storageScope").asText()).isEqualTo("CUSTOMER_SCOPED");
                assertThat(document.at("/contributions/datasets/0/sourceConnector/storageOwnership").asText()).isEqualTo("BOUND_CONNECTION");
                ObjectNode previous = (ObjectNode) JSON.readTree(previousJson);
                ObjectNode comparable = ((ObjectNode) definition).deepCopy();
                comparable.set("template", previous.path("template"));
                comparable.set("plugins", previous.path("plugins"));
                assertThat(comparable).isEqualTo(previous);
            }
        }
    }

    private static void seedOffer(Connection connection, String id, ObjectNode template) throws Exception {
        ObjectNode definition = JSON.createObjectNode().put("schemaVersion", "1").put("name", "Reviewed starter")
            .put("templateId", template.at("/contributions/template/templateId").asText())
            .put("approvedPolicyRef", "original-policy").put("storagePolicyRef", "original-storage-policy");
        definition.set("template", pin(connection, template.path("pluginId").asText(), template.path("version").asText()));
        var plugins = definition.putArray("plugins");
        for (JsonNode reference : template.at("/contributions/template/requiredPluginRefs")) {
            String[] parts = reference.asText().split("@");
            plugins.add(pin(connection, parts[0], parts[1]));
        }
        var policy = new Definition("1", "Reviewed starter", definition.path("templateId").asText(),
            JSON.treeToValue(definition.path("template"), PluginPin.class),
            JSON.readerForListOf(PluginPin.class).<List<PluginPin>>readValue(definition.path("plugins")),
            List.of(new ArtifactPin("reviewed-runtime", "reviewed-runtime-hash")),
            List.of(new TargetPermission("reviewed-staging", TargetIntent.CUSTOMER_STAGING, "reviewed-target-hash")),
            Set.of(Operation.BOOTSTRAP_CONFIGURATION), List.of(new SettingRule(Setting.DEPLOYMENT_NAME, ValueType.TEXT, 120, Set.of())),
            List.of(new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("reviewed-s3"))),
            Set.of(DocumentAudience.PUBLIC), Set.of(Prerequisite.APPROVED_POLICY, Prerequisite.IMMUTABLE_ARTIFACTS),
            new ReviewedLimits("original-measurement", 1, 1, 1000, 1024, 2048, 0, 1, 0, 1, 24, 24, 24),
            new ReviewedTopology(1, 750, 768, 1536, 0, 1, 0, 1),
            "original-policy", "original-operating-policy", "original-identity-policy", "original-storage-policy");
        try (var query = connection.prepareStatement("insert into platform_customer_offers values (?, ?, ?, 'original-review', 'original-reviewer', current_timestamp)")) {
            query.setString(1, id);
            query.setString(2, CODEC.write(policy));
            query.setString(3, CODEC.hash(policy));
            query.executeUpdate();
        }
    }

    private static ObjectNode pin(Connection connection, String plugin, String version) throws Exception {
        try (var query = connection.prepareStatement("select id, manifest_json from platform_marketplace_plugin_versions where plugin_id = ? and version = ?")) {
            query.setString(1, plugin);
            query.setString(2, version);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).as(plugin + "@" + version).isTrue();
                return JSON.createObjectNode().put("pluginId", plugin).put("versionId", rows.getString(1))
                    .put("manifestSha256", CODEC.hash(JSON.readTree(rows.getString(2))));
            }
        }
    }

    private static JsonNode version(Connection connection, String id) throws Exception {
        try (var query = connection.prepareStatement("select manifest_json from platform_marketplace_plugin_versions where id = ?")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return JSON.readTree(rows.getString(1));
            }
        }
    }

    private static Map<String, List<String>> offers(Connection connection) throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (var rows = connection.createStatement().executeQuery("select * from platform_customer_offers order by id")) {
            while (rows.next()) result.put(rows.getString("id"), List.of(rows.getString("definition_json"), rows.getString("fingerprint"),
                rows.getString("review_reference"), rows.getString("reviewed_by"), rows.getString("reviewed_at")));
        }
        return result;
    }
}

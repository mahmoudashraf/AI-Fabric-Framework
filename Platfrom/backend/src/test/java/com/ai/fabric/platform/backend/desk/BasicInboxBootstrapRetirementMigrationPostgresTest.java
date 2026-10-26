package com.ai.fabric.platform.backend.desk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class BasicInboxBootstrapRetirementMigrationPostgresTest {
    private static final String BASIC_INBOX = "mkp-integration-basic-loomai-inbox";
    private static final String DESK = "mkp-integration-loomai-desk";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void retiresOnlyObsoleteBootstrapMetadataWithoutRewritingPayloadsOrLiveGraphHistory() throws Exception {
        migrate("215");
        try (var connection = POSTGRES.createConnection("")) {
            insertSpec(connection, "retired-contract", true, """
                {"schemaVersion":"1","services":[
                  {"slot":"inbox","contract":"BASIC_INBOX_KEYS_V1","environment":{}},
                  {"slot":"external-connector","contract":"HTTP_SERVICE","environment":{}}
                ]}
                """);
            insertSpec(connection, "retired-initialization", true, """
                {"services":[{"slot":"inbox","contract":"HTTP_SERVICE",
                  "initializationContract":"inbox-callback-canary/v1","environment":{}}]}
                """);
            insertSpec(connection, "retired-operators", true, """
                {"services":[{"slot":"inbox","contract":"HTTP_SERVICE","environment":{
                  "INITIAL_OPERATORS":{"kind":"OPERATORS"}}}]}
                """);
            insertSpec(connection, "already-inactive", false, """
                {"services":[{"contract":"BASIC_INBOX_KEYS_V1"}]}
                """);
            insertSpec(connection, "unrelated-http", true, """
                {"services":[{"slot":"external-service","contract":"HTTP_SERVICE",
                  "initializationContract":"other-callback/v1","environment":{
                    "DESCRIPTION":{"kind":"LITERAL","reference":"BASIC_INBOX_KEYS_V1 OPERATORS inbox-callback-canary/v1"}}}]}
                """);
            insertSpec(connection, "unrelated-null-environment", true, """
                {"services":[{"contract":"HTTP_SERVICE","environment":null}]}
                """);
            insertTemplateVersion(connection, "migration-retired-template", "91.0.0",
                List.of(BASIC_INBOX + "@1.3.0"));
            insertTemplateVersion(connection, "migration-similar-plugin-ref", "91.1.0",
                List.of(BASIC_INBOX + "-external@1.0.0"));
            insertTemplateVersion(connection, "migration-desk-template", "91.2.0", List.of(DESK + "@1.0.0"));
            insertGraphAndOfferHistory(connection);

            var specs = snapshot(connection, "deployment_managed_dependency_specs");
            var plugins = snapshot(connection, "platform_marketplace_plugins");
            var versions = snapshot(connection, "platform_marketplace_plugin_versions");
            var graphs = snapshot(connection, "deployment_managed_dependency_graphs");
            var offers = snapshot(connection, "platform_customer_offers");
            var deskBindings = snapshot(connection, "platform_desk_bindings");

            Set<String> retiredSpecs = Set.of("retired-contract", "retired-initialization", "retired-operators");
            var expectedSpecs = copy(specs);
            retiredSpecs.forEach(id -> expectedSpecs.get(id).put("active", false));
            var expectedPlugins = copy(plugins);
            expectedPlugins.get(BASIC_INBOX).put("status", "DEPRECATED");
            var expectedVersions = copy(versions);
            for (var entry : versions.entrySet()) {
                var version = entry.getValue();
                var plugin = plugins.get(version.path("plugin_id").asText());
                var manifest = MAPPER.readTree(version.path("manifest_json").asText());
                boolean requiresBasicInbox = false;
                for (var ref : manifest.path("contributions").path("template").path("requiredPluginRefs")) {
                    requiresBasicInbox |= ref.isTextual() && ref.asText().startsWith(BASIC_INBOX + "@");
                }
                if (BASIC_INBOX.equals(version.path("plugin_id").asText())
                    || ("TEMPLATE".equals(plugin.path("plugin_type").asText()) && requiresBasicInbox)) {
                    expectedVersions.get(entry.getKey()).put("status", "DEPRECATED");
                }
            }

            migrate("216");

            // Whole-row equality protects raw JSON strings, hashes, review evidence and timestamps.
            assertThat(snapshot(connection, "deployment_managed_dependency_specs")).isEqualTo(expectedSpecs);
            assertThat(snapshot(connection, "platform_marketplace_plugins")).isEqualTo(expectedPlugins);
            var migratedVersions = snapshot(connection, "platform_marketplace_plugin_versions");
            assertThat(migratedVersions).isEqualTo(expectedVersions);
            assertThat(snapshot(connection, "deployment_managed_dependency_graphs")).isEqualTo(graphs);
            assertThat(snapshot(connection, "platform_customer_offers")).isEqualTo(offers);
            assertThat(snapshot(connection, "platform_desk_bindings")).isEqualTo(deskBindings);

            assertThat(migratedVersions.get("migration-retired-template").path("status").asText()).isEqualTo("DEPRECATED");
            assertThat(migratedVersions.get("migration-similar-plugin-ref")).isEqualTo(versions.get("migration-similar-plugin-ref"));
            assertThat(migratedVersions.get("migration-desk-template")).isEqualTo(versions.get("migration-desk-template"));
            assertThat(migratedVersions.get("mkv-integration-loomai-desk-v1").path("status").asText()).isEqualTo("PUBLISHED");
            var deskClones = versions.keySet().stream().filter(id -> id.startsWith("mkv-desk-")).toList();
            assertThat(deskClones).isNotEmpty();
            deskClones.forEach(id -> assertThat(migratedVersions.get(id)).isEqualTo(versions.get(id)));
            assertThat(plugins).containsKeys(DESK, "mkp-data-dealership-external-stock-v1",
                "mkp-action-dealership-external-discovery-v1", "mkp-action-dealership-external-engagement-v1");
        }
    }

    private void insertSpec(Connection connection, String id, boolean active, String json) throws Exception {
        try (var statement = connection.prepareStatement("""
            insert into deployment_managed_dependency_specs
                (id, fingerprint, spec_json, reviewed_by, reviewed_at, review_reference, active)
            values (?, ?, ?, 'migration-test', current_timestamp, 'retained-review', ?)
            """)) {
            statement.setString(1, id);
            statement.setString(2, hash(json));
            statement.setString(3, json);
            statement.setBoolean(4, active);
            statement.executeUpdate();
        }
    }

    private void insertTemplateVersion(Connection connection, String id, String version, List<String> refs) throws Exception {
        ObjectNode manifest;
        try (var statement = connection.createStatement(); var row = statement.executeQuery("""
            select manifest_json from platform_marketplace_plugin_versions
            where id = 'mkv-template-dealership-external-systems-v1-0'
            """)) {
            assertThat(row.next()).isTrue();
            manifest = (ObjectNode) MAPPER.readTree(row.getString(1));
        }
        manifest.put("version", version);
        // A textual mention outside the structured dependency list must not retire a template.
        manifest.put("description", "Historical reference: " + BASIC_INBOX + "@1.3.0");
        ((ObjectNode) manifest.path("contributions").path("template"))
            .set("requiredPluginRefs", MAPPER.valueToTree(refs));
        String json = MAPPER.writeValueAsString(manifest);
        try (var statement = connection.prepareStatement("""
            insert into platform_marketplace_plugin_versions
                (id, plugin_id, version, release_channel, status, manifest_json,
                 submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
                 reviewed_at, review_notes, bundle_sha256, created_at, published_at)
            values (?, 'mkp-template-dealership-external-systems-v1', ?, 'EARLY_ACCESS', 'PUBLISHED', ?,
                'mpub-loom', 'system', 'system', current_timestamp, 'Retained review', ?, current_timestamp, current_timestamp)
            """)) {
            statement.setString(1, id);
            statement.setString(2, version);
            statement.setString(3, json);
            statement.setString(4, hash(json));
            statement.executeUpdate();
        }
    }

    private void insertGraphAndOfferHistory(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into deployment_managed_dependency_graphs
                    (id, deployment_id, target_profile_id, customer_id, tenant_id, version_id, release_id,
                     spec_ref, spec_fingerprint, binding_fingerprint, status, step, revision, spec_json, state_json,
                     created_at, updated_at)
                select 'retained-graph', 'retained-deployment', 'retained-target', 'retained-customer',
                    'retained-tenant', 'retained-version', 'retained-release', id, fingerprint,
                    repeat('b',64), 'READY', 'ACTIVE', 7, spec_json,
                    '{"nodes":[{"slot":"inbox","applicationUuid":"old-inbox-app","databaseUuid":"old-inbox-db","status":"READY"},
                      {"slot":"external-connector","applicationUuid":"keep-connector","status":"READY"}]}',
                    current_timestamp, current_timestamp
                from deployment_managed_dependency_specs where id = 'retired-contract'
                """);
            statement.executeUpdate("""
                insert into platform_customer_offers
                    (id, definition_json, fingerprint, review_reference, reviewed_by, reviewed_at)
                values ('retained-offer', '{"historicalIntegration":"mkp-integration-basic-loomai-inbox@1.3.0"}',
                    repeat('c',64), 'retained-review', 'system', current_timestamp)
                """);
        }
    }

    private Map<String, ObjectNode> snapshot(Connection connection, String table) throws Exception {
        var result = new LinkedHashMap<String, ObjectNode>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("select id, to_jsonb(t)::text from " + table + " t order by id")) {
            while (rows.next()) result.put(rows.getString(1), (ObjectNode) MAPPER.readTree(rows.getString(2)));
        }
        return result;
    }

    private Map<String, ObjectNode> copy(Map<String, ObjectNode> source) {
        var result = new LinkedHashMap<String, ObjectNode>();
        source.forEach((id, row) -> result.put(id, row.deepCopy()));
        return result;
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(target).load().migrate();
    }
}

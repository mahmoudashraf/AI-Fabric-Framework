package com.ai.fabric.platform.backend.marketplace.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CustomerPluginLibraryMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String SOURCE = "{\"pluginType\":\"DATA\",\"unknown\":{\"n\":123456789012345678901234567890,\"d\":0.123456789012345678900,\"null\":null,\"array\":[3,1,2]}}";
    private static final String ACTION_SOURCE = "{\"pluginType\":\"ACTION\",\"version\":\"1.0.0\",\"audit\":true}";

    @ParameterizedTest
    @ValueSource(strings = {"235", "236"})
    void convergesFromProductionAndAlreadyAppliedStagingWithoutRewritingHistory(String baseline) throws Exception {
        String database = "plugin_library_from_" + baseline;
        try (Connection admin = POSTGRES.createConnection(""); var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
        migrate(url, baseline);
        String originalChecksum = null;
        try (Connection connection = connect(url)) {
            if ("236".equals(baseline)) {
                originalChecksum = scalar(connection, "select checksum::text from flyway_schema_history where version='236'");
                oldFixture(connection);
                assertThat(scalar(connection, "select count(*) from information_schema.columns where table_name='platform_marketplace_plugins' and column_name='visibility'")).isEqualTo("0");
            }
        }
        migrate(url, "237").validate();
        try (Connection connection = connect(url)) {
            assertThat(scalar(connection, "select max(version::numeric)::text from flyway_schema_history where success and version ~ '^[0-9]+$'")).isEqualTo("237");
            assertThat(scalar(connection, "select count(*) from information_schema.tables where table_name='platform_plugin_definition_drafts'")).isEqualTo("0");
            assertThat(scalar(connection, "select count(*) from platform_marketplace_plugins where owner_customer_id is null and visibility <> 'PUBLIC'")).isEqualTo("0");
            assertThat(scalar(connection, "select catalogue_family from platform_marketplace_plugins where id='mkp-data-ai-support-documents'")).isEqualTo("DOCUMENT_STORAGE");
            if ("236".equals(baseline)) {
                assertThat(scalar(connection, "select checksum::text from flyway_schema_history where version='236'")).isEqualTo(originalChecksum);
                assertThat(scalar(connection, "select draft_manifest_json from platform_marketplace_plugins where id='old-data'")).isEqualTo(SOURCE);
                assertThat(scalar(connection, "select draft_revision::text from platform_marketplace_plugins where id='old-data'")).isEqualTo("42");
                assertThat(scalar(connection, "select status from platform_marketplace_plugins where id='old-data'")).isEqualTo("ACTIVE");
                assertThat(scalar(connection, "select draft_manifest_json from platform_marketplace_plugins where id='old-action'")).isEqualTo(ACTION_SOURCE);
                assertThat(scalar(connection, "select draft_revision::text from platform_marketplace_plugins where id='old-action'")).isEqualTo("7");
                assertThat(scalar(connection, "select status from platform_marketplace_plugins where id='old-action'")).isEqualTo("ARCHIVED");
                assertThat(scalar(connection, "select plugin_type from platform_marketplace_plugins where id='old-action'")).isEqualTo("ACTION");
                assertThat(scalar(connection, "select count(*) from platform_marketplace_plugins where id in ('old-data','old-action','old-no-draft') and visibility='CUSTOMER_PRIVATE' and owner_customer_id='old-owner'")).isEqualTo("3");
                assertThat(scalar(connection, "select manifest_json from platform_marketplace_plugin_versions where id='old-action-version'")).isEqualTo(ACTION_SOURCE);
                assertThat(scalar(connection, "select draft_manifest_json from platform_marketplace_plugins where id='old-no-draft'"))
                    .isEqualTo(scalar(connection, "select manifest_json from platform_marketplace_plugin_versions where id='old-no-draft-version'"));
                assertThat(scalar(connection, "select count(*) from platform_marketplace_plugin_versions where id like 'old-%-version'")).isEqualTo("3");
            }
        }
    }

    private static Flyway migrate(String url, String target) {
        Flyway flyway = Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(target)).load();
        flyway.migrate(); return flyway;
    }
    private static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }
    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue(); return result.getString(1);
        }
    }
    private static void oldFixture(Connection connection) throws Exception {
        String dataVersion = GuidedDataPluginSeedsTest.seed(new com.fasterxml.jackson.databind.ObjectMapper(), "HTTP_JSON").toString();
        try (var statement = connection.createStatement()) {
            statement.execute("insert into platform_customers(id,name,slug,status,platform_managed,created_at,updated_at) values('old-owner','Old owner','old-owner','ACTIVE',false,now(),now())");
        }
        try (var plugin = connection.prepareStatement("""
            insert into platform_marketplace_plugins(id,slug,display_name,plugin_type,publisher_slug,publisher_display_name,
                short_description,status,owner_customer_id,created_at,updated_at)
            values(?,?,?,?,'old-private','Old private','Preserved source','ACTIVE','old-owner',now(),now())
            """);
             var version = connection.prepareStatement("""
            insert into platform_marketplace_plugin_versions(id,plugin_id,version,release_channel,status,manifest_json,created_at,published_at)
            values(?,?,'1.0.0','PRIVATE','PUBLISHED',?,now(),now())
            """);
             var draft = connection.prepareStatement("""
            insert into platform_plugin_definition_drafts(id,plugin_id,owner_customer_id,name,plugin_type,manifest_source,revision,created_at,updated_at)
            values(?,?,'old-owner',?,?,?, ?,now(),now())
            """)) {
            for (String id : java.util.List.of("old-data", "old-action", "old-no-draft")) {
                String type = "old-action".equals(id) ? "ACTION" : "DATA";
                String source = "ACTION".equals(type) ? ACTION_SOURCE : SOURCE;
                plugin.setString(1, id); plugin.setString(2, id); plugin.setString(3, id); plugin.setString(4, type); plugin.executeUpdate();
                version.setString(1, id + "-version"); version.setString(2, id); version.setString(3, "ACTION".equals(type) ? ACTION_SOURCE : dataVersion); version.executeUpdate();
                if ("old-no-draft".equals(id)) continue;
                draft.setString(1, id + "-draft"); draft.setString(2, id); draft.setString(3, "Saved " + id); draft.setString(4, type);
                draft.setString(5, source); draft.setLong(6, "ACTION".equals(type) ? 7 : 42); draft.executeUpdate();
            }
        }
    }
}

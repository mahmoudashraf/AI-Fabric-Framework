package com.ai.fabric.platform.backend.marketplace;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceDatasetProductizationMigrationTest {

    @Test
    void v36AllowsPreexistingPolicyFolderPluginVersionWithDifferentRowId() throws Exception {
        String url = "jdbc:h2:mem:marketplace_v36_regression;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("35"))
            .load()
            .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.prepareStatement("""
                insert into platform_marketplace_plugins (
                    id,
                    slug,
                    display_name,
                    plugin_type,
                    publisher_slug,
                    publisher_display_name,
                    short_description,
                    status,
                    created_at,
                    updated_at
                ) values (
                    'mkp-data-policy-folder',
                    'policy-folder-data',
                    'Policy Folder Data',
                    'DATA',
                    'loom',
                    'Loom AI',
                    'Preexisting live plugin row.',
                    'ACTIVE',
                    current_timestamp,
                    current_timestamp
                )
                """).executeUpdate();

            connection.prepareStatement("""
                insert into platform_marketplace_plugin_versions (
                    id,
                    plugin_id,
                    version,
                    release_channel,
                    status,
                    manifest_json,
                    submitted_by_publisher_id,
                    submitted_by_actor_id,
                    reviewed_by_actor_id,
                    reviewed_at,
                    review_notes,
                    bundle_sha256,
                    created_at,
                    published_at
                ) values (
                    'custom-policy-folder-version',
                    'mkp-data-policy-folder',
                    '1.0.0',
                    'GA',
                    'PUBLISHED',
                    '{"schemaVersion":1,"pluginId":"mkp-data-policy-folder","version":"1.0.0","pluginType":"DATA"}',
                    'mpub-loom',
                    'system',
                    'system',
                    current_timestamp,
                    'Inserted by regression test.',
                    'preexisting-policy-folder-version',
                    current_timestamp,
                    current_timestamp
                )
                """).executeUpdate();
        }

        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (PreparedStatement countStatement = connection.prepareStatement("""
                select count(*)
                from platform_marketplace_plugin_versions
                where plugin_id = 'mkp-data-policy-folder'
                  and version = '1.0.0'
                """);
                 ResultSet countResult = countStatement.executeQuery()) {
                assertTrue(countResult.next());
                assertEquals(1, countResult.getInt(1));
            }

            try (PreparedStatement manifestStatement = connection.prepareStatement("""
                select id, manifest_json
                from platform_marketplace_plugin_versions
                where plugin_id = 'mkp-data-policy-folder'
                  and version = '1.0.0'
                """);
                 ResultSet manifestResult = manifestStatement.executeQuery()) {
                assertTrue(manifestResult.next());
                assertEquals("custom-policy-folder-version", manifestResult.getString("id"));
                String manifestJson = manifestResult.getString("manifest_json");
                assertTrue(manifestJson.contains("\"datasetId\": \"policy-folder-pack\""));
                assertTrue(manifestJson.contains("\"ingestionMode\": \"EXTERNAL_SYNC_FOLDER\""));
            }
        }
    }

    @Test
    void latestShopifyCompanionReadManifestDeclaresGroundedReadResolutionFlags() throws Exception {
        String url = "jdbc:h2:mem:marketplace_shopify_companion_read_flags;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (PreparedStatement manifestStatement = connection.prepareStatement("""
                select manifest_json
                from platform_marketplace_plugin_versions
                where id = 'mkv-action-shopify-companion-read-v1'
                """);
                 ResultSet manifestResult = manifestStatement.executeQuery()) {
                assertTrue(manifestResult.next());
                String manifestJson = manifestResult.getString("manifest_json");
                assertTrue(manifestJson.contains("\"actionId\": \"list_products\""));
                assertFalse(manifestJson.contains("\"actionId\": \"find_similar_products\""));
                assertFalse(manifestJson.contains("\"actionId\": \"compare_products\""));
                assertTrue(manifestJson.contains("\"groundingEligible\": true"));
                assertTrue(manifestJson.contains("\"readActionResolutionEligible\": true"));
                assertTrue(manifestJson.contains("\"anonymousAllowed\": true"));
                assertTrue(manifestJson.contains("\"actionId\": \"search_products\""));
                assertTrue(manifestJson.contains("\"actionId\": \"relationship_query\""));
                assertTrue(manifestJson.contains("\"actionId\": \"add_product_to_cart\""));
                assertTrue(manifestJson.contains("\"actionId\": \"update_cart_quantity\""));
                assertTrue(manifestJson.contains("\"requiresConfirmation\": true"));
                assertTrue(manifestJson.contains("\"description\": \"Shopper query or category hint\""));
                assertTrue(manifestJson.contains("\"description\": \"Shopper search query\""));
            }
        }
    }
}

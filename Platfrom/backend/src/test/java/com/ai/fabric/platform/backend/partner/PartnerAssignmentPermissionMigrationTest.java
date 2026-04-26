package com.ai.fabric.platform.backend.partner;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerAssignmentPermissionMigrationTest {

    @Test
    void v68BackfillsProductControlPermissionsOnlyForActiveAssignments() throws Exception {
        String url = "jdbc:h2:mem:partner_assignment_permissions_v68;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("67"))
            .load()
            .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.prepareStatement("""
                insert into partner_accounts (
                    id,
                    name,
                    status,
                    created_at,
                    updated_at
                ) values (
                    'pa-permission-backfill',
                    'Permission Backfill Partner',
                    'ACTIVE',
                    current_timestamp,
                    current_timestamp
                )
                """).executeUpdate();

            connection.prepareStatement("""
                insert into partner_store_assignments (
                    id,
                    partner_account_id,
                    shop_domain,
                    status,
                    assignment_source,
                    permissions_json,
                    created_at,
                    updated_at
                ) values (
                    'psa-active-old-permissions',
                    'pa-permission-backfill',
                    'active-old-permissions.myshopify.com',
                    'ACTIVE',
                    'SHOPIFY_ADMIN_APPROVAL',
                    '["STORE_READ","CATALOG_READ","VERIFICATION_READ"]',
                    current_timestamp,
                    current_timestamp
                )
                """).executeUpdate();

            connection.prepareStatement("""
                insert into partner_store_assignments (
                    id,
                    partner_account_id,
                    shop_domain,
                    status,
                    assignment_source,
                    permissions_json,
                    created_at,
                    updated_at
                ) values (
                    'psa-revoked-old-permissions',
                    'pa-permission-backfill',
                    'revoked-old-permissions.myshopify.com',
                    'REVOKED',
                    'SHOPIFY_ADMIN_APPROVAL',
                    '["STORE_READ","CATALOG_READ","VERIFICATION_READ"]',
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
            String activePermissions = permissionsJson(connection, "psa-active-old-permissions");
            assertTrue(activePermissions.contains("\"PRODUCT_CONFIG_READ\""));
            assertTrue(activePermissions.contains("\"PRODUCT_CONFIG_WRITE\""));
            assertTrue(activePermissions.contains("\"STOREFRONT_SURFACE_CONTROL\""));
            assertTrue(activePermissions.contains("\"KNOWLEDGE_SOURCE_CONTROL\""));
            assertTrue(activePermissions.contains("\"SUPPORT_MANAGE\""));
            assertTrue(activePermissions.contains("\"PRODUCT_PAUSE_RESUME\""));

            String revokedPermissions = permissionsJson(connection, "psa-revoked-old-permissions");
            assertFalse(revokedPermissions.contains("\"PRODUCT_CONFIG_READ\""));
            assertFalse(revokedPermissions.contains("\"STOREFRONT_SURFACE_CONTROL\""));
        }
    }

    private String permissionsJson(Connection connection, String assignmentId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            select permissions_json
            from partner_store_assignments
            where id = ?
            """)) {
            statement.setString(1, assignmentId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString("permissions_json");
            }
        }
    }
}

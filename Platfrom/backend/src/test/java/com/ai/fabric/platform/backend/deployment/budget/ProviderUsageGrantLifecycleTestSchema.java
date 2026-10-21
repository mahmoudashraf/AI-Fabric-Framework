package com.ai.fabric.platform.backend.deployment.budget;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

final class ProviderUsageGrantLifecycleTestSchema {
    private ProviderUsageGrantLifecycleTestSchema() { }

    static void migrate(DataSource source) throws Exception {
        try (var connection = source.getConnection()) {
            if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                try (var statement = connection.createStatement()) {
                    statement.execute(new ClassPathResource("db/migration/V204__provider_usage_grant_void_unused.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
                    statement.execute(new ClassPathResource("db/migration/V207__provider_usage_validity_extensions.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
                }
                return;
            }
        }
        // H2 has no PostgreSQL partial indexes or plpgsql. Real migration/immutability checks run on PostgreSQL.
        var jdbc = new JdbcTemplate(source);
        String oldConstraint = jdbc.queryForObject("""
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE TABLE_NAME='PROVIDER_USAGE_GRANTS' AND CONSTRAINT_TYPE='UNIQUE'
            """, String.class);
        jdbc.execute("ALTER TABLE provider_usage_grants DROP CONSTRAINT " + oldConstraint);
        jdbc.execute("ALTER TABLE provider_usage_grants ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (lifecycle_status IN ('ACTIVE','VOID_UNUSED'))");
        jdbc.execute("ALTER TABLE provider_usage_grants ADD COLUMN active_scope INTEGER GENERATED ALWAYS AS (CASE WHEN lifecycle_status='ACTIVE' THEN 1 ELSE NULL END)");
        jdbc.execute("CREATE UNIQUE INDEX ux_provider_usage_active_grant_scope ON provider_usage_grants(campaign_id,customer_id,target_profile_id,environment_name,active_scope)");
        jdbc.execute("ALTER TABLE provider_usage_allowance_events ADD COLUMN grant_id VARCHAR(96) REFERENCES provider_usage_grants(id)");
        jdbc.execute("""
            CREATE TABLE provider_usage_grant_void_receipts (
                grant_id VARCHAR(96) PRIMARY KEY REFERENCES provider_usage_grants(id),receipt_id VARCHAR(96) NOT NULL UNIQUE,
                operation_key VARCHAR(128) NOT NULL UNIQUE,grant_fingerprint VARCHAR(64) NOT NULL,command_fingerprint VARCHAR(64) NOT NULL,
                receipt_fingerprint VARCHAR(64) NOT NULL,payload_json TEXT NOT NULL,actor_id VARCHAR(255) NOT NULL,
                actor_role VARCHAR(64) NOT NULL CHECK (actor_role='PLATFORM_ADMIN'),created_at TIMESTAMP WITH TIME ZONE NOT NULL)
            """);
        String validity = new ClassPathResource("db/migration/V207__provider_usage_validity_extensions.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(
            validity.substring(0, validity.indexOf("CREATE TRIGGER")).getBytes(StandardCharsets.UTF_8))).execute(source);
    }
}

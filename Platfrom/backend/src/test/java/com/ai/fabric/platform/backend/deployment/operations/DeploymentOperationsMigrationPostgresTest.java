package com.ai.fabric.platform.backend.deployment.operations;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class DeploymentOperationsMigrationPostgresTest {
    @Test void completeProductionMigrationChainPreservesFinancialHistoryAndCreatesOnlyCoreAuthority() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            var old = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target("209").load();
            old.migrate();
            String before;
            try (var connection = postgres.createConnection(""); var sql = connection.createStatement()) {
                sql.execute("""
                    INSERT INTO provider_usage_grants
                    (id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,approved_by,approved_role,approved_at)
                    VALUES ('history-check','campaign','customer','target','staging',repeat('a',64),'{}','admin','PLATFORM_ADMIN',CURRENT_TIMESTAMP)
                    """);
                try (var result = sql.executeQuery("SELECT row_to_json(g)::text FROM provider_usage_grants g WHERE id='history-check'")) {
                    result.next(); before = result.getString(1);
                }
            }
            var current = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load();
            assertThat(current.migrate().migrationsExecuted).isEqualTo(2);
            current.validate();
            try (var connection = postgres.createConnection(""); var sql = connection.createStatement()) {
                try (var result = sql.executeQuery("SELECT row_to_json(g)::text FROM provider_usage_grants g WHERE id='history-check'")) {
                    result.next(); assertThat(result.getString(1)).isEqualTo(before);
                }
                try (var result = sql.executeQuery("SELECT COUNT(*) FROM platform_release_authorizations")) {
                    result.next(); assertThat(result.getInt(1)).isZero();
                }
                try (var result = sql.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.table_constraints c
                    JOIN information_schema.constraint_column_usage u ON c.constraint_name=u.constraint_name
                    WHERE c.table_name='platform_release_authorizations' AND c.constraint_type='FOREIGN KEY'
                      AND (u.table_name LIKE 'deployment_admission%' OR u.table_name='deployment_capacity_reservations')
                    """)) { result.next(); assertThat(result.getInt(1)).isZero(); }
            }
        }
    }
}

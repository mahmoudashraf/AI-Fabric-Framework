package com.ai.fabric.platform.backend.deployment.retirement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TargetRetirementMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void isolatedMigrationPreservesHistoryAndEnforcesScopedKeysAndAttemptBounds() throws Exception {
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            statement.execute("create table preserved_history (id varchar(64) primary key)");
            statement.execute("insert into preserved_history values ('release-version-audit-source')");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V161__target_retirement_operations.sql"));
            statement.executeUpdate(insert("one", "staging", "same-key", 1));
            assertThatThrownBy(() -> statement.executeUpdate(insert("duplicate", "staging", "same-key", 1)))
                .isInstanceOf(SQLException.class);
            statement.executeUpdate(insert("other-target", "another-staging", "same-key", 1));
            assertThatThrownBy(() -> statement.executeUpdate(insert("unbounded", "staging", "other-key", 9)))
                .isInstanceOf(SQLException.class);
            try (var result = statement.executeQuery("select id from preserved_history")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("release-version-audit-source");
            }
        }
    }
    private String insert(String id, String target, String key, int attempt) {
        return """
            insert into platform_target_retirement_operations
              (id, customer_id, deployment_id, target_profile_id, operation_key, request_fingerprint,
               requested_by_actor_id, requested_by_role, request_json, snapshot_json, result_json,
               state, step, drain_deadline, attempt, created_at, updated_at)
            values ('%s', 'customer', 'deployment', '%s', '%s', 'fingerprint', 'actor', 'CUSTOMER_ADMIN',
                    '{}', '{}', '{}', 'PLANNED', 'DRAIN', current_timestamp, %d, current_timestamp, current_timestamp)
            """.formatted(id, target, key, attempt);
    }
}

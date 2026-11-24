package com.ai.fabric.runtime.config;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.assertThat;

class RuntimeGoalMigrationTest {
    @Test
    void addsSchedulingWithoutRewritingExistingProtectedExecutionAndReceipts() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        for (String migration : new String[] {"V2__ai_specialist_execution.sql", "V27__workflow_execution_checkpoint.sql"})
            new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/" + migration)).execute(source);
        jdbc.update("""
            INSERT INTO ai_specialist_execution
              (invocation_id, specialist_name, specialist_version, specialist_content_hash, access_fingerprint,
               request_fingerprint, protected_request, protected_result, status, deadline, created_at, updated_at,
               expires_at, attempt_count, version, protected_workflow_checkpoint)
            VALUES ('existing', 'configured-authoring', '1', 'hash', 'owner', 'request', 'encrypted-request',
                    'encrypted-result', 'COMPLETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 1, 7, 'encrypted-receipts')
            """);
        var before = jdbc.queryForMap("SELECT * FROM ai_specialist_execution WHERE invocation_id='existing'");
        var migration = new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/V29__workflow_goal_continuation.sql"));
        migration.execute(source);
        migration.execute(source);
        var after = jdbc.queryForMap("SELECT * FROM ai_specialist_execution WHERE invocation_id='existing'");
        assertThat(after).containsAllEntriesOf(before);
        assertThat(jdbc.queryForObject("SELECT goal_authority_reference FROM ai_specialist_execution WHERE invocation_id='existing'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT goal_notification_pending FROM ai_specialist_execution WHERE invocation_id='existing'", Boolean.class)).isFalse();
        jdbc.update("UPDATE ai_specialist_execution SET status='YIELDED', goal_authority_reference='owned-goal', next_eligible_at=CURRENT_TIMESTAMP, goal_notification_pending=TRUE WHERE invocation_id='existing'");
        migration.execute(source);
        assertThat(jdbc.queryForMap("SELECT status, goal_authority_reference, goal_notification_pending FROM ai_specialist_execution WHERE invocation_id='existing'"))
            .containsEntry("STATUS", "YIELDED").containsEntry("GOAL_AUTHORITY_REFERENCE", "owned-goal")
            .containsEntry("GOAL_NOTIFICATION_PENDING", true);
        assertThat(jdbc.queryForObject("SELECT protected_workflow_checkpoint FROM ai_specialist_execution WHERE invocation_id='existing'", String.class)).isEqualTo("encrypted-receipts");
    }
}

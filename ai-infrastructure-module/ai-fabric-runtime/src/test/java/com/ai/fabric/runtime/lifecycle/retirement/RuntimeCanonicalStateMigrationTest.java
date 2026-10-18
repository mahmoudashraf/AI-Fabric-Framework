package com.ai.fabric.runtime.lifecycle.retirement;

import static org.assertj.core.api.Assertions.*;

import ai.fabric.chat.control.ConversationScope;
import ai.fabric.chat.control.ConversationControlViolationException;
import ai.fabric.chat.control.DefaultConversationControlGateway;
import ai.fabric.chat.control.JdbcConversationControlStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class RuntimeCanonicalStateMigrationTest {
    @Test
    void addsStateToExistingCanonicalSchemaAndPersistsAcrossStoreRecreationWithoutShadowSessions() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        apply(source, "V10__conversation_handover.sql");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var gateway = new DefaultConversationControlGateway(new JdbcConversationControlStore(source, mapper, false));
        var scope = new ConversationScope("tenant-1", "dep-123", "owned", "alice");
        gateway.status(scope);

        apply(source, "V16__conversation_orchestration_state.sql");
        gateway.updateOrchestrationState(scope, 0L, metadata -> Map.of("actionDraft", Map.of("action", "collect_contact")));
        apply(source, "V16__conversation_orchestration_state.sql");

        var restarted = new DefaultConversationControlGateway(new JdbcConversationControlStore(source, mapper, false));
        assertThat(restarted.orchestrationState(scope, 0L)).get().satisfies(state -> {
            assertThat(state.revision()).isOne();
            assertThat(state.metadata()).containsEntry("actionDraft", Map.of("action", "collect_contact"));
        });
        assertThatThrownBy(() -> restarted.orchestrationState(new ConversationScope("tenant-1", "dep-123", "owned", "mallory"), 0L))
            .isInstanceOf(ConversationControlViolationException.class)
            .extracting(exception -> ((ConversationControlViolationException) exception).code())
            .isEqualTo("CONVERSATION_OWNER_MISMATCH");
        assertThat(new RuntimeRetirementSchema(source).supports("chat_sessions")).isFalse();
        assertThat(new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM ai_conversation_control", Long.class)).isOne();
    }

    @Test
    void schemaRejectsOrphansAndCascadesOnlyTheDeletedConversationScope() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        apply(source, "V10__conversation_handover.sql");
        apply(source, "V16__conversation_orchestration_state.sql");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var gateway = new DefaultConversationControlGateway(new JdbcConversationControlStore(source, mapper, false));
        var first = new ConversationScope("tenant-1", "dep-123", "same-id", "alice");
        var second = new ConversationScope("tenant-2", "dep-456", "same-id", "bob");
        for (ConversationScope scope : new ConversationScope[]{first, second}) {
            gateway.status(scope);
            gateway.updateOrchestrationState(scope, 0L, metadata -> Map.of("actionDraft", Map.of("action", "collect_contact")));
        }
        var jdbc = new JdbcTemplate(source);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO ai_conversation_orchestration_state VALUES ('tenant-1','dep-123','orphan','alice',1,'{}',CURRENT_TIMESTAMP)
            """)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM ai_conversation_control WHERE tenant_id = 'tenant-1' AND deployment_id = 'dep-123' AND conversation_id = 'same-id'");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_orchestration_state", Long.class)).isOne();
        assertThat(gateway.orchestrationState(second, 0L)).isPresent();
    }

    @Test
    void addsOptimisticVersionColumnWhenLegacyChatSessionsExist() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE chat_sessions (
              id VARCHAR(160) PRIMARY KEY,
              owner_id VARCHAR(256) NOT NULL,
              status VARCHAR(32) NOT NULL,
              created_at TIMESTAMP NOT NULL,
              last_interaction_at TIMESTAMP NOT NULL,
              session_metadata TEXT
            )
            """);
        jdbc.update("""
            INSERT INTO chat_sessions (id, owner_id, status, created_at, last_interaction_at, session_metadata)
            VALUES ('conversation-1', 'owner-1', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}')
            """);

        apply(source, "V10__conversation_handover.sql");
        apply(source, "V16__conversation_orchestration_state.sql");

        assertThat(jdbc.queryForObject(
            "SELECT row_version FROM chat_sessions WHERE id = 'conversation-1'",
            Long.class
        )).isZero();
    }

    private void apply(DriverManagerDataSource source, String migration) {
        new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/" + migration)).execute(source);
    }
}

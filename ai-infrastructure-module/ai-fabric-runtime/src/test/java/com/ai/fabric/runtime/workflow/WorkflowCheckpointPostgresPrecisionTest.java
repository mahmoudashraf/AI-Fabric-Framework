package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.state.*;
import ai.fabric.intent.orchestration.workflow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.time.*;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class WorkflowCheckpointPostgresPrecisionTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static String hash(char value) { return String.valueOf(value).repeat(64); }

    @ParameterizedTest @ValueSource(ints = {123456132, 123456823})
    void databaseRoundingCannotRejectValidWorkOrWidenItsDeadline(int nanos) {
        var now = Instant.parse("2026-11-24T12:00:00Z");
        var requested = now.plusSeconds(120).plusNanos(nanos);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var security = new DurableExecutionSecurity(mapper, "workflow-test-encryption-secret-0001", "workflow-test-fingerprint-secret-0002");
        try (var source = new HikariDataSource()) {
            source.setJdbcUrl(POSTGRES.getJdbcUrl()); source.setUsername(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
            var repository = new JdbcDurableExecutionRepository(source, true);
            String id = "precision-" + nanos;
            var record = DurableExecutionRecord.queued(id, SpecialistId.of("workflow-api", "1"), hash('a'), hash('b'),
                hash(nanos % 1000 < 500 ? 'c' : 'e'), hash('d'), "protected-request", requested, now, Duration.ofDays(1)).claimed("owner", now, requested);
            repository.create(record);
            var actual = repository.findById(id).orElseThrow();
            assertThat(actual.deadline()).isNotEqualTo(requested);
            var store = new DurableWorkflowCheckpointStore(repository, security, mapper, Clock.fixed(now, ZoneOffset.UTC), actual);
            if (nanos % 1000 < 500) {
                assertThat(actual.deadline()).isBefore(requested);
                assertThatThrownBy(() -> store.create(checkpoint(id, requested))).hasMessage("WORKFLOW_EXECUTION_SCOPE_MISMATCH");
            } else assertThat(actual.deadline()).isAfter(requested);
            var bounded = RuntimeConfiguredWorkflowDispatcher.persistedDeadline(requested, actual.deadline());
            assertThat(bounded).isBeforeOrEqualTo(requested).isBeforeOrEqualTo(actual.deadline());
            assertThatThrownBy(() -> store.create(checkpoint(id, actual.deadline().plusNanos(1))))
                .hasMessage("WORKFLOW_EXECUTION_SCOPE_MISMATCH");
            assertThat(store.create(checkpoint(id, bounded))).isTrue();
            assertThat(store.find(id).orElseThrow().deadline()).isEqualTo(bounded);
        }
    }
    static WorkflowCheckpoint checkpoint(String id, Instant deadline) {
        return new WorkflowCheckpoint(id, hash('d'), hash('b'), hash('a'), deadline, 0,
            WorkflowAllowance.Usage.empty(), List.of(), WorkflowCheckpoint.State.RUNNING, null, List.of(), null);
    }
}

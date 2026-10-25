package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RuntimeReleaseRetirementPostgresTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private String database;
    private RuntimeRetirementTestFixture f;

    @BeforeEach
    void migrateFreshDatabase() {
        database = "release_gate_" + UUID.randomUUID().toString().replace("-", "");
        admin().execute("CREATE DATABASE " + database);
        var source = new DriverManagerDataSource("jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432)
            + "/" + database, PG.getUsername(), PG.getPassword());
        f = new RuntimeRetirementTestFixture(source, true, true, List.of(), false);
    }

    @AfterEach
    void dropDatabase() {
        if (database != null) admin().execute("DROP DATABASE " + database + " WITH (FORCE)");
    }

    @Test
    void sameVersionColdReleasesIgnoreOldGateStatesAndUnsettledPermitsWithoutDeletingHistoryOrAiData() {
        f.session("retained-ai-history", "alice", "CLOSED", "{\"retained\":true}");
        f.jdbc.update("UPDATE runtime_target_retirement SET deployment_id='legacy-deployment', release_id='legacy-release', state='AUTHORITY_REVOKED', authority_revoked=TRUE");
        f.jdbc.update("INSERT INTO runtime_target_retirement_turn VALUES ('legacy-permit',0,'legacy','owner',CURRENT_TIMESTAMP)");
        var legacyGate = f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement");
        var legacyTurns = f.jdbc.queryForList("SELECT * FROM runtime_target_retirement_turn");
        for (State state : List.of(State.ACTIVE, State.DRAINING, State.AUTHORITY_REVOKED)) {
            var old = f.replacement("old-" + state.name());
            old.service().beginIngress("accepted-before-native-stop", false);
            if (state != State.ACTIVE) old.service().command(command(old.target(), 1, 0, CommandType.BEGIN_DRAIN));
            if (state == State.AUTHORITY_REVOKED) old.service().command(command(old.target(), 2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));
            var oldGate = f.jdbc.queryForMap("SELECT * FROM runtime_release_retirement_gate WHERE release_id=?", old.target().releaseId());
            var replacement = f.replacement("new-" + state.name());
            assertThat(replacement.service().status(replacement.target()).state()).isEqualTo(State.ACTIVE);
            replacement.service().requireNewSessionAllowed();
            replacement.service().command(command(replacement.target(), 1, 0, CommandType.BEGIN_DRAIN));
            var snapshot = replacement.service().snapshot(binding(replacement.target(), 1));
            assertThat(snapshot.inFlightRequests()).isZero();
            assertThat(snapshot.localDrainComplete()).isTrue();
            replacement.service().command(command(replacement.target(), 2, 1, CommandType.FENCE_AUTHORITY));
            assertThat(f.jdbc.queryForMap("SELECT * FROM runtime_release_retirement_gate WHERE release_id=?", old.target().releaseId())).isEqualTo(oldGate);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_turn WHERE release_id=?", Long.class, old.target().releaseId())).isOne();
        }
        assertThat(f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement")).isEqualTo(legacyGate);
        assertThat(f.jdbc.queryForList("SELECT * FROM runtime_target_retirement_turn")).isEqualTo(legacyTurns);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE id='retained-ai-history'", Long.class)).isOne();
    }

    @Test
    void concurrentFirstAdmissionCreatesOneScopedGateAndReplaysOneExactCommand() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<CommandResult> invoke = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out.");
                return f.restart().command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            };
            var first = executor.submit(invoke);
            var second = executor.submit(invoke);
            start.countDown();
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.replay()).isNotEqualTo(b.replay());
            assertThat(a.current()).isEqualTo(b.current());
        }
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_gate", Long.class)).isOne();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_command", Long.class)).isOne();
    }

    private Binding binding(Target target, long epoch) { return new Binding(target, epoch, "same-operation", "a".repeat(64)); }
    private Command command(Target target, long epoch, long expected, CommandType type) {
        return new Command(binding(target, epoch), expected, type, Instant.parse("2030-01-01T00:00:00Z"),
            type == CommandType.RECORD_AUTHORITY_REVOCATION ? "core-native-journal:old-writer-stopped" : null);
    }
    private JdbcTemplate admin() {
        return new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    }
}

package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.ai.fabric.runtime.lifecycle.retirement.RuntimeColdMaintenanceTransitionTest.Case;
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

@Testcontainers
class RuntimeColdMaintenanceTransitionPostgresTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private String database;
    private Case c;

    @BeforeEach
    void migrateFreshDatabase() {
        database = "cold_" + UUID.randomUUID().toString().replace("-", "");
        admin().execute("CREATE DATABASE " + database);
        var source = new DriverManagerDataSource("jdbc:postgresql://" + PG.getHost() + ":"
            + PG.getMappedPort(5432) + "/" + database, PG.getUsername(), PG.getPassword());
        c = new Case(new RuntimeRetirementTestFixture(source, true, true, List.of(), false));
    }

    @AfterEach
    void dropFreshDatabase() {
        if (database != null) admin().execute("DROP DATABASE " + database + " WITH (FORCE)");
    }

    @Test
    void migratedPostgresColdAdoptionPreservesWalletHistoryAndDeadLetters() {
        c.f.jdbc.execute("CREATE TABLE retained_wallet (id VARCHAR(40), consumed INTEGER, approval_fingerprint VARCHAR(64))");
        c.f.jdbc.update("INSERT INTO retained_wallet VALUES ('same-wallet',12,?)", "e".repeat(64));
        var walletBefore = c.f.jdbc.queryForMap("SELECT * FROM retained_wallet");
        c.f.session("closed-history", "owner", "CLOSED", "{}");
        c.f.jdbc.update("INSERT INTO ai_specialist_execution VALUES ('SUCCEEDED')");
        c.f.jdbc.update("INSERT INTO ai_indexing_queue VALUES ('DEAD_LETTER')");
        var oldSnapshot = c.f.service.snapshot(c.f.binding(1));
        String oldJson = c.f.jdbc.queryForObject("SELECT snapshot_json FROM runtime_target_retirement_snapshot", String.class);

        var result = c.service.coldMaintenanceTransition(c.command());

        assertThat(result.transition().previous().state()).isEqualTo(State.DRAINING);
        assertThat(result.transition().previous().authorityRevoked()).isFalse();
        assertThat(result.transition().current().state()).isEqualTo(State.ACTIVE);
        assertThat(result.transition().current().fenceEpoch()).isEqualTo(2);
        assertThat(result.observedInventory().inventoryComplete()).isFalse();
        assertThat(result.observedInventory().workerFencingComplete()).isFalse();
        assertThat(result.observedInventory().localDrainComplete()).isFalse();
        assertThat(result.evidenceKind()).isEqualTo("PHYSICAL_STOP_COLD_MAINTENANCE_V1");
        assertThat(c.f.jdbc.queryForMap("SELECT * FROM retained_wallet")).isEqualTo(walletBefore);
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE id = 'closed-history'", Long.class)).isOne();
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_specialist_execution WHERE status = 'SUCCEEDED'", Long.class)).isOne();
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_indexing_queue WHERE status = 'DEAD_LETTER'", Long.class)).isOne();
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_retirement_snapshot", Long.class)).isOne();
        assertThat(c.f.jdbc.queryForObject("SELECT snapshot_json FROM runtime_target_retirement_snapshot WHERE snapshot_id = ?",
            String.class, oldSnapshot.snapshotId())).isEqualTo(oldJson);
        assertThat(c.service.status(c.next)).isEqualTo(result.transition().current());
    }

    @Test
    void postgresLockSerializesAdoptionAndRestartReplaysExactDurableReceipt() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<ColdMaintenanceTransitionResult> invoke = () -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test start latch timed out");
                return c.restart().coldMaintenanceTransition(c.command());
            };
            var first = executor.submit(invoke);
            var second = executor.submit(invoke);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.transition().replay()).isNotEqualTo(b.transition().replay());
            assertThat(a.transition().current()).isEqualTo(b.transition().current());
            var replay = c.restart().coldMaintenanceTransition(c.command());
            assertThat(replay.transition().replay()).isTrue();
            assertThat(replay.transition().transitionedAt()).isEqualTo(a.transition().transitionedAt());
            assertThat(replay.observedInventory()).isEqualTo(a.observedInventory());
        }
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_generation_transition", Long.class)).isOne();
        assertThat(c.service.status(c.next).fenceEpoch()).isEqualTo(2);
    }

    @Test
    void postgresLiveWorkRejectsWithoutChangingBindingOrWritingReceipt() {
        var before = c.f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement");
        c.f.jdbc.update("INSERT INTO ai_specialist_execution VALUES ('RUNNING')");

        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.command()))
            .hasMessageContaining("COLD_MAINTENANCE_WORK_REMAINS_OR_UNOBSERVED");

        assertThat(c.f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement")).isEqualTo(before);
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_generation_transition", Long.class)).isZero();
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_retirement_snapshot", Long.class)).isZero();
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_specialist_execution WHERE status = 'RUNNING'", Long.class)).isOne();
    }

    private JdbcTemplate admin() {
        return new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    }
}

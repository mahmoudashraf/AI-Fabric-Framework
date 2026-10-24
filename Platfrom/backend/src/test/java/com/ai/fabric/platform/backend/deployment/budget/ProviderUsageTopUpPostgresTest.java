package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.security.PlatformRole;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageTopUpContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** One real CP PostgreSQL qualification; runtime responses remain synthetic and no provider traffic is sent. */
class ProviderUsageTopUpPostgresTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    ProviderUsageTopUpServiceTest h;

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @BeforeEach void setup() throws Exception {
        // Composition deliberately avoids inheriting/rerunning the H2 test matrix on PostgreSQL.
        h = new ProviderUsageTopUpServiceTest() {
            @Override Fixture fixture() {
                return new Fixture() {
                    @Override DataSource freshDataSource() throws Exception {
                        var source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl());
                        source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
                        String schema = "top_up_" + UUID.randomUUID().toString().replace("-", "");
                        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                            statement.execute("CREATE SCHEMA " + schema);
                        }
                        source.setCurrentSchema(schema); return source;
                    }
                };
            }

            @Override void migrateTopUps() {
                var migration = Flyway.configure().dataSource(f.source).locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("208").target("209").load().migrate();
                assertThat(migration.migrationsExecuted).isEqualTo(1);
                assertThat(f.jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='209'", Boolean.class)).isTrue();
            }
        };
        h.setup();
    }

    @AfterEach void cleanup() { if (h != null) h.cleanup(); }

    @Test void v209ProtectsFundingHistoryAndSerializesPendingCapsAndExactServiceReconciliation() throws Exception {
        var jdbc = h.f.jdbc; var before = h.originalHistory(); var quota = h.f.service.readQuota("grant");
        assertThat(quota.availableForAllocation()).isEqualTo(new Limits(0, 0, 0));
        assertThatThrownBy(() -> h.topUps.plan(h.request("old-funding", h.active.caps(), h.f.runtimeGrant().ownerApprovalEvidenceRef())))
            .hasMessageContaining("NEW_FUNDING_REQUIRED");

        var first = h.topUps.plan(h.request("pg-first")); var stale = h.topUps.plan(h.request("pg-stale"));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> {
            ProviderUsageTopUpServiceTest.noTransaction(); h.posts.incrementAndGet(); entered.countDown();
            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
            h.runtimeReceipt = h.receipt(call.getArgument(1)); h.runtimeLimits = h.runtimeReceipt.effectiveLimits();
            throw new IllegalStateException("synthetic lost response after runtime apply");
        }).when(h.runtime).topUp(anyString(), any(), anyMap());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> {
                try {
                    ProviderUsageAllowanceContract.authenticate(PlatformRole.PLATFORM_ADMIN);
                    return h.topUps.execute("pg-first", first.command());
                } finally { h.cleanup(); }
            });
            try {
                assertThat(entered.await(15, TimeUnit.SECONDS)).isTrue();
                assertThat(h.topUps.execute("pg-first", first.command()).status()).isEqualTo(Status.UNKNOWN);
                assertThatThrownBy(() -> h.topUps.execute("pg-stale", stale.command())).hasMessageContaining("RECONCILIATION_REQUIRED");
                assertThatThrownBy(() -> h.topUps.plan(h.request("pg-blocked"))).hasMessageContaining("RECONCILIATION_REQUIRED");
                assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM provider_usage_top_up_reviews r
                    WHERE r.approval_id=? AND EXISTS
                      (SELECT 1 FROM provider_usage_top_up_events e WHERE e.top_up_id=r.top_up_id AND e.event_type='INTENT')
                    AND NOT EXISTS
                      (SELECT 1 FROM provider_usage_top_up_events e WHERE e.top_up_id=r.top_up_id AND e.event_type='ACK')
                    """, Integer.class, h.active.approvalId())).isEqualTo(1);
            } finally { release.countDown(); }
            assertThat(running.get(20, TimeUnit.SECONDS).status()).isEqualTo(Status.UNKNOWN);
        }

        h.topUps = h.service();
        assertThat(h.topUps.execute("pg-first", first.command()).status()).isEqualTo(Status.UNKNOWN);
        var reconciled = h.topUps.reconcile("pg-first");
        assertThat(reconciled.status()).isEqualTo(Status.ACKNOWLEDGED);
        assertThat(reconciled.receipt()).isEqualTo(h.runtimeReceipt);
        assertThat(h.topUps.reconcile("pg-first")).isEqualTo(reconciled);
        assertThat(h.posts).hasValue(1);
        verify(h.runtime, times(1)).readTopUp(anyString(), eq("pg-first"), anyMap());

        // Even a stale runtime readback cannot bypass the CP's acknowledged cap head under its shared allocation locks.
        var head = h.runtimeLimits; h.runtimeLimits = h.active.caps();
        assertThatThrownBy(() -> h.topUps.execute("pg-stale", stale.command())).hasMessageContaining("EFFECTIVE_LIMITS_CHANGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_top_up_events WHERE top_up_id='pg-stale'", Integer.class)).isZero();
        h.runtimeLimits = head;
        assertThatThrownBy(() -> h.topUps.plan(h.request("pg-funding-reused", head, first.command().review().approvalEvidenceRef())))
            .hasMessageContaining("NEW_FUNDING_REQUIRED");

        var next = h.topUps.plan(h.request("pg-next", head, "new-pg-owner-funding-next"));
        doAnswer(call -> {
            ProviderUsageTopUpServiceTest.noTransaction(); h.posts.incrementAndGet();
            h.runtimeReceipt = h.receipt(call.getArgument(1)); h.runtimeLimits = h.runtimeReceipt.effectiveLimits(); return h.runtimeReceipt;
        }).when(h.runtime).topUp(anyString(), any(), anyMap());
        var acknowledged = h.topUps.execute("pg-next", next.command());
        assertThat(acknowledged.status()).isEqualTo(Status.ACKNOWLEDGED);
        assertThat(acknowledged.effectiveLimits()).isEqualTo(add(add(h.active.caps(), ProviderUsageTopUpServiceTest.ADDITION), ProviderUsageTopUpServiceTest.ADDITION));
        assertThat(acknowledged.originalExpiresAt()).isEqualTo(h.active.expiresAt());
        assertThat(acknowledged.effectiveExpiresAt()).isEqualTo(h.active.effectiveExpiresAt());
        assertThat(h.topUps.execute("pg-next", next.command())).isEqualTo(acknowledged); assertThat(h.posts).hasValue(2);

        for (var plan : List.of(first, next)) {
            var stored = h.f.codec.read(jdbc.queryForObject("SELECT payload_json FROM provider_usage_top_up_reviews WHERE top_up_id=?",
                String.class, plan.command().review().topUpId()), Review.class);
            assertThat(stored.command()).isEqualTo(plan.command());
            assertThat(stored.actorId()).isEqualTo(plan.command().runtimeCommand().authorizedBy());
            assertThat(stored.command().runtimeCommand().approvalEvidenceRef()).isEqualTo(plan.command().review().approvalEvidenceRef())
                .isNotEqualTo(h.f.runtimeGrant().ownerApprovalEvidenceRef());
            assertThat(stored.command().runtimeCommand().payerRef()).isEqualTo("synthetic-new-payer");
            var event = h.f.codec.read(jdbc.queryForObject("SELECT payload_json FROM provider_usage_top_up_events WHERE top_up_id=? AND event_type='ACK'",
                String.class, plan.command().review().topUpId()), Event.class);
            assertThat(event.actorId()).isEqualTo(stored.actorId());
            assertThat(event.receipt().authorizedBy()).isEqualTo(stored.actorId());
            assertThat(event.receipt().approvalEvidenceRef()).isEqualTo(stored.command().runtimeCommand().approvalEvidenceRef());
            assertThat(event.receipt().payerRef()).isEqualTo(stored.command().runtimeCommand().payerRef());
        }
        assertThat(jdbc.queryForList("SELECT event_type FROM provider_usage_top_up_events WHERE top_up_id='pg-first'", String.class))
            .containsExactlyInAnyOrder("INTENT", "UNKNOWN", "ACK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grants", Integer.class)).isEqualTo(1);
        assertThat(h.f.service.readQuota("grant")).isEqualTo(quota);
        assertThat(h.originalHistory()).isEqualTo(before);
        assertThatThrownBy(() -> jdbc.execute("""
            INSERT INTO provider_usage_top_up_reviews
            SELECT 'duplicate-funding-evidence',approval_id,approval_evidence_ref,review_fingerprint,record_fingerprint,payload_json
            FROM provider_usage_top_up_reviews WHERE top_up_id='pg-first'
            """)).isInstanceOf(DataIntegrityViolationException.class);

        for (String table : List.of("provider_usage_top_up_reviews", "provider_usage_top_up_events")) {
            for (String sql : List.of("UPDATE " + table + " SET payload_json='{}'", "DELETE FROM " + table, "TRUNCATE " + table + " CASCADE"))
                assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Provider usage ledger records are append-only");
        }
        assertThat(h.topUps.read("pg-next")).isEqualTo(acknowledged);
        assertThat(h.originalHistory()).isEqualTo(before);
    }
}

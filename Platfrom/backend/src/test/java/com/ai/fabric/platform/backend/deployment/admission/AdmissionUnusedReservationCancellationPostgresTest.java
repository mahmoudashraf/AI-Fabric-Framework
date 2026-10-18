package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

@Testcontainers(disabledWithoutDocker = true)
class AdmissionUnusedReservationCancellationPostgresTest extends AdmissionUnusedReservationCancellationTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override void database() {
        String schema = "unused_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl() + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema=" + schema, DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql"),
            new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void inFlightApplyCommitWinsAndCancellationRemainsFullyCharged() throws Exception {
        var consumed = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var apply = workers.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(tx -> {
                jdbc.queryForObject("SELECT id FROM platform_deployments WHERE id='deployment' FOR UPDATE", String.class);
                consumed.countDown();
                try { if (!commit.await(10, TimeUnit.SECONDS)) throw new AssertionError("commit timeout"); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                // The actual R7 source prerequisite read locks the reservation/authorization join before bindRelease.
                var matched = store.customerPrerequisiteReservations(sourceScope);
                assertThat(matched).hasSize(1);
                store.requireCustomerAuthorization(matched.getFirst(), sourceScope);
                bindInTransaction("concurrent-release");
                return true;
            }));
            assertThat(consumed.await(10, TimeUnit.SECONDS)).isTrue();
            var cancel = workers.submit(this::cancel);
            assertThatThrownBy(() -> cancel.get(3, TimeUnit.SECONDS)).hasRootCauseMessage("409 CONFLICT \"UNUSED_CANCELLATION_DEPLOYMENT_BUSY\"");
            // The failed cancellation must not retain the mutex while the source command continues.
            store.approveManifest(admitted.manifest(), "staff", NOW);
            commit.countDown();
            assertThat(apply.get(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(this::cancel).hasMessageContaining("EFFECT_HISTORY_PRESENT");
        }
        assertHeld();
    }

    @Test void rolledBackApplyLeavesNoEffectAndCancellationCanWin() throws Exception {
        var consumed = new CountDownLatch(1);
        var rollback = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var apply = workers.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(tx -> {
                bindInTransaction("rolled-back-release"); consumed.countDown();
                try { if (!rollback.await(10, TimeUnit.SECONDS)) throw new AssertionError("rollback timeout"); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                tx.setRollbackOnly(); return true;
            }));
            assertThat(consumed.await(10, TimeUnit.SECONDS)).isTrue();
            var cancel = workers.submit(this::cancel);
            assertThatThrownBy(() -> cancel.get(3, TimeUnit.SECONDS)).hasRootCauseMessage("409 CONFLICT \"UNUSED_CANCELLATION_DEPLOYMENT_BUSY\"");
            rollback.countDown(); apply.get(10, TimeUnit.SECONDS);
            assertThat(cancel().reservation().state()).isEqualTo(State.RELEASED);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_release_bindings", Long.class)).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"authorization", "reservation", "binding"})
    void directReverseOrderRowContentionReturnsBoundedConflictWithoutChangingCapacity(String row) throws Exception {
        if (row.equals("binding")) externalBinding();
        String sql = switch (row) {
            case "authorization" -> "SELECT reservation_id FROM deployment_admission_authorizations FOR UPDATE";
            case "reservation" -> "SELECT id FROM deployment_capacity_reservations FOR UPDATE";
            case "binding" -> "SELECT id FROM deployment_provider_resource_handles FOR UPDATE";
            default -> throw new AssertionError(row);
        };
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var owner = workers.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(tx -> {
                jdbc.queryForList(sql, String.class); held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock timeout"); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                return true;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                var attempt = workers.submit(this::cancel);
                String code = row.equals("binding") ? "UNUSED_CANCELLATION_BINDING_BUSY" : "UNUSED_CANCELLATION_AUTHORIZATION_BUSY";
                assertThatThrownBy(() -> attempt.get(3, TimeUnit.SECONDS)).hasRootCauseMessage("409 CONFLICT \"" + code + "\"");
            } finally { release.countDown(); }
            owner.get(10, TimeUnit.SECONDS);
        }
        assertHeld();
        var receipt = cancel();
        assertThat(cancel()).isEqualTo(receipt);
    }

    @Test void cancelledUnusedHandleKeepsHistoryWhileFreeingTheActiveOnlyClusterConstraint() {
        // Same partial constraint as V192; no marketplace/template seeding is part of this fixture.
        jdbc.execute("CREATE UNIQUE INDEX uq_active_external_vector_binding_cluster ON deployment_provider_resource_handles(provider_resource_uuid) WHERE resource_kind='EXTERNAL_VECTOR_STORAGE_BINDING' AND status='ACTIVE'");
        externalBinding();
        String next = "INSERT INTO deployment_provider_resource_handles(id,deployment_id,target_profile_id,resource_kind,status,provider_type,provider_resource_uuid) VALUES ('next','fresh-deployment','fresh-target','EXTERNAL_VECTOR_STORAGE_BINDING','ACTIVE','COOLIFY','synthetic-cluster')";
        assertThatThrownBy(() -> jdbc.update(next)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        var receipt = cancel();
        jdbc.update(next);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_provider_resource_handles", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM deployment_provider_resource_handles WHERE id='external'", String.class)).isEqualTo("RETAINED_UNUSED");
        assertThat(cancel()).isEqualTo(receipt);
    }

    @Test void cancellationCommitWinsAgainstADeploymentLockedReleaseTransaction() throws Exception {
        var cancellationHolding = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var observedJdbc = new JdbcTemplate(dataSource) {
            @Override public int update(String sql, Object... arguments) {
                int changed = super.update(sql, arguments);
                if (sql.contains("UPDATE deployment_capacity_reservations SET")) {
                    cancellationHolding.countDown();
                    try { if (!allowCommit.await(10, TimeUnit.SECONDS)) throw new AssertionError("cancellation commit timeout"); }
                    catch (InterruptedException error) { throw new IllegalStateException(error); }
                }
                return changed;
            }
        };
        var cancelling = new AdmissionStore(observedJdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var cancellation = workers.submit(() -> cancelling.cancelUnused("deployment", TARGET, command, "staff"));
            assertThat(cancellationHolding.await(10, TimeUnit.SECONDS)).isTrue();
            var apply = workers.submit(() -> { bind("late-release"); return true; });
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%platform_deployments%' AND pid<>pg_backend_pid()", Long.class);
                    if (count != null && count > 0) { waiting = true; break; }
                    Thread.sleep(20);
                }
                assertThat(waiting).as("release transaction really waits for the deployment row owned by cancellation").isTrue();
            } finally { allowCommit.countDown(); }
            var receipt = cancellation.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> apply.get(10, TimeUnit.SECONDS))
                .hasRootCauseMessage("409 CONFLICT \"ALLOCATION_NOT_CURRENT_FOR_REVIEWED_LIFECYCLE\"");
            assertThat(cancel()).isEqualTo(receipt);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployment_releases", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_release_bindings", Long.class)).isZero();
        assertThat(store.held()).isEmpty();
    }
}

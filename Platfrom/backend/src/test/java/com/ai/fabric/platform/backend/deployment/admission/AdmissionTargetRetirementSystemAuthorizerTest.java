package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdmissionTargetRetirementSystemAuthorizerTest {
    private final AdmissionStore admissions = mock(AdmissionStore.class);
    private final TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
    private final AdmissionTargetRetirementSystemAuthorizer authorizer =
        new AdmissionTargetRetirementSystemAuthorizer(admissions, operations,
            Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));

    @Test
    void exactClaimedAdmissionExpiryAuthorizesTheSystemR3Operation() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.PLANNED, Step.DRAIN);

        assertThat(authorizer.blocker(context(reservation, "deployment", "generation-1"))).isNull();
    }

    @Test
    void changedDeploymentOrGenerationCannotBorrowAdmissionAuthority() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.PLANNED, Step.DRAIN);

        assertThat(authorizer.blocker(context(reservation, "foreign-deployment", "generation-1")))
            .isEqualTo("RETIREMENT_ACTOR_REVOKED");
        assertThat(authorizer.blocker(context(reservation, "deployment", "other-generation")))
            .isEqualTo("RETIREMENT_ACTOR_REVOKED");
    }

    @Test
    void destructiveStepsRemainBlockedUntilTheAdmissionRecoveryWindowElapses() {
        Reservation reservation = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.RUNNING, Step.AUTHORITY);

        assertThat(authorizer.blocker(context(reservation, "deployment", "generation-1")))
            .isEqualTo("RECOVERY_WINDOW_NOT_ELAPSED");
    }

    @Test
    void destructiveStepsBecomeAuthorizedAfterTheExactRecoveryDeadline() {
        Reservation reservation = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.RUNNING, Step.AUTHORITY);
        var elapsed = new AdmissionTargetRetirementSystemAuthorizer(admissions, operations,
            Clock.fixed(AdmissionFixture.NOW.plusSeconds(20), ZoneOffset.UTC));

        assertThat(elapsed.blocker(context(reservation, "deployment", "generation-1"))).isNull();
    }

    @Test
    void onlyTheExactOrCanonicalPersistedDeadlineCanBorrowAdmissionAuthority() {
        var reservation = reservation(ExpiryState.CLAIMED, null, Instant.parse("2026-10-22T12:32:34.412649467Z"));
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.PLANNED, Step.DRAIN);
        var exact = context(reservation, "deployment", "generation-1");
        var persisted = Instant.parse("2026-10-22T12:32:34.412649Z");

        assertThat(authorizer.blocker(withDeadline(exact, persisted))).isNull();
        for (Instant changed : List.of(persisted.minusNanos(1), persisted.plusNanos(1_000),
            persisted.minusNanos(1_000), persisted.plusSeconds(1))) {
            assertThat(authorizer.blocker(withDeadline(exact, changed))).isEqualTo("RETIREMENT_ACTOR_REVOKED");
        }
        assertThat(authorizer.blocker(withDeadline(exact, null))).isEqualTo("RETIREMENT_ACTOR_REVOKED");
    }

    @Test
    void canonicalPostgresDeadlineRoundingPreservesClaimScopeAndRejectsDifferentMicroseconds() {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            var dataSource = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            var jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("SELECT MAX(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
                Integer.class)).isEqualTo(208);
            var json = new TargetRetirementJson(new ObjectMapper().registerModule(new JavaTimeModule()));
            var transactions = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
            var cases = List.of(
                List.of("2026-10-22T12:32:34.412649467Z", "2026-10-22T12:32:34.412649Z"),
                List.of("2026-10-22T12:32:34.412649499Z", "2026-10-22T12:32:34.412649Z"),
                List.of("2026-10-22T12:32:34.412649500Z", "2026-10-22T12:32:34.412650Z"),
                List.of("2026-10-22T12:32:34.999999999Z", "2026-10-22T12:32:35Z"));
            for (var timestamps : cases) {
                transactions.executeWithoutResult(tx -> {
                    var expected = Instant.parse(timestamps.getFirst());
                    var reservation = reservation(ExpiryState.CLAIMED, null, expected);
                    when(admissions.require(reservation.id())).thenReturn(reservation);
                    operation(TargetRetirementContracts.State.PLANNED, Step.DRAIN);
                    var exact = context(reservation, "deployment", "generation-1");
                    var request = new PlanRequest(exact.operationKey(), List.of(), List.of(), expected, exact.retention(), "expiry");
                    String id = exact.operationId();
                    jdbc.update("""
                        INSERT INTO platform_target_retirement_operations
                        (id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,
                         requested_by_actor_id,requested_by_role,request_json,snapshot_json,result_json,state,step,
                         drain_deadline,created_at,updated_at)
                        VALUES (?,'customer','deployment',?,?,'fingerprint','system','SYSTEM',?,?,'{}','PLANNED','DRAIN',?,?,?)
                        """, id, reservation.targetProfileId(), exact.operationKey(), json.write(request), json.write(exact.snapshot()),
                        java.sql.Timestamp.from(expected), java.sql.Timestamp.from(AdmissionFixture.NOW), java.sql.Timestamp.from(AdmissionFixture.NOW));
                    var persisted = jdbc.queryForObject("SELECT drain_deadline FROM platform_target_retirement_operations WHERE id=?",
                        java.sql.Timestamp.class, id).toInstant();
                    var persistedRequest = json.read(jdbc.queryForObject("SELECT request_json FROM platform_target_retirement_operations WHERE id=?",
                        String.class, id), PlanRequest.class);
                    assertThat(persisted).isEqualTo(Instant.parse(timestamps.get(1))).isNotEqualTo(expected);
                    assertThat(persistedRequest.drainDeadline()).isEqualTo(expected);
                    assertThat(authorizer.blocker(withDeadline(exact, persisted))).isNull();
                    assertThat(authorizer.blocker(withDeadline(context(reservation, "foreign-deployment", "generation-1"), persisted)))
                        .isEqualTo("RETIREMENT_ACTOR_REVOKED");
                    assertThat(authorizer.blocker(withDeadline(context(reservation, "deployment", "foreign-generation"), persisted)))
                        .isEqualTo("RETIREMENT_ACTOR_REVOKED");
                    assertThat(authorizer.blocker(withDeadline(exact, persisted.plusNanos(1_000))))
                        .isEqualTo("RETIREMENT_ACTOR_REVOKED");
                    assertThat(authorizer.blocker(withDeadline(exact, persisted.minusNanos(1_000))))
                        .isEqualTo("RETIREMENT_ACTOR_REVOKED");
                    tx.setRollbackOnly();
                });
            }
        }
    }

    private Context withDeadline(Context context, Instant deadline) {
        return new Context(context.operationId(), context.operationKey(), context.requestFingerprint(), context.snapshot(),
            context.fenceEpoch(), context.workerToken(), context.requestedByActorId(), context.requestedByRole(), deadline, context.retention());
    }

    private void operation(TargetRetirementContracts.State state, Step step) {
        var operation = new TargetRetirementOperationEntity();
        operation.setId("retirement-1");
        operation.setState(state);
        operation.setStep(step);
        when(operations.findById("retirement-1")).thenReturn(java.util.Optional.of(operation));
        FleetPolicy policy = mock(FleetPolicy.class);
        when(policy.restoreEvidenceReference()).thenReturn("restore-proof");
        when(admissions.reviewed("POLICY", "policy1", FleetPolicy.class)).thenReturn(policy);
    }

    private Context context(Reservation reservation, String deploymentId, String generation) {
        Snapshot snapshot = new Snapshot(reservation.customerId(), reservation.tenantId(), deploymentId,
            reservation.targetProfileId(), "target-generation", 1, List.of(), List.of(), List.of());
        return new Context("retirement-1", "admission-expiry:" + reservation.id() + ":" + generation,
            "fingerprint", snapshot, 0, null, "system", "SYSTEM", reservation.drainDeadline(),
            new Retention(RetentionDecision.RECLAIM, null, null, "restore-proof"));
    }

    private Reservation reservation(ExpiryState state, String retirementId) {
        return reservation(state, retirementId, AdmissionFixture.NOW.plusSeconds(10));
    }

    private Reservation reservation(ExpiryState state, String retirementId, Instant drainDeadline) {
        return new Reservation("reservation-1", "customer", "tenant", "deployment", AdmissionFixture.TARGET,
            Intent.CUSTOMER_STAGING, "operation", "request", "generation-1", 4, AdmissionContracts.State.ACTIVE,
            AdmissionFixture.vector(1), "policy1", "manifest1", "manifest-fingerprint", "binding-fingerprint",
            AdmissionFixture.NOW.minusSeconds(2000), AdmissionFixture.NOW, AdmissionFixture.NOW.minusSeconds(1000),
            AdmissionFixture.NOW.minusSeconds(1), 0, null, null, state, "cycle-1", null, 1,
            "worker-1", drainDeadline, AdmissionFixture.NOW.plusSeconds(20), retirementId,
            "evidence", null, null, null, null);
    }
}

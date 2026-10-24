package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagedDependencyAdmissionLedgerTest extends AdmissionFixture {
    private static final String SPEC = "a".repeat(64);
    private static final String BINDING = "b".repeat(64);
    private ManagedDependencyAdmissionLedger ledger;
    private ManagedDependencyAdmissionLedger.Requirement requirement;

    @BeforeEach
    void setUpLedger() {
        approve(20_000_000_000L);
        Vector demand = with(with(with(with(with(vector(1), Dimension.CPU_MILLICORES, 1_000),
            Dimension.MEMORY_BYTES, 1024L * 1024L * 1024L), Dimension.DISK_BYTES,
            4L * 1024L * 1024L * 1024L), Dimension.DATABASES, 2), Dimension.DEPENDENCIES, 4);
        Manifest manifest = new Manifest("managed", "deployment1", "version", "config-hash", TARGET,
            BINDING, "reviewed-managed-topology", demand,
            Set.of("managed-dependency-spec:" + SPEC, "immutable-artifacts"));
        store.approveManifest(manifest, "operator", NOW);
        ledger = new ManagedDependencyAdmissionLedger(store, json, context -> evidence(context, NOW), Clock.fixed(NOW, ZoneOffset.UTC));
        requirement = new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1",
            TARGET, "version", BINDING, SPEC, 250, 512L * 1024L * 1024L,
            2L * 1024L * 1024L * 1024L, 1, 3);
    }

    @Test
    void bindsExactlyOneReviewedParentReservationAndReplaysWithoutAllocatingCapacity() {
        Reservation reservation = reserve("managed", "apply");

        var first = ledger.require(requirement, null);
        var replay = ledger.require(requirement, reservation.id());

        assertThat(replay).isEqualTo(first);
        assertThat(first.reservationId()).isEqualTo(reservation.id());
        assertThat(first.bindingFingerprint()).isEqualTo(BINDING);
        assertThat(store.held()).hasSize(1);
    }

    @Test void dependencyReadbackUsesFreshCompatiblePolicyWithoutMovingTheHeldEnvelope() {
        Reservation reservation = reserve("managed", "apply");
        var next = AdmissionPolicyContinuityTest.continuation(store, json, "policy-next", List.of(reservation), NOW, NOW.plusSeconds(300));
        AdmissionPolicyContinuityTest.publishFixture(store, jdbc, next, NOW);
        var resolved = ledger.require(requirement, reservation.id());
        assertThat(resolved.reservationId()).isEqualTo(reservation.id());
        assertThat(store.require(reservation.id())).isEqualTo(reservation);
        assertThat(resolved.validUntil()).isEqualTo(NOW.plusSeconds(60));
        jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='POLICY_CONT'");
        assertThatThrownBy(() -> ledger.require(requirement, reservation.id())).hasMessageContaining("POLICY_CONTINUITY_REQUIRED");
    }

    @Test
    void ambiguityExpiryFingerprintAndEnvelopeChangesFailClosed() {
        Reservation reservation = reserve("managed", "apply");
        AdmissionStore ambiguous = mock(AdmissionStore.class);
        when(ambiguous.held()).thenReturn(List.of(reservation, reservation));
        var ambiguousLedger = new ManagedDependencyAdmissionLedger(ambiguous, json, context -> evidence(context, NOW),
            Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> ambiguousLedger.require(requirement, null))
            .hasMessageContaining("DEPENDENCY_ADMISSION_AMBIGUOUS_OR_MISSING");

        var expired = new ManagedDependencyAdmissionLedger(store, json, context -> evidence(context, NOW),
            Clock.fixed(NOW.plusSeconds(1_001), ZoneOffset.UTC));
        assertThatThrownBy(() -> expired.require(requirement, reservation.id()))
            .hasMessageContaining("DEPENDENCY_ADMISSION_INVALID");

        var changedBinding = new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1",
            TARGET, "version", "c".repeat(64), SPEC, 250, 1, 1, 1, 1);
        assertThatThrownBy(() -> ledger.require(changedBinding, reservation.id()))
            .hasMessageContaining("DEPENDENCY_ADMISSION_FINGERPRINT_CHANGED");

        var tooLarge = new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1",
            TARGET, "version", BINDING, SPEC, 1_001, 2L * 1024L * 1024L * 1024L,
            5L * 1024L * 1024L * 1024L, 3, 5);
        assertThatThrownBy(() -> ledger.require(tooLarge, reservation.id()))
            .hasMessageContaining("DEPENDENCY_CAPACITY_NOT_RESERVED");
    }

    @Test
    void crossCustomerDeploymentAndTargetCannotSelectTheReservation() {
        Reservation reservation = reserve("managed", "apply");
        for (var foreign : List.of(
            new ManagedDependencyAdmissionLedger.Requirement("foreign", "tenant", "deployment1", TARGET,
                "version", BINDING, SPEC, 1, 1, 1, 1, 1),
            new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "foreign", TARGET,
                "version", BINDING, SPEC, 1, 1, 1, 1, 1),
            new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1", "foreign",
                "version", BINDING, SPEC, 1, 1, 1, 1, 1))) {
            assertThatThrownBy(() -> ledger.require(foreign, reservation.id()))
                .hasMessageContaining("DEPENDENCY_ADMISSION_AMBIGUOUS_OR_MISSING");
        }
    }

    @Test
    void concurrentReadbackReturnsOneStableReservationGeneration() throws Exception {
        Reservation reservation = reserve("managed", "apply");
        CountDownLatch start = new CountDownLatch(1);
        List<ManagedDependencyAdmissionLedger.Evidence> results =
            java.util.Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4).mapToObj(index -> executor.submit(() -> {
                start.await();
                results.add(ledger.require(requirement, reservation.id()));
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get();
        }
        assertThat(results).hasSize(4).allMatch(value -> value.reservationId().equals(reservation.id())
            && value.generation().equals(reservation.generation()));
        assertThat(store.held()).hasSize(1);
    }

    @Test void expiredOriginalReadbackUsesFreshExecutionEvidenceWithoutChangingAnyLedgerRows() {
        Reservation reservation = reserve("managed", "apply");
        Instant executionTime = NOW.plusSeconds(600);
        var before = databaseSnapshot();
        AdmissionEvidenceSource source = new AdmissionEvidenceSource() {
            @Override public PrerequisiteReadback read(AdmissionContext context) {
                throw new AssertionError("Execution must use the explicit execution adapter");
            }
            @Override public PrerequisiteReadback readForExecution(AdmissionContext context) {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return evidence(context, executionTime);
            }
        };
        var execution = new ManagedDependencyAdmissionLedger(store, json, source, Clock.fixed(executionTime, ZoneOffset.UTC));

        var result = execution.requireForExecution(requirement, reservation.id());

        assertThat(result.validUntil()).isEqualTo(executionTime.plusSeconds(60));
        assertThat(result.generation()).isEqualTo(reservation.generation());
        assertThat(result.revision()).isEqualTo(reservation.revision());
        assertThat(store.require(reservation.id())).isEqualTo(reservation);
        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test void absentStaleExpiredAndForeignExecutionEvidenceDenyWithoutChangingLedgerRows() {
        Reservation reservation = reserve("managed", "apply");
        Instant executionTime = NOW.plusSeconds(600);
        var context = store.context(reservation);
        var fresh = evidence(context, executionTime);
        var invalid = new ArrayList<PrerequisiteReadback>();
        invalid.add(null);
        invalid.add(reservation.prerequisiteReadback());
        invalid.add(evidence(context, executionTime.plusSeconds(1)));
        invalid.add(new PrerequisiteReadback(executionTime, executionTime, fresh.evidenceReference(), fresh.placement(),
            fresh.profileFingerprint(), fresh.manifestFingerprint(), fresh.bindingFingerprint(), fresh.verifiedPrerequisiteReferences(),
            fresh.restoreEvidenceReference(), fresh.privacyEvidenceReference(), fresh.ownerContactEvidenceReference(), fresh.noticeTransportReference()));
        invalid.add(new PrerequisiteReadback(executionTime, executionTime.plusSeconds(60), fresh.evidenceReference(),
            new Placement("foreign", STAGING.destinationUuid(), STAGING.ip()), fresh.profileFingerprint(), fresh.manifestFingerprint(),
            fresh.bindingFingerprint(), fresh.verifiedPrerequisiteReferences(), fresh.restoreEvidenceReference(),
            fresh.privacyEvidenceReference(), fresh.ownerContactEvidenceReference(), fresh.noticeTransportReference()));
        invalid.add(new PrerequisiteReadback(executionTime, executionTime.plusSeconds(60), fresh.evidenceReference(), fresh.placement(),
            fresh.profileFingerprint(), "foreign-manifest", fresh.bindingFingerprint(), fresh.verifiedPrerequisiteReferences(),
            fresh.restoreEvidenceReference(), fresh.privacyEvidenceReference(), fresh.ownerContactEvidenceReference(), fresh.noticeTransportReference()));
        invalid.add(new PrerequisiteReadback(executionTime, executionTime.plusSeconds(60), fresh.evidenceReference(), fresh.placement(),
            fresh.profileFingerprint(), fresh.manifestFingerprint(), fresh.bindingFingerprint(), Set.of("unreviewed"),
            fresh.restoreEvidenceReference(), fresh.privacyEvidenceReference(), fresh.ownerContactEvidenceReference(), fresh.noticeTransportReference()));
        var before = databaseSnapshot();
        for (var value : invalid) {
            var execution = new ManagedDependencyAdmissionLedger(store, json, ignored -> value, Clock.fixed(executionTime, ZoneOffset.UTC));
            assertThatThrownBy(() -> execution.requireForExecution(requirement, reservation.id())).isInstanceOf(AdmissionFailure.class);
            assertThat(databaseSnapshot()).isEqualTo(before);
        }
    }

    @Test void reservationRevisionOrStateChangeDuringProbeDeniesRatherThanReturningStaleAuthority() {
        Reservation reservation = reserve("managed", "apply");
        var execution = new ManagedDependencyAdmissionLedger(store, json, context -> {
            store.outcome(reservation.fence(), State.PROVISIONING, "independent-transition", "worker", NOW);
            return evidence(context, NOW);
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> execution.requireForExecution(requirement, reservation.id()))
            .hasMessageContaining("DEPENDENCY_ADMISSION_CHANGED_DURING_READBACK");
        assertThat(store.require(reservation.id()).state()).isEqualTo(State.PROVISIONING);
    }

    @Test void compatiblePolicyPublicationDuringProbeStillRequiresANewReadback() {
        Reservation reservation = reserve("managed", "apply");
        var next = AdmissionPolicyContinuityTest.continuation(store, json, "policy-next", List.of(reservation), NOW, NOW.plusSeconds(300));
        var execution = new ManagedDependencyAdmissionLedger(store, json, context -> {
            AdmissionPolicyContinuityTest.publishFixture(store, jdbc, next, NOW);
            return evidence(context, NOW);
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> execution.requireForExecution(requirement, reservation.id()))
            .hasMessageContaining("DEPENDENCY_ADMISSION_CHANGED_DURING_READBACK");
        assertThat(store.require(reservation.id())).isEqualTo(reservation);
    }

    @Test void cachedProjectionFailsStaleWithoutCallingTheExecutionAdapter() {
        Reservation reservation = reserve("managed", "apply");
        Instant executionTime = NOW.plusSeconds(600);
        var source = mock(AdmissionEvidenceSource.class);
        when(source.read(org.mockito.ArgumentMatchers.any())).thenReturn(reservation.prerequisiteReadback());
        var projection = new ManagedDependencyAdmissionLedger(store, json, source, Clock.fixed(executionTime, ZoneOffset.UTC));
        assertThatThrownBy(() -> projection.require(requirement, reservation.id())).hasMessageContaining("FRESH_READBACK_REQUIRED");
        org.mockito.Mockito.verify(source, org.mockito.Mockito.never()).readForExecution(org.mockito.ArgumentMatchers.any());
        assertThat(store.require(reservation.id())).isEqualTo(reservation);
    }

    @Test void transactionalProjectionStaysLocalAndExplicitExecutionRejectsRatherThanSuspendsTheTransaction() {
        Reservation reservation = reserve("managed", "apply");
        var source = mock(AdmissionEvidenceSource.class);
        when(source.read(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return evidence(call.getArgument(0), NOW);
        });
        var target = new ManagedDependencyAdmissionLedger(store, json, source, Clock.fixed(NOW, ZoneOffset.UTC));
        var factory = new org.springframework.aop.framework.ProxyFactory(target);
        factory.setProxyTargetClass(true);
        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var adapter = (ManagedDependencyAdmissionLedger) factory.getProxy();
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThat(adapter.require(requirement, reservation.id()).reservationId()).isEqualTo(reservation.id());
            assertThatThrownBy(() -> adapter.requireForExecution(requirement, reservation.id()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        });
        org.mockito.Mockito.verify(source, org.mockito.Mockito.never()).readForExecution(org.mockito.ArgumentMatchers.any());
        assertThat(store.require(reservation.id())).isEqualTo(reservation);
    }

    private String databaseSnapshot() {
        var tables = new java.util.TreeMap<String, Object>();
        for (String name : jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME", String.class))
            tables.put(name, jdbc.queryForList("SELECT * FROM " + name));
        return json.fingerprint(tables);
    }

    @Test void existingActiveProductionConfigurationUsesHeldCapacityAfterHistoricDeadlinesWithoutWritingLedger() {
        var active = activeProduction();
        Instant now = NOW.plusSeconds(1_001);
        var execution = configurationLedger(now);
        var before = databaseSnapshot();

        assertThat(execution.reviewedIntent(requirement, active.id())).isEqualTo(Intent.CUSTOMER_PRODUCTION);
        var result = execution.requireConfigurationForExecution(requirement, active.id(), "release");
        assertThat(result.existingConfiguration()).isTrue();
        assertThat(result.validUntil()).isEqualTo(now.plusSeconds(60));
        assertThat(result.generation()).isEqualTo(active.generation());
        assertThat(execution.requireConfiguration(requirement, active.id(), "release")).isEqualTo(result);
        assertThatThrownBy(() -> execution.requireForExecution(requirement, active.id()))
            .hasMessageContaining("DEPENDENCY_ADMISSION_INVALID");
        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test void configurationStillRequiresExactActiveReleaseGrantAndHeldEnvelope() {
        var active = activeProduction();
        var execution = configurationLedger(NOW.plusSeconds(1_001));
        assertThatThrownBy(() -> execution.requireConfigurationForExecution(requirement, active.id(), "foreign"))
            .hasMessageContaining("DEPENDENCY_ACTIVE_PRODUCTION_REQUIRED");
        var tooLarge = new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1",
            TARGET, "version", BINDING, SPEC, 1_001, 1, 1, 1, 1);
        assertThatThrownBy(() -> execution.requireConfigurationForExecution(tooLarge, active.id(), "release"))
            .hasMessageContaining("DEPENDENCY_CAPACITY_NOT_RESERVED");
        assertThatThrownBy(() -> configurationLedger(NOW.plusSeconds(5_001))
            .requireConfigurationForExecution(requirement, active.id(), "release"))
            .hasMessageContaining("ACTIVE_CUSTOMER_QUOTA_REQUIRED");
        store.releaseBindingState("release", "SUPERSEDED");
        assertThatThrownBy(() -> execution.requireConfigurationForExecution(requirement, active.id(), "release"))
            .hasMessageContaining("DEPENDENCY_ACTIVE_PRODUCTION_REQUIRED");
    }

    @Test void configurationKeepsStagingExpiryFenced() {
        var staging = reserve("managed", "stage");
        assertThat(ledger.reviewedIntent(requirement, staging.id())).isEqualTo(Intent.CUSTOMER_STAGING);
        assertThatThrownBy(() -> configurationLedger(NOW.plusSeconds(1_001))
            .requireForExecution(requirement, staging.id())).hasMessageContaining("DEPENDENCY_ADMISSION_INVALID");
        assertThatThrownBy(() -> configurationLedger(NOW.plusSeconds(1_001))
            .requireConfigurationForExecution(requirement, staging.id(), "release"))
            .hasMessageContaining("DEPENDENCY_ACTIVE_PRODUCTION_REQUIRED");
    }

    @Test void configurationCannotAuthorizeAProductionCandidate() {
        var active = activeProduction();
        store.releaseBindingState("release", "QUEUED");
        assertThatThrownBy(() -> configurationLedger(NOW.plusSeconds(1_001))
            .requireConfigurationForExecution(requirement, active.id(), "release"))
            .hasMessageContaining("DEPENDENCY_ACTIVE_PRODUCTION_REQUIRED");
    }

    @Test void activeConfigurationStillRequiresExplicitPolicyContinuity() {
        var active = activeProduction();
        var next = AdmissionPolicyContinuityTest.continuation(store, json, "policy-next", List.of(active), NOW, NOW.plusSeconds(100));
        AdmissionPolicyContinuityTest.publishFixture(store, jdbc, next, NOW);
        assertThat(configurationLedger(NOW.plusSeconds(1_001))
            .requireConfigurationForExecution(requirement, active.id(), "release").existingConfiguration()).isTrue();
        jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='POLICY_CONT'");
        assertThatThrownBy(() -> configurationLedger(NOW.plusSeconds(1_001))
            .requireConfigurationForExecution(requirement, active.id(), "release"))
            .hasMessageContaining("POLICY_CONTINUITY_REQUIRED");
    }

    private ManagedDependencyAdmissionLedger configurationLedger(Instant now) {
        AdmissionEvidenceSource source = new AdmissionEvidenceSource() {
            @Override public PrerequisiteReadback read(AdmissionContext context) {
                throw new AssertionError("Existing configuration must use its explicit evidence boundary");
            }
            @Override public PrerequisiteReadback readExistingConfiguration(AdmissionContext context) {
                return evidence(context, now);
            }
        };
        return new ManagedDependencyAdmissionLedger(store, json, source, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Reservation activeProduction() {
        // Fixture setup only: establish the same held allocation and exact ACTIVE release binding.
        var old = policy(20_000_000_000L);
        var budget = new Budget(old.fleet().measured(), old.fleet().baseline(), old.fleet().headroom(),
            old.fleet().measurementReference(), NOW.minusSeconds(1), NOW.plusSeconds(100));
        var placement = new Placement("kvufjk78dj4wyhjgp1mlxecr", "r3thf2xmxcjn1tt2bclabebz", "46.225.162.106");
        store.approvePolicy(new FleetPolicy("production-policy", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_PRODUCTION, placement, "profile-fingerprint", budget)),
            old.lifecycle(), old.restoreEvidenceReference(), old.privacyEvidenceReference(), old.supportReference()), "operator", NOW);
        var reserved = reserve("managed", "production");
        var active = store.reconcile(reserved.fence(), new ResourceReadback(reserved.id(), reserved.generation(), reserved.revision(),
            reserved.workerToken(), reserved.manifestFingerprint(), reserved.bindingFingerprint(), placement, NOW.plusSeconds(2),
            "inventory-proof", true, true, false, true, true, true, 0, 0L, 0L, 0L, 0L), false, "worker", NOW.plusSeconds(2));
        jdbc.update("""
            INSERT INTO deployment_admission_release_bindings
            (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,
             customer_id,tenant_id,deployment_id,target_profile_id,state,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, "release", active.id(), active.generation(), "production", "version", "config-hash",
            "customer", "tenant", "deployment1", TARGET, "ACTIVE", java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        return active;
    }
}

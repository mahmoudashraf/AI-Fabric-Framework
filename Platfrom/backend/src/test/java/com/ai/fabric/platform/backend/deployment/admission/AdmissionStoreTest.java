package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class AdmissionStoreTest extends AdmissionFixture {
    @Test
    void emptyMigrationKeepsOfferClosed() {
        assertThatThrownBy(store::activePolicy).isInstanceOf(AdmissionFailure.class).hasMessageContaining("MEASURED_POLICY_REQUIRED");
        assertThat(store.held()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews", Long.class)).isZero();
    }
    @ParameterizedTest
    @EnumSource(Dimension.class)
    void everyDimensionIsExplicitAndChecked(Dimension dimension) {
        var missing = new EnumMap<>(vector(1).amounts()); missing.remove(dimension);
        assertThatThrownBy(() -> AdmissionRules.vector(new Vector(missing))).hasMessageContaining("COMPLETE_RESOURCE_ENVELOPE_REQUIRED");
        assertThatThrownBy(() -> AdmissionRules.within(with(vector(1), dimension, 2), vector(1), "QUOTA"))
            .hasMessageContaining("QUOTA:" + dimension.name());
    }
    @Test
    void capacityIsMeasuredNotDefaultedAndHeadroomCannotBeOverdrawn() {
        var p = policy(2);
        var bad = new Budget(with(p.fleet().measured(), Dimension.MEMORY_BYTES, 0), vector(0), vector(0), "cpu-only", NOW, NOW.plusSeconds(1));
        assertThatThrownBy(() -> AdmissionRules.budget(bad, NOW)).hasMessageContaining("MEASURED_CPU_RAM_DISK_REQUIRED");
        var protectedBudget = new Budget(p.fleet().measured(), vector(1), vector(1), "full-load", NOW, NOW.plusSeconds(5000));
        store.approvePolicy(new FleetPolicy(p.id(), protectedBudget, p.targets(), p.lifecycle(), p.restoreEvidenceReference(), p.privacyEvidenceReference(), p.supportReference()), "operator", NOW);
        store.approveGrant(grant(2), "operator", NOW); store.approveManifest(manifest("m", "d"), "operator", NOW);
        assertThatThrownBy(() -> reserve("m", "op")).hasMessageContaining("FLEET_QUOTA_EXCEEDED");
        assertThat(store.held()).isEmpty();
    }
    @Test
    void stagingCannotBeRelabelledOntoProductionAndAliasesCannotDuplicatePool() {
        var production = new Placement("kvufjk78dj4wyhjgp1mlxecr", "r3thf2xmxcjn1tt2bclabebz", "46.225.162.106");
        assertThatThrownBy(() -> AdmissionRules.placement(Intent.CUSTOMER_STAGING, production)).hasMessageContaining("PHYSICAL_PLACEMENT_FORBIDDEN");
        var p = policy(2);
        var alias = new TargetBudget("alias", Intent.CUSTOMER_STAGING, STAGING, "fp", p.fleet());
        var aliases = new FleetPolicy(p.id(), p.fleet(), Map.of(TARGET, p.targets().get(TARGET), "alias", alias), p.lifecycle(), "restore", "privacy", "support");
        assertThatThrownBy(() -> AdmissionRules.policy(aliases, NOW)).hasMessageContaining("DUPLICATE_PHYSICAL_CAPACITY_POOL");
    }
    @Test
    void missingRestorePrivacyAndPrerequisiteReadbackDenyWithoutReservation() {
        approve(1); store.approveManifest(manifest("m", "d"), "operator", NOW); var c = context("m");
        assertThatThrownBy(() -> store.reserve(c, new Reserve("op", "m"), null, "owner", NOW)).hasMessageContaining("PREREQUISITE_READBACK_REQUIRED");
        var e = evidence(c, NOW);
        var missing = new PrerequisiteReadback(e.observedAt(), e.validUntil(), e.evidenceReference(), e.placement(), e.profileFingerprint(),
            e.manifestFingerprint(), e.bindingFingerprint(), Set.of("storage"), null, null, e.ownerContactEvidenceReference(), e.noticeTransportReference());
        assertThatThrownBy(() -> store.reserve(c, new Reserve("op", "m"), missing, "owner", NOW)).hasMessageContaining("ADMISSION_PREREQUISITE_UNVERIFIED");
        assertThat(store.held()).isEmpty();
    }
    @Test
    void idempotentReplaySurvivesRestartAndConflictCannotAllocate() {
        Reservation r = one();
        var restarted = new AdmissionStore(jdbc, json, new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        assertThat(restarted.reserve(context("manifest1"), new Reserve("op1", "manifest1"), null, "owner", NOW)).isEqualTo(r);
        var other = manifest("manifest2", "deployment1"); store.approveManifest(other, "operator", NOW);
        assertThatThrownBy(() -> reserve("manifest2", "op1")).hasMessageContaining("OPERATION_KEY_REUSED");
        assertThatThrownBy(() -> reserve("manifest2", "op2")).hasMessageContaining("CURRENT_RESERVATION_REQUIRES_RECONCILIATION");
        assertThat(store.held()).hasSize(1);
    }
    @Test
    void immutableReviewAndPolicyRotationCannotEvadeHeldCapacity() {
        one();
        assertThatThrownBy(() -> store.approvePolicy(policy(2), "operator", NOW)).hasMessageContaining("REVIEWED_RECORD_IMMUTABLE");
        var grant = grant(0);
        assertThatThrownBy(() -> store.approveGrant(grant, "operator", NOW)).hasMessageContaining("CUSTOMER_FLEET_QUOTA_EXCEEDED");
    }
    @Test
    void policyChangeBetweenReadbackAndCommitConflicts() {
        approve(2); store.approveManifest(manifest("m", "d"), "operator", NOW); var c = context("m");
        var p = policy(2);
        store.approvePolicy(new FleetPolicy("policy2", p.fleet(), p.targets(), p.lifecycle(), "restore", "privacy", "support"), "operator", NOW);
        assertThatThrownBy(() -> store.reserve(c, new Reserve("op", "m"), evidence(c, NOW), "owner", NOW)).hasMessageContaining("ADMISSION_POLICY_CHANGED");
        assertThat(store.held()).isEmpty();
    }
    @Test
    void concurrentSubmissionsCannotOverbookFleetOrCustomer() throws Exception {
        approve(2);
        int jobs = 12; CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < jobs; i++) {
            String id = "m" + i; store.approveManifest(manifest(id, "d" + i), "operator", NOW);
            tasks.add(() -> { start.await(); try { reserve(id, "op"); return true; }
                catch (AdmissionFailure ex) { assertThat(ex.getReason()).contains("QUOTA_EXCEEDED"); return false; } });
        }
        try (var pool = Executors.newFixedThreadPool(jobs)) {
            List<Future<Boolean>> results = tasks.stream().map(pool::submit).toList(); start.countDown();
            int accepted = 0; for (Future<Boolean> result : results) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(2);
        }
        assertThat(store.held()).hasSize(2);
    }
    @Test
    void concurrentIdenticalCommandHasOneDurableResult() throws Exception {
        approve(2); store.approveManifest(manifest("m", "d"), "operator", NOW); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<Reservation>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> { start.await(); return reserve("m", "op"); }));
            start.countDown(); String id = results.get(0).get().id();
            for (var result : results) assertThat(result.get().id()).isEqualTo(id);
        }
        assertThat(store.held()).hasSize(1);
    }
    @Test
    void unknownAndFailedCleanupRetainQuotaUntilFreshGenerationBoundRelease() {
        Reservation r = one(); r = store.outcome(r.fence(), State.PROVISIONING, "provider-start", "operator", NOW);
        r = store.outcome(r.fence(), State.OUTCOME_UNKNOWN, "timeout", "operator", NOW);
        r = store.outcome(r.fence(), State.CLEANUP_FAILED, "cleanup-timeout", "operator", NOW);
        store.approveManifest(manifest("m2", "d2"), "operator", NOW);
        assertThatThrownBy(() -> reserve("m2", "op2")).hasMessageContaining("QUOTA_EXCEEDED");
        Reservation held = r;
        assertThatThrownBy(() -> store.reconcile(held.fence(), readback(held, NOW.minusSeconds(61), true, 0L, 0L), true, "operator", NOW))
            .hasMessageContaining("FRESH_READBACK_REQUIRED");
        assertThatThrownBy(() -> store.reconcile(held.fence(), readback(held, NOW, true, null, 0L), true, "operator", NOW))
            .hasMessageContaining("RECLAMATION_UNPROVEN");
        assertThatThrownBy(() -> store.reconcile(held.fence(), readback(held, NOW, true, 0L, 1L), true, "operator", NOW))
            .hasMessageContaining("RECLAMATION_UNPROVEN");
        Reservation released = store.reconcile(r.fence(), readback(r, NOW, true, 0L, 0L), true, "operator", NOW);
        assertThat(released.consumesCapacity()).isFalse(); assertThat(store.held()).isEmpty();
        assertThat(reserve("m2", "op2").consumesCapacity()).isTrue();
        assertThat(reserve("manifest1", "op1").state()).isEqualTo(State.RELEASED);
        assertThatThrownBy(() -> store.outcome(held.fence(), State.OUTCOME_UNKNOWN, "late", "operator", NOW)).hasMessageContaining("STALE_RESERVATION_FENCE");
    }
    @Test
    void canonicalFingerprintIgnoresSetAndMapInsertionOrder() {
        var a = manifest("m", "d");
        var b = new Manifest(a.id(), a.deploymentId(), a.versionId(), a.versionConfigHash(), a.targetProfileId(), a.bindingFingerprint(), a.topologyReference(),
            a.demand(), new java.util.LinkedHashSet<>(List.of("identity", "dependency-topology", "storage")));
        assertThat(json.fingerprint(a)).isEqualTo(json.fingerprint(b));
    }
    @Test
    void resourceGenerationMismatchCannotReleaseHeldCapacity() {
        Reservation r = one(); var valid = readback(r, NOW, true, 0L, 0L);
        var stale = new ResourceReadback(r.id(), "another-generation", r.revision(), r.workerToken(), r.manifestFingerprint(), r.bindingFingerprint(),
            STAGING, NOW, valid.evidenceReference(), true, true, true, true, true, true, 0, 0L, 0L, 0L, 0L);
        assertThatThrownBy(() -> store.reconcile(r.fence(), stale, true, "operator", NOW)).hasMessageContaining("STALE_RESOURCE_GENERATION");
        assertThat(store.require(r.id()).consumesCapacity()).isTrue();
    }
}

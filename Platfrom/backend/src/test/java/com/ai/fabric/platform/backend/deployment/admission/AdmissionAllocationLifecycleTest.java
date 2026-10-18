package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.deployment.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class AdmissionAllocationLifecycleTest extends AdmissionFixture {
    static final String PROD = "reviewed-production";
    static final Placement PRODUCTION = new Placement("kvufjk78dj4wyhjgp1mlxecr", "r3thf2xmxcjn1tt2bclabebz", "46.225.162.106");
    private AdmissionContext context;

    @BeforeEach
    void reviewProduction() {
        var p = policy(2);
        store.approvePolicy(new FleetPolicy(p.id(), p.fleet(),
            Map.of(PROD, new TargetBudget(PROD, Intent.CUSTOMER_PRODUCTION, PRODUCTION, "profile-fingerprint", p.fleet())),
            p.lifecycle(), p.restoreEvidenceReference(), p.privacyEvidenceReference(), p.supportReference()), "staff", NOW);
        var g = grant(2);
        store.approveGrant(new CustomerGrant(g.customerId(), g.fleetLimit(), Map.of(PROD, vector(2)), g.validUntil(),
            g.ownerContactEvidenceReference(), g.payerReference(), g.keepStagingEntitlementReference()), "staff", NOW);
        var m = manifest("manifest", "deployment");
        store.approveManifest(new Manifest(m.id(), m.deploymentId(), m.versionId(), m.versionConfigHash(), PROD,
            m.bindingFingerprint(), m.topologyReference(), m.demand(), m.prerequisiteReferences(), m.customerPrerequisiteReferences()), "staff", NOW);
        context = store.context("customer", "tenant", "deployment", "manifest");
    }

    @Test
    void planApplyAndActivateUseOneAllocationWithDistinctExactReceipts() {
        Reservation allocation = reserveOperation(Operation.PROMOTION_PLAN, "plan", null, null);
        Reservation apply = reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null);
        ReleaseBinding binding = bindRelease("release");
        Reservation provisioning = store.beginReleaseProvisioning(binding, context, evidence(context, NOW), "worker", NOW);
        assertThat(provisioning.state()).isEqualTo(State.PROVISIONING);
        store.reconcile(provisioning.fence(), new ResourceReadback(provisioning.id(), provisioning.generation(), provisioning.revision(),
            null, provisioning.manifestFingerprint(), provisioning.bindingFingerprint(), PRODUCTION, NOW, "provider-proof",
            true, true, false, false, false, false, 0, null, null, null, null), false, "worker", NOW);
        store.releaseBindingState("release", "ACTIVE");
        Reservation activate = reserveOperation(Operation.ACTIVATE, "activate", allocation.id(), "release");
        store.requireCustomerAuthorization(activate, scope(Operation.ACTIVATE, "activate", "release"));
        assertThat(activate.id()).isEqualTo(apply.id()).isEqualTo(allocation.id());
        assertThat(store.held()).hasSize(1);
        assertThat(store.held().getFirst().demand()).isEqualTo(vector(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(3L);
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release"))
            .isEqualTo(activate);
        assertThatThrownBy(() -> store.activeCustomerReleaseReservation("other-customer", "tenant", "deployment", "release"))
            .hasMessageContaining("ACTIVE_RELEASE_ALLOCATION_REQUIRED");
        assertThatThrownBy(() -> store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "other-release"))
            .hasMessageContaining("ACTIVE_RELEASE_ALLOCATION_REQUIRED");
        assertThatThrownBy(() -> store.requireCustomerAuthorization(activate, scope(Operation.ACTIVATE, "apply", "release")))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
    }

    @Test
    void allocationAndReleaseCannotBeInferredOrBroadened() {
        Reservation allocation = reserveOperation(Operation.PROMOTION_PLAN, "plan", null, null);
        assertThatThrownBy(() -> reserveOperation(Operation.PROMOTION_APPLY, "apply", null, null))
            .hasMessageContaining("CURRENT_RESERVATION_REQUIRES_RECONCILIATION");
        assertThatThrownBy(() -> reserveOperation(Operation.ACTIVATE, "activate", allocation.id(), "foreign-release"))
            .hasMessageContaining("ALLOCATION_RELEASE_SCOPE_MISMATCH");
        assertThatThrownBy(() -> reserveOperation(Operation.ACTIVATE, "activate", allocation.id(), null))
            .hasMessageContaining("ALLOCATION_RELEASE_SCOPE_REQUIRED");
        reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null);
        bindRelease("release");
        assertThatThrownBy(() -> reserveOperation(Operation.ACTIVATE, "activate", allocation.id(), "release"))
            .hasMessageContaining("ALLOCATION_RELEASE_NOT_VERIFIED");
    }

    @Test
    void replaySurvivesRestartAndConflictingCommandCannotAllocateMoreCapacity() {
        Reservation allocation = reserveOperation(Operation.PROMOTION_PLAN, "plan", null, null);
        Reservation apply = reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null)).isEqualTo(apply);
        assertThatThrownBy(() -> reserveOperation(Operation.PROMOTION_PLAN, "apply", allocation.id(), null))
            .hasMessageContaining("OPERATION_KEY_REUSED");
        assertThat(store.held()).hasSize(1);
    }

    @Test
    void oneAuthorizationCannotQueueTwoReleases() {
        Reservation allocation = reserveOperation(Operation.PROMOTION_PLAN, "plan", null, null);
        reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null);
        bindRelease("release");
        assertThatThrownBy(() -> bindRelease("other-release")).hasMessageContaining("EXACT_APPLY_AUTHORIZATION_REQUIRED");
    }

    @Test
    void concurrentLifecycleAuthorizationHasOneReceiptAndOneAllocation() throws Exception {
        Reservation allocation = reserveOperation(Operation.PROMOTION_PLAN, "plan", null, null);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null); });
            var b = workers.submit(() -> { start.await(); return reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null); });
            start.countDown();
            assertThat(a.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(store.held()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(2L);
    }

    @ParameterizedTest @ValueSource(strings = {"APPLY", "PROMOTION_APPLY"})
    void reviewedNewVersionKeepsActiveScopeUntilApplyAndAppendsAnExactReceipt(String operation) {
        Reservation active = activeAllocation();
        var oldReceipts = jdbc.queryForList("SELECT payload_json FROM deployment_admission_authorizations ORDER BY operation_key", String.class);
        ReleaseBinding oldBinding = store.releaseBinding("release");
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        Reserve command = new Reserve("apply-v2", next.manifest().id(), scope(Operation.valueOf(operation), "apply-v2", null), active.id());

        Reservation updated = store.reserve(next, command, evidence(next, NOW), "owner", NOW);

        assertThat(updated.id()).isEqualTo(active.id());
        assertThat(updated.generation()).isEqualTo(active.generation());
        assertThat(updated.revision()).isEqualTo(active.revision() + 1);
        assertThat(updated.manifestId()).isEqualTo(active.manifestId());
        assertThat(updated.manifestFingerprint()).isEqualTo(active.manifestFingerprint());
        assertThat(updated.bindingFingerprint()).isEqualTo(active.bindingFingerprint());
        assertThat(updated.prerequisiteReadback()).isEqualTo(active.prerequisiteReadback());
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release")).isEqualTo(updated);
        assertThat(updated.demand()).isEqualTo(active.demand());
        assertThat(updated.expiresAt()).isEqualTo(active.expiresAt());
        assertThat(store.held()).containsExactly(updated);
        assertThat(store.releaseBinding("release")).isEqualTo(oldBinding);
        assertThat(jdbc.queryForList("SELECT payload_json FROM deployment_admission_authorizations WHERE operation_key IN ('plan','apply') ORDER BY operation_key", String.class))
            .isEqualTo(oldReceipts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(3L);
        store.requireCustomerAuthorization(updated, command.customerContext());
        assertThatThrownBy(() -> store.reserve(next, new Reserve("activate-old-release", next.manifest().id(),
            scope(Operation.ACTIVATE, "activate-old-release", "release"), active.id()), evidence(next, NOW), "owner", NOW))
            .hasMessageContaining("ALLOCATION_REAPPLY_SCOPE_INVALID");
        assertThatThrownBy(() -> store.beginReleaseProvisioning(oldBinding, next, evidence(next, NOW), "stale-worker", NOW))
            .hasMessageContaining("RELEASE_ALLOCATION_GENERATION_CHANGED");
        assertThatThrownBy(() -> store.requireCustomerAuthorization(updated, scope(Operation.PROMOTION_APPLY, "apply", null)))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        assertThatThrownBy(() -> store.outcome(active.fence(), State.OUTCOME_UNKNOWN, "stale", "worker", NOW))
            .hasMessageContaining("STALE_RESERVATION_FENCE");
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(store.reserve(next, command, evidence(next, NOW), "owner", NOW)).isEqualTo(updated);
        assertThatThrownBy(() -> store.reserve(next, new Reserve("apply-v2", next.manifest().id(),
            scope(Operation.ACTIVATE, "apply-v2", "release"), active.id()), evidence(next, NOW), "owner", NOW))
            .hasMessageContaining("OPERATION_KEY_REUSED");

        ReleaseBinding binding = bindRelease("release-v2", next.manifest());
        assertThat(store.require(active.id()).manifestId()).isEqualTo(next.manifest().id());
        Reservation provisioning = store.beginReleaseProvisioning(binding, next, evidence(next, NOW), "worker", NOW);
        settle(provisioning, "release-v2");
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release-v2").manifestId())
            .isEqualTo(next.manifest().id());
        assertThat(store.releaseBinding("release").state()).isEqualTo("SUPERSEDED");
        assertThat(store.releaseBinding("release").versionId()).isEqualTo("version");
    }

    @Test void customerStagingNewVersionReusesItsExactAllocationWithoutAffectingTheActiveReleaseDuringPlanning() {
        var policy = policy(2);
        store.approvePolicy(new FleetPolicy("staging-policy", policy.fleet(), policy.targets(), policy.lifecycle(),
            policy.restoreEvidenceReference(), policy.privacyEvidenceReference(), policy.supportReference()), "staff", NOW);
        store.approveGrant(grant(2), "staff", NOW);
        Manifest initialManifest = manifest("staging-manifest", "deployment");
        store.approveManifest(initialManifest, "staff", NOW);
        AdmissionContext initial = store.context("customer", "tenant", "deployment", initialManifest.id());
        Reserve firstApply = new Reserve("staging-apply", initialManifest.id(), stagingScope("staging-apply"), null);
        Reservation reserved = store.reserve(initial, firstApply, evidence(initial, NOW), "owner", NOW);
        Reservation provisioning = store.beginReleaseProvisioning(bindRelease("staging-release", initialManifest), initial,
            evidence(initial, NOW), "worker", NOW);
        Reservation active = store.reconcile(provisioning.fence(), readback(provisioning, NOW, false, 0L, 0L), false, "worker", NOW);
        store.releaseBindingState("staging-release", "ACTIVE");
        Manifest nextManifest = new Manifest("staging-v2", initialManifest.deploymentId(), "staging-version-v2", "staging-hash-v2",
            TARGET, "staging-binding-v2", initialManifest.topologyReference(), initialManifest.demand(),
            initialManifest.prerequisiteReferences(), initialManifest.customerPrerequisiteReferences());
        store.approveManifest(nextManifest, "staff", NOW);
        AdmissionContext next = store.context("customer", "tenant", "deployment", nextManifest.id());
        Reserve nextApply = new Reserve("staging-apply-v2", nextManifest.id(), stagingScope("staging-apply-v2"), reserved.id());

        Reservation planned = store.reserve(next, nextApply, evidence(next, NOW), "owner", NOW);
        store.requireCustomerAuthorization(planned, nextApply.customerContext());
        assertThat(planned.intent()).isEqualTo(Intent.CUSTOMER_STAGING);
        assertThat(planned.generation()).isEqualTo(active.generation());
        assertThat(planned.manifestFingerprint()).isEqualTo(active.manifestFingerprint());
        assertThat(planned.prerequisiteReadback()).isEqualTo(active.prerequisiteReadback());
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "staging-release")).isEqualTo(planned);
        assertThat(store.held()).containsExactly(planned);
        assertThatThrownBy(() -> store.beginProvisioning(planned.fence(), next, evidence(next, NOW), "worker", NOW, () -> { }))
            .hasMessageContaining("RESERVATION_POLICY_CHANGED");

        ReleaseBinding replacement = bindRelease("staging-release-v2", nextManifest);
        assertThat(replacement.authorizationKey()).isEqualTo("staging-apply-v2");
        assertThat(replacement.targetProfileId()).isEqualTo(TARGET);
        Reservation replacing = store.beginReleaseProvisioning(replacement, next, evidence(next, NOW), "worker", NOW);
        store.reconcile(replacing.fence(), readback(replacing, NOW, false, 0L, 0L), false, "worker", NOW);
        store.releaseBindingState("staging-release-v2", "ACTIVE");
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "staging-release-v2").manifestId())
            .isEqualTo(nextManifest.id());
        assertThat(store.releaseBinding("staging-release").state()).isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(2L);
    }

    private CustomerOfferContracts.AdmissionContext stagingScope(String key) {
        var original = scope(Operation.APPLY, key, null);
        return new CustomerOfferContracts.AdmissionContext(original.customerId(), original.tenantId(), original.deploymentId(),
            null, TARGET, CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, original.offerId(), original.offerFingerprint(),
            original.entitlementRevision(), original.placementEvidenceId(), original.operation(), key, original.limits(), original.topology());
    }

    @Test void replacementPromotionPlanRequiresSeparateApplyReceiptBeforeProviderWork() {
        Reservation active = activeAllocation();
        ReleaseBinding oldBinding = store.releaseBinding("release");
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        Reserve plan = new Reserve("plan-v2", next.manifest().id(), scope(Operation.PROMOTION_PLAN, "plan-v2", null), active.id());

        Reservation planned = store.reserve(next, plan, evidence(next, NOW), "owner", NOW);
        store.requireCustomerAuthorization(planned, plan.customerContext());
        Reservation projected = new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(ignored ->
            store.customerPrerequisiteReservations(plan.customerContext()).getFirst());
        assertThat(projected.manifestId()).isEqualTo(next.manifest().id());
        assertThat(projected.prerequisiteReadback()).isEqualTo(evidence(next, NOW));
        store.requireCustomerAuthorization(projected, plan.customerContext());
        assertThat(planned.manifestFingerprint()).isEqualTo(active.manifestFingerprint());
        assertThat(planned.prerequisiteReadback()).isEqualTo(active.prerequisiteReadback());
        assertThat(planned.generation()).isEqualTo(active.generation());
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release")).isEqualTo(planned);
        assertThatThrownBy(() -> bindRelease("plan-only-release", next.manifest()))
            .hasMessageContaining("EXACT_APPLY_AUTHORIZATION_REQUIRED");
        assertThatThrownBy(() -> store.requireCustomerAuthorization(planned, scope(Operation.PROMOTION_APPLY, "plan-v2", null)))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        assertThatThrownBy(() -> store.beginProvisioning(planned.fence(), next, evidence(next, NOW), "worker", NOW, () -> { }))
            .hasMessageContaining("RESERVATION_POLICY_CHANGED");
        assertThatThrownBy(() -> store.beginReleaseProvisioning(oldBinding, next, evidence(next, NOW), "worker", NOW))
            .hasMessageContaining("RELEASE_ALLOCATION_GENERATION_CHANGED");
        assertThat(store.require(active.id())).isEqualTo(planned);
        assertThat(store.releaseBinding("plan-only-release")).isNull();
        assertThat(store.releaseBinding("release")).isEqualTo(oldBinding);
        String planReceipt = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_authorizations WHERE operation_key='plan-v2'", String.class);

        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(store.reserve(next, plan, evidence(next, NOW), "owner", NOW)).isEqualTo(planned);
        assertThatThrownBy(() -> store.reserve(next, new Reserve("plan-v2", next.manifest().id(),
            scope(Operation.PROMOTION_APPLY, "plan-v2", null), active.id()), evidence(next, NOW), "owner", NOW))
            .hasMessageContaining("OPERATION_KEY_REUSED");

        Reserve apply = new Reserve("apply-v2", next.manifest().id(), scope(Operation.PROMOTION_APPLY, "apply-v2", null), active.id());
        Reservation authorized = store.reserve(next, apply, evidence(next, NOW), "owner", NOW);
        store.requireCustomerAuthorization(authorized, apply.customerContext());
        assertThat(authorized.manifestFingerprint()).isEqualTo(active.manifestFingerprint());
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release")).isEqualTo(authorized);
        assertThat(store.held()).containsExactly(authorized);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_authorizations WHERE operation_key='plan-v2'", String.class))
            .isEqualTo(planReceipt);

        ReleaseBinding replacement = bindRelease("release-v2", next.manifest());
        assertThat(replacement.authorizationKey()).isEqualTo("apply-v2");
        assertThat(store.require(active.id()).manifestId()).isEqualTo(next.manifest().id());
        Reservation provisioning = store.beginReleaseProvisioning(replacement, next, evidence(next, NOW), "worker", NOW);
        settle(provisioning, "release-v2");
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release-v2").manifestId())
            .isEqualTo(next.manifest().id());
        assertThat(store.releaseBinding("release").state()).isEqualTo("SUPERSEDED");
    }

    @Test void competingCandidatesCanBePlannedButCannotBothCommitOneAllocation() throws Exception {
        Reservation active = activeAllocation();
        AdmissionContext second = reviewedVersion("manifest-v2", "version-v2", vector(1));
        AdmissionContext third = reviewedVersion("manifest-v3", "version-v3", vector(1));
        reserveVersion(second, active, "apply-v2", null);
        reserveVersion(third, active, "apply-v3", null);
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release").manifestId())
            .isEqualTo(active.manifestId());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var attempts = List.of(second, third).stream().map(candidate -> workers.submit(() -> {
                start.await();
                try {
                    bindRelease("release-" + candidate.manifest().versionId(), candidate.manifest());
                    return "COMMITTED";
                } catch (AdmissionFailure rejected) { return "REJECTED"; }
            })).toList();
            start.countDown();
            assertThat(List.of(attempts.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS),
                attempts.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("COMMITTED", "REJECTED");
        }
        assertThat(store.held()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_release_bindings", Long.class)).isEqualTo(2L);
    }

    @Test void pendingApplyAndQueuedReleaseBlockFurtherCommandsWithoutOverwritingReceipts() {
        Reservation active = activeAllocation();
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        reserveVersion(next, active, "apply-v2", null);
        Reservation pending = store.require(active.id());
        assertThatThrownBy(() -> reserveVersion(next, active, "another-apply-v2", null))
            .hasMessageContaining("ALLOCATION_APPLY_ALREADY_AUTHORIZED");
        var queued = bindRelease("release-v2", next.manifest());
        Reservation committed = store.require(active.id());
        AdmissionContext later = reviewedVersion("manifest-v3", "version-v3", vector(1));
        assertThatThrownBy(() -> reserveVersion(later, active, "apply-v3", null))
            .hasMessageContaining("ALLOCATION_RELEASE_ALREADY_IN_PROGRESS");
        assertThatThrownBy(() -> store.reserve(later, new Reserve("plan-v3", later.manifest().id(),
            scope(Operation.PROMOTION_PLAN, "plan-v3", null), active.id()), evidence(later, NOW), "owner", NOW))
            .isInstanceOf(AdmissionFailure.class);
        assertThat(committed.revision()).isEqualTo(pending.revision() + 1);
        assertThat(store.require(active.id())).isEqualTo(committed);
        assertThat(store.releaseBinding("release-v2")).isEqualTo(queued);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(3L);
    }

    @Test void abandonedCandidatePreservesOldReleaseReadinessRollbackAndOriginalReceipt() {
        Reservation active = activeAllocation();
        String oldReceipt = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_authorizations WHERE operation_key='apply'", String.class);
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        Reserve candidate = new Reserve("apply-v2", next.manifest().id(), scope(Operation.PROMOTION_APPLY, "apply-v2", null), active.id());
        store.reserve(next, candidate, evidence(next, NOW), "owner", NOW);

        var projected = new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(ignored ->
            store.customerPrerequisiteReservations(candidate.customerContext()).getFirst());
        assertThat(projected.manifestId()).isEqualTo(next.manifest().id());
        assertThat(projected.prerequisiteReadback()).isEqualTo(evidence(next, NOW));
        store.requireCustomerAuthorization(projected, candidate.customerContext());
        Reservation stillActive = store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release");
        assertThat(stillActive.manifestFingerprint()).isEqualTo(active.manifestFingerprint());
        assertThat(stillActive.prerequisiteReadback()).isEqualTo(active.prerequisiteReadback());
        Reservation rollback = reserveOperation(Operation.ROLLBACK, "rollback-current", active.id(), "release");
        store.requireCustomerAuthorization(rollback, scope(Operation.ROLLBACK, "rollback-current", "release"));
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release")).isEqualTo(rollback);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_authorizations WHERE operation_key='apply'", String.class))
            .isEqualTo(oldReceipt);
        assertThat(store.releaseBinding("release").state()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_release_bindings", Long.class)).isEqualTo(1L);

        // An expired, unapplied candidate remains immutable but cannot revoke the old release's authority.
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));
        assertThatThrownBy(() -> bindRelease("expired-candidate", next.manifest())).isInstanceOf(AdmissionFailure.class);
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release").manifestId())
            .isEqualTo(active.manifestId());
        assertThat(store.releaseBinding("expired-candidate")).isNull();
        AdmissionContext later = reviewedVersion("manifest-v3", "version-v3", vector(1));
        store.reserve(later, new Reserve("apply-v3", later.manifest().id(), scope(Operation.PROMOTION_APPLY, "apply-v3", null), active.id()),
            evidence(later, NOW.plusSeconds(61)), "owner", NOW.plusSeconds(61));
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release").manifestId())
            .isEqualTo(active.manifestId());
    }

    @Test void failedApplyTransactionRollsBackManifestCommitAndKeepsTheOldReleaseUsable() {
        Reservation active = activeAllocation();
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        reserveVersion(next, active, "apply-v2", null);
        Reservation planned = store.require(active.id());
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            var deployment = new DeploymentEntity(); deployment.setId("deployment");
            deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
            var version = new DeploymentVersionEntity(); version.setId(next.manifest().versionId());
            version.setConfigHash(next.manifest().versionConfigHash());
            var release = new DeploymentReleaseEntity(); release.setId("rolled-back-apply"); release.setTargetProfileId(PROD);
            store.bindRelease(deployment, version, release);
            assertThat(store.require(active.id()).manifestId()).isEqualTo(next.manifest().id());
            throw new IllegalStateException("apply transaction failed");
        })).hasMessage("apply transaction failed");

        assertThat(store.require(active.id())).isEqualTo(planned);
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release")).isEqualTo(planned);
        assertThat(store.releaseBinding("rolled-back-apply")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(3L);
        assertThat(bindRelease("retry-apply", next.manifest()).versionId()).isEqualTo(next.manifest().versionId());
    }

    @ParameterizedTest @ValueSource(strings = {"PROVISIONING", "OUTCOME_UNKNOWN", "CLEANUP_FAILED", "RELEASED",
        "NOTICE_PENDING", "CLAIMED", "HANDED_OFF", "worker", "retirement", "failure", "expired"})
    void newVersionCannotReplaceBusyExpiredOrRetiringAllocation(String condition) {
        Reservation active = activeAllocation();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(active);
        switch (condition) {
            case "PROVISIONING", "OUTCOME_UNKNOWN", "CLEANUP_FAILED", "RELEASED" -> payload.put("state", condition);
            case "NOTICE_PENDING", "CLAIMED", "HANDED_OFF" -> payload.put("expiryState", condition);
            case "worker" -> payload.put("workerToken", "claimed-worker");
            case "retirement" -> payload.put("retirementOperationId", "retirement");
            case "failure" -> payload.put("failureCode", "UNCERTAIN_PROVIDER_OUTCOME");
            case "expired" -> payload.put("expiresAt", NOW.minusSeconds(1).toString());
            default -> throw new AssertionError(condition);
        }
        jdbc.update("UPDATE deployment_capacity_reservations SET state=?,expiry_state=?,current_slot=?,payload_json=? WHERE id=?",
            payload.path("state").asText(), payload.path("expiryState").asText(), "RELEASED".equals(condition)
                ? null : active.deploymentId() + "|" + active.targetProfileId(), json.write(payload), active.id());
        Reservation blocked = store.require(active.id());
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));

        assertThatThrownBy(() -> reserveVersion(next, active, "apply-v2", null)).isInstanceOf(AdmissionFailure.class);
        assertThatThrownBy(() -> store.reserve(next, new Reserve("plan-v2", next.manifest().id(),
            scope(Operation.PROMOTION_PLAN, "plan-v2", null), active.id()), evidence(next, NOW), "owner", NOW))
            .isInstanceOf(AdmissionFailure.class);

        assertThat(store.require(active.id())).isEqualTo(blocked);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(2L);
    }

    @Test void reapplyRequiresNewVersionReviewedScopeAndTheExactActiveRelease() {
        Reservation active = activeAllocation();
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        assertThatThrownBy(() -> reserveVersion(next, active, "foreign-release", "foreign-release"))
            .hasMessageContaining("ALLOCATION_REAPPLY_SCOPE_INVALID");
        assertThatThrownBy(() -> store.reserve(next, new Reserve("activate-new-manifest", next.manifest().id(),
            scope(Operation.ACTIVATE, "activate-new-manifest", "release"), active.id()), evidence(next, NOW), "owner", NOW))
            .hasMessageContaining("ALLOCATION_REAPPLY_SCOPE_INVALID");
        AdmissionContext sameVersion = reviewedVersion("changed-review", "version", vector(1));
        assertThatThrownBy(() -> reserveVersion(sameVersion, active, "same-version", null))
            .hasMessageContaining("ALLOCATION_REAPPLY_REQUIRES_NEW_REVIEWED_VERSION");
        AdmissionContext otherTenant = store.context("customer", "other-tenant", "deployment", next.manifest().id());
        var original = scope(Operation.PROMOTION_APPLY, "foreign-tenant", null);
        var foreign = new CustomerOfferContracts.AdmissionContext(original.customerId(), "other-tenant", original.deploymentId(),
            null, original.targetProfileId(), original.intent(), original.offerId(), original.offerFingerprint(),
            original.entitlementRevision(), original.placementEvidenceId(), original.operation(), original.operationKey(), original.limits(), original.topology());
        assertThatThrownBy(() -> store.reserve(otherTenant, new Reserve("foreign-tenant", next.manifest().id(), foreign, active.id()),
            evidence(otherTenant, NOW), "owner", NOW)).hasMessageContaining("ALLOCATION_REAPPLY_SCOPE_INVALID");
        var foreignOffer = new CustomerOfferContracts.AdmissionContext(original.customerId(), original.tenantId(), original.deploymentId(),
            null, original.targetProfileId(), original.intent(), "other-offer", "other-offer-fingerprint", original.entitlementRevision(),
            original.placementEvidenceId(), original.operation(), "foreign-offer", original.limits(), original.topology());
        assertThatThrownBy(() -> store.reserve(next, new Reserve("foreign-offer", next.manifest().id(), foreignOffer, active.id()),
            evidence(next, NOW), "owner", NOW)).hasMessageContaining("ALLOCATION_REAPPLY_SCOPE_INVALID");
        jdbc.update("UPDATE deployment_admission_release_bindings SET generation='foreign-generation' WHERE release_id='release'");
        assertThatThrownBy(() -> reserveVersion(next, active, "bad-generation", null)).isInstanceOf(AdmissionFailure.class);
        assertThat(store.require(active.id())).isEqualTo(active);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(2L);
    }

    @Test void reapplyDoesNotAllocateTwiceOrGrowTheHeldCapacityEnvelope() {
        Reservation active = activeAllocation();
        AdmissionContext enlarged = reviewedVersion("larger", "larger-version", with(vector(2), Dimension.ENVIRONMENTS, 1));
        assertThatThrownBy(() -> reserveVersion(enlarged, active, "larger-apply", null))
            .hasMessageContaining("ALLOCATION_REAPPLY_CAPACITY_REVIEW_REQUIRED");
        assertThat(store.require(active.id())).isEqualTo(active);
        var other = manifest("other", "other-deployment");
        store.approveManifest(new Manifest(other.id(), other.deploymentId(), other.versionId(), other.versionConfigHash(), PROD,
            other.bindingFingerprint(), other.topologyReference(), other.demand(), other.prerequisiteReferences(), other.customerPrerequisiteReferences()), "staff", NOW);
        var otherContext = store.context("customer", "tenant", "other-deployment", "other");
        store.reserve(otherContext, new Reserve("other", "other"), evidence(otherContext, NOW), "owner", NOW);
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        reserveVersion(next, active, "apply-v2", null);
        assertThat(store.held()).hasSize(2);
        assertThat(store.require(active.id()).demand()).isEqualTo(vector(1));
    }

    @ParameterizedTest @ValueSource(strings = {"customer_id", "tenant_id", "deployment_id", "target_profile_id",
        "version_id", "version_config_hash", "generation"})
    void reapplyRequiresAnUncorruptedOwnedCurrentRelease(String field) {
        Reservation active = activeAllocation();
        // Columns are closed test parameters, not request input.
        jdbc.update("UPDATE deployment_admission_release_bindings SET " + field + "='foreign' WHERE release_id='release'");
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));

        assertThatThrownBy(() -> reserveVersion(next, active, "apply-v2", null)).isInstanceOf(AdmissionFailure.class);

        assertThat(store.require(active.id())).isEqualTo(active);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(2L);
    }

    @Test void reapplyCannotBypassFinalOwnershipAndRetirementGuard() {
        Reservation active = activeAllocation();
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        var command = new Reserve("apply-v2", next.manifest().id(), scope(Operation.PROMOTION_APPLY, "apply-v2", null), active.id());
        for (String code : List.of("TARGET_RETIREMENT_FENCED", "ADMISSION_OWNERSHIP_CHANGED")) {
            assertThatThrownBy(() -> store.reserve(next, command, evidence(next, NOW), "owner", NOW,
                () -> { throw new AdmissionFailure(org.springframework.http.HttpStatus.CONFLICT, code); }))
                .hasMessageContaining(code);
        }
        assertThat(store.require(active.id())).isEqualTo(active);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(2L);
    }

    @ParameterizedTest @ValueSource(strings = {"PROMOTION_PLAN", "PROMOTION_APPLY"})
    void reserveWaitsForAllocationBeforeLockingTheActiveBindingSoBindCanCommit(String operation) throws Exception {
        Reservation active = activeAllocation();
        AdmissionContext applying = reviewedVersion("manifest-v2", "version-v2", vector(1));
        reserveVersion(applying, active, "apply-v2", null);
        AdmissionContext later = reviewedVersion("manifest-v3", "version-v3", vector(1));
        String allocationLock = "SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE";
        String bindingLock = "SELECT release_id FROM deployment_admission_release_bindings WHERE release_id=? FOR UPDATE";
        var firstLock = new java.util.concurrent.atomic.AtomicReference<String>();
        var lockAttempted = new java.util.concurrent.CountDownLatch(1);
        var tracingJdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource) {
            @Override public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                if ((allocationLock.equals(sql) || bindingLock.equals(sql)) && firstLock.compareAndSet(null, sql)) {
                    lockAttempted.countDown();
                }
                return super.queryForObject(sql, requiredType, args);
            }
        };
        var reservingStore = new AdmissionStore(tracingJdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        var attempt = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(ignored -> {
                jdbc.queryForObject(allocationLock, String.class, active.id());
                attempt.set(worker.submit(() -> {
                    try {
                        reservingStore.reserve(later, new Reserve("candidate-v3", later.manifest().id(),
                            scope(Operation.valueOf(operation), "candidate-v3", null), active.id()), evidence(later, NOW), "owner", NOW);
                        return "UNEXPECTEDLY_PLANNED";
                    } catch (AdmissionFailure rejected) { return rejected.getReason(); }
                }));
                try {
                    assertThat(lockAttempted.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new AssertionError(error);
                }
                assertThat(firstLock.get()).isEqualTo(allocationLock);
                // The reserve cannot pass the allocation held here, so its source binding must remain free.
                assertThat(jdbc.queryForObject(bindingLock + " NOWAIT", String.class, "release")).isEqualTo("release");
                assertThat(bindRelease("release-v2", applying.manifest()).authorizationKey()).isEqualTo("apply-v2");
            });
            assertThat(attempt.get().get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(
                "PROMOTION_PLAN".equals(operation) ? "ACTIVE_RELEASE_ALLOCATION_REQUIRED" : "ALLOCATION_RELEASE_ALREADY_IN_PROGRESS");
        }
        assertThat(store.require(active.id()).manifestId()).isEqualTo(applying.manifest().id());
        assertThat(store.releaseBinding("release-v2").state()).isEqualTo("QUEUED");
        assertThat(store.releaseBinding("release").state()).isEqualTo("ACTIVE");
        assertThat(store.held()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations WHERE operation_key='candidate-v3'", Long.class))
            .isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"PROMOTION_PLAN", "PROMOTION_APPLY"})
    void sourceReleaseStateIsRevalidatedAfterWaitingForItsRowLock(String operation) throws Exception {
        Reservation active = activeAllocation();
        AdmissionContext next = reviewedVersion("manifest-v2", "version-v2", vector(1));
        var releaseLocked = new java.util.concurrent.CountDownLatch(1);
        var admissionChecked = new java.util.concurrent.CountDownLatch(1);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var stateChange = workers.submit(() -> transaction.executeWithoutResult(ignored -> {
                    jdbc.queryForObject("SELECT release_id FROM deployment_admission_release_bindings WHERE release_id='release' FOR UPDATE", String.class);
                    releaseLocked.countDown();
                    try {
                        if (!admissionChecked.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Admission did not reach its final guard.");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt(); throw new AssertionError(error);
                    }
                    jdbc.update("UPDATE deployment_admission_release_bindings SET state='OUTCOME_UNKNOWN' WHERE release_id='release'");
                }));
                assertThat(releaseLocked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var reapply = workers.submit(() -> assertThatThrownBy(() -> store.reserve(next,
                    new Reserve("candidate-v2", next.manifest().id(), scope(Operation.valueOf(operation), "candidate-v2", null), active.id()),
                    evidence(next, NOW), "owner", NOW, admissionChecked::countDown)).isInstanceOf(AdmissionFailure.class));
                stateChange.get(15, java.util.concurrent.TimeUnit.SECONDS);
                reapply.get(15, java.util.concurrent.TimeUnit.SECONDS);
            } finally { admissionChecked.countDown(); }
        }
        assertThat(store.require(active.id())).isEqualTo(active);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(2L);
    }

    private AdmissionContext reviewedVersion(String id, String versionId, Vector demand) {
        Manifest previous = store.reviewed("MANIFEST", "manifest", Manifest.class);
        store.approveManifest(new Manifest(id, "deployment", versionId, "hash-" + versionId, PROD, "binding-" + versionId,
            previous.topologyReference(), demand, previous.prerequisiteReferences(), previous.customerPrerequisiteReferences()), "staff", NOW);
        return store.context("customer", "tenant", "deployment", id);
    }

    private Reservation reserveVersion(AdmissionContext next, Reservation active, String key, String releaseId) {
        return store.reserve(next, new Reserve(key, next.manifest().id(), scope(Operation.PROMOTION_APPLY, key, releaseId), active.id()),
            evidence(next, NOW), "owner", NOW);
    }

    private Reservation activeAllocation() {
        Reservation allocation = reserveOperation(Operation.PROMOTION_PLAN, "plan", null, null);
        reserveOperation(Operation.PROMOTION_APPLY, "apply", allocation.id(), null);
        Reservation provisioning = store.beginReleaseProvisioning(bindRelease("release"), context, evidence(context, NOW), "worker", NOW);
        return settle(provisioning, "release");
    }

    private Reservation settle(Reservation provisioning, String releaseId) {
        Reservation active = store.reconcile(provisioning.fence(), new ResourceReadback(provisioning.id(), provisioning.generation(),
            provisioning.revision(), null, provisioning.manifestFingerprint(), provisioning.bindingFingerprint(), PRODUCTION,
            NOW, "provider-proof", true, true, false, false, false, false, 0, null, null, null, null), false, "worker", NOW);
        store.releaseBindingState(releaseId, "ACTIVE");
        return active;
    }

    private Reservation reserveOperation(Operation operation, String key, String allocationId, String releaseId) {
        return store.reserve(context, new Reserve(key, "manifest", scope(operation, key, releaseId), allocationId),
            evidence(context, NOW), "owner", NOW);
    }

    private CustomerOfferContracts.AdmissionContext scope(Operation operation, String key, String releaseId) {
        return new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", releaseId, PROD,
            CustomerOfferContracts.TargetIntent.CUSTOMER_PRODUCTION, "offer", "offer-fingerprint", 1, "placement",
            operation, key, new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
    }

    private ReleaseBinding bindRelease(String id) {
        return bindRelease(id, store.reviewed("MANIFEST", "manifest", Manifest.class));
    }

    private ReleaseBinding bindRelease(String id, Manifest manifest) {
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        var version = new DeploymentVersionEntity(); version.setId(manifest.versionId()); version.setConfigHash(manifest.versionConfigHash());
        var release = new DeploymentReleaseEntity(); release.setId(id); release.setTargetProfileId(manifest.targetProfileId());
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> store.bindRelease(deployment, version, release));
        return store.releaseBinding(id);
    }
}

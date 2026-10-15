package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.deployment.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    private Reservation reserveOperation(Operation operation, String key, String allocationId, String releaseId) {
        return store.reserve(context, new Reserve(key, "manifest", scope(operation, key, releaseId), allocationId),
            evidence(context, NOW), "owner", NOW);
    }

    private CustomerOfferContracts.AdmissionContext scope(Operation operation, String key, String releaseId) {
        return new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", releaseId, PROD,
            CustomerOfferContracts.TargetIntent.CUSTOMER_PRODUCTION, "offer", "offer-fingerprint", 1, "placement",
            operation, key, new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1));
    }

    private ReleaseBinding bindRelease(String id) {
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setConfigHash("config-hash");
        var release = new DeploymentReleaseEntity(); release.setId(id); release.setTargetProfileId(PROD);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> store.bindRelease(deployment, version, release));
        return store.releaseBinding(id);
    }
}

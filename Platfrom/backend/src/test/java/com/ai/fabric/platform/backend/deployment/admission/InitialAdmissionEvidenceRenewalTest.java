package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class InitialAdmissionEvidenceRenewalTest extends AdmissionFixture {
    private Reservation original;
    private AdmissionContext admitted;
    private CustomerOfferContracts.AdmissionContext originalScope;
    private CustomerOfferContracts.AdmissionContext freshScope;
    private String originalAuthorization;
    private String originalGrant;
    private MutableClock clock;

    @BeforeEach void initialUnusedApply() {
        for (String ddl : List.of(
            "CREATE TABLE platform_deployments(id VARCHAR(64) PRIMARY KEY,customer_id VARCHAR(64),tenant_id VARCHAR(64))",
            "CREATE TABLE platform_deployment_versions(id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),status VARCHAR(32),config_hash VARCHAR(255))",
            "CREATE TABLE platform_deployment_releases(id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE deployment_managed_dependency_graphs(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE provider_usage_allocations(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE platform_target_retirement_operations(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE platform_target_retirement_gates(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),state VARCHAR(64),epoch BIGINT,operation_id VARCHAR(64),row_version BIGINT NOT NULL DEFAULT 0)",
            "CREATE TABLE platform_deployment_deletion_operations(id VARCHAR(64),deployment_id VARCHAR(64))",
            "CREATE TABLE platform_consumers(id VARCHAR(64),consumer_id VARCHAR(128),customer_id VARCHAR(64),status VARCHAR(64),bound_deployment_id VARCHAR(64),bound_target_profile_id VARCHAR(64),bound_release_id VARCHAR(64),binding_revision BIGINT,row_version BIGINT)",
            "CREATE TABLE deployment_provider_resource_handles(id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),target_profile_id VARCHAR(64),release_id VARCHAR(64),resource_kind VARCHAR(64),status VARCHAR(64),metadata_json TEXT,provider_type VARCHAR(32),provider_resource_uuid VARCHAR(255),provider_project_uuid VARCHAR(255),provider_environment_uuid VARCHAR(255),provider_server_uuid VARCHAR(255),fqdn TEXT,created_at TIMESTAMP WITH TIME ZONE,updated_at TIMESTAMP WITH TIME ZONE)",
            "CREATE TABLE platform_runtime_authority_snapshots(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),version_id VARCHAR(64),release_id VARCHAR(64),fingerprint VARCHAR(64),binding_json TEXT)",
            "CREATE TABLE platform_runtime_target_authorities(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),customer_id VARCHAR(64),tenant_id VARCHAR(64),generation BIGINT)")) jdbc.execute(ddl);

        jdbc.execute("CREATE TABLE provider_usage_grants(id VARCHAR(96),customer_id VARCHAR(64),target_profile_id VARCHAR(64),fingerprint VARCHAR(64),payload_json TEXT,lifecycle_status VARCHAR(16),allocated_chat_attempts BIGINT,allocated_total_tokens BIGINT,allocated_cost_nano_usd BIGINT)");
        jdbc.execute("CREATE TABLE platform_consumer_binding_history(id VARCHAR(64),consumer_entity_id VARCHAR(64),customer_id VARCHAR(64),from_deployment_id VARCHAR(64),to_deployment_id VARCHAR(64),from_release_id VARCHAR(64),to_release_id VARCHAR(64),from_target_profile_id VARCHAR(64),to_target_profile_id VARCHAR(64),from_binding_revision BIGINT,to_binding_revision BIGINT,operation_key VARCHAR(128))");
        jdbc.execute("CREATE TABLE platform_customer_offer_entitlements(id VARCHAR(64),customer_id VARCHAR(64),offer_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE platform_customer_offer_deployments(deployment_id VARCHAR(64),customer_id VARCHAR(64),tenant_id VARCHAR(64),offer_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE platform_customer_entitlement_commands(id VARCHAR(64),entitlement_id VARCHAR(64),operation_key VARCHAR(128),command_type VARCHAR(64),result_json TEXT)");
        jdbc.execute("CREATE TABLE ai_workspace_installations(id VARCHAR(64),customer_id VARCHAR(64),consumer_entity_id VARCHAR(64),status VARCHAR(32),activated_at TIMESTAMP WITH TIME ZONE,disabled_at TIMESTAMP WITH TIME ZONE,row_version BIGINT)");
        jdbc.update("INSERT INTO platform_deployments VALUES ('deployment','customer','tenant')");
        jdbc.update("INSERT INTO platform_deployment_versions VALUES ('version','deployment','PUBLISHED','config-hash')");
        jdbc.update("INSERT INTO provider_usage_grants VALUES ('grant','customer',?,'grant-fingerprint','unchanged-grant-payload','ACTIVE',0,0,0)", TARGET);
        approve(2); store.approveManifest(manifest("manifest", "deployment"), "staff", NOW);
        admitted = context("manifest");
        originalScope = new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", null, TARGET,
            CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", 1, "placement-P1",
            CustomerOfferContracts.Operation.APPLY, "apply",
            new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
        original = store.reserve(admitted, new Reserve("apply", "manifest", originalScope), evidence(admitted, NOW), "owner", NOW);
        freshScope = scope("placementEvidenceId", "placement-P2");
        originalAuthorization = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_authorizations", String.class);
        originalGrant = store.initialProviderGrantsFingerprint(original);
        clock = new MutableClock(NOW.plusSeconds(120));
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), clock);
    }

    @Test void renewsExpiredInitialEvidenceWithoutRewritingAuthorizationOrCapacityLifetime() {
        var renewed = renew(original.fence(), freshScope, () -> { });
        assertThat(renewed.revision()).isEqualTo(1);
        assertThat(renewed.id()).isEqualTo(original.id());
        assertThat(renewed.generation()).isEqualTo(original.generation());
        assertThat(renewed.expiresAt()).isEqualTo(original.expiresAt());
        assertThat(renewed.createdAt()).isEqualTo(original.createdAt());
        assertThat(renewed.lastActivityAt()).isEqualTo(original.lastActivityAt());
        assertThat(renewed.demand()).isEqualTo(original.demand());
        assertThat(renewed.customerContextFingerprint()).isEqualTo(original.customerContextFingerprint());
        assertThat(renewed.state()).isEqualTo(State.RESERVED);
        assertThat(renewed.prerequisiteReadback()).isEqualTo(evidence(admitted, clock.instant()));
        assertImmutableOriginals();
        assertThat(proofCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_usage_allocations", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_release_bindings", Long.class)).isZero();
    }

    @Test void initialEvidenceRenewalUsesExactMeasuredPolicyContinuityWithoutChangingOriginalAuthorityOrFunding() {
        var next = AdmissionPolicyContinuityTest.continuation(store, json, "policy-next", List.of(original), clock.instant(), clock.instant().plusSeconds(300));
        AdmissionPolicyContinuityTest.publishFixture(store, jdbc, next, clock.instant());
        var current = store.context(original);
        var renewed = store.renewInitialCustomerEvidence(original.fence(), current, freshScope, evidence(current, clock.instant()),
            originalGrant, "owner", clock.instant(), () -> {});
        store.requireCustomerAuthorization(renewed, freshScope);
        assertThat(renewed.policyId()).isEqualTo(original.policyId());
        assertThat(renewed.expiresAt()).isEqualTo(original.expiresAt());
        assertThat(store.context(renewed).policy().id()).isEqualTo("policy-next");
        assertImmutableOriginals();
    }

    @Test void latestPlacementAloneFailsButRenewedContextIsExactAndOriginalKeyCanBindOnlyOnce() {
        assertThatThrownBy(() -> store.requireCustomerAuthorization(original, freshScope)).hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        var renewed = renew(original.fence(), freshScope, () -> { });
        store.requireCustomerAuthorization(renewed, freshScope);
        assertThatThrownBy(() -> store.requireCustomerAuthorization(renewed, originalScope)).hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        assertThatThrownBy(() -> store.requireCustomerAuthorization(renewed, scope("placementEvidenceId", "unreviewed-P3")))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        ReleaseBinding binding = bind("release");
        assertThat(binding.authorizationKey()).isEqualTo("apply");
        assertThatThrownBy(() -> bind("duplicate-release")).hasMessageContaining("EXACT_APPLY_AUTHORIZATION_REQUIRED");
        assertImmutableOriginals();
    }

    @Test void restartAndLostResponseRequireReadbackAndCurrentFenceThenIdenticalEvidenceIsANoop() {
        var renewed = renew(original.fence(), freshScope, () -> { });
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), clock);
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> { })).hasMessageContaining("STALE_RESERVATION_FENCE");
        assertThat(renew(renewed.fence(), freshScope, () -> { })).isEqualTo(renewed);
        assertThat(proofCount()).isEqualTo(1);
        store.requireCustomerAuthorization(store.require(original.id()), freshScope);
        assertImmutableOriginals();
    }

    @Test void secondFreshPlacementAppendsEvidenceWithoutOverwritingEarlierProof() {
        var renewed = renew(original.fence(), freshScope, () -> { });
        String first = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF'", String.class);
        clock.now = NOW.plusSeconds(130);
        var newerScope = scope("placementEvidenceId", "placement-P3");
        var newer = renew(renewed.fence(), newerScope, () -> { });
        assertThat(newer.revision()).isEqualTo(2);
        assertThat(proofCount()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF'", String.class)).contains(first);
        assertThatThrownBy(() -> store.requireCustomerAuthorization(newer, freshScope)).hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        store.requireCustomerAuthorization(newer, newerScope);
        assertImmutableOriginals();
    }

    @Test void bindingAndActiveReleaseReadersResolveRenewalRatherThanExpiredOriginalContext() {
        var renewed = renew(original.fence(), freshScope, () -> { });
        var binding = bind("release");
        clock.now = NOW.plusSeconds(300);
        var provisioning = store.beginReleaseProvisioning(binding, admitted, evidence(admitted, clock.instant()), "worker", clock.instant());
        var active = store.reconcile(provisioning.fence(), new ResourceReadback(provisioning.id(), provisioning.generation(), provisioning.revision(),
            null, provisioning.manifestFingerprint(), provisioning.bindingFingerprint(), STAGING, clock.instant(), "provider-proof",
            true, true, false, false, false, false, 0, null, null, null, null), false, "worker", clock.instant());
        store.releaseBindingState("release", "ACTIVE");
        assertThat(store.activeCustomerReleaseReservation("customer", "tenant", "deployment", "release")).isEqualTo(active);
        var releaseScope = scope("releaseId", "release");
        var allocation = new TransactionTemplate(new DataSourceTransactionManager(dataSource))
            .execute(tx -> store.customerPrerequisiteReleaseAllocation(releaseScope));
        assertThat(allocation.admittedContext()).isEqualTo(freshScope);
        assertThat(renewed.expiresAt()).isEqualTo(active.expiresAt());
        assertImmutableOriginals();
    }

    @ParameterizedTest @ValueSource(strings={"CLOSED","EXHAUSTED","ACTIVE"})
    void unrelatedHistoricalSpentGrantsArePreservedButDoNotBlockRenewal(String status) {
        jdbc.update("INSERT INTO provider_usage_grants VALUES ('historic','customer',?,'historic-fingerprint','historic-payload',?,2,30,40)", TARGET, status);
        originalGrant = store.initialProviderGrantsFingerprint(original);
        assertThat(renew(original.fence(), freshScope, () -> { }).revision()).isEqualTo(1);
        assertImmutableOriginals();
    }

    @ParameterizedTest @ValueSource(strings={"customerId","tenantId","deploymentId","targetProfileId","offerId","offerFingerprint","operationKey","placementEvidenceId"})
    void changedScopeCannotRenew(String field) {
        var changed = scope(field, field.equals("placementEvidenceId") ? "" : "changed");
        assertThatThrownBy(() -> renew(original.fence(), changed, () -> { })).isInstanceOf(AdmissionFailure.class);
        assertUnchanged();
    }

    @Test void changedEntitlementOperationLimitsAndPhysicalPlacementFailClosed() {
        for (var changed : List.of(scope("entitlementRevision", 2), scope("operation", "PROMOTION_APPLY"),
            scope("releaseId", "release"), scope("limits", null), scope("topology", null))) {
            assertThatThrownBy(() -> renew(original.fence(), changed, () -> { })).isInstanceOf(AdmissionFailure.class);
            assertUnchanged();
        }
        var e = evidence(admitted, clock.instant());
        var elsewhere = new PrerequisiteReadback(e.observedAt(), e.validUntil(), e.evidenceReference(),
            new Placement("other-server", STAGING.destinationUuid(), STAGING.ip()), e.profileFingerprint(), e.manifestFingerprint(),
            e.bindingFingerprint(), e.verifiedPrerequisiteReferences(), e.restoreEvidenceReference(), e.privacyEvidenceReference(),
            e.ownerContactEvidenceReference(), e.noticeTransportReference());
        assertThatThrownBy(() -> store.renewInitialCustomerEvidence(original.fence(), admitted, freshScope, elsewhere,
            originalGrant, "owner", clock.instant(), () -> { })).hasMessageContaining("ADMISSION_PREREQUISITE_UNVERIFIED");
        assertUnchanged();
    }

    @Test void revokedAuthorityFailureRollsBackAndDifferentOwnerIsRejected() {
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> { throw new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN,"ENTITLEMENT_REVOKED"); }))
            .hasMessageContaining("ENTITLEMENT_REVOKED");
        assertThatThrownBy(() -> store.renewInitialCustomerEvidence(original.fence(), admitted, freshScope, evidence(admitted, clock.instant()),
            originalGrant, "other-owner", clock.instant(), () -> { })).hasMessageContaining("INITIAL_RENEWAL_OWNER_CHANGED");
        assertUnchanged();
    }

    @ParameterizedTest @ValueSource(strings={"release","binding","graph","allocation","retirement","deletion","consumer","handle","authority","event","gate"})
    void anyKnownOrUnknownEffectsBlockEvidenceRenewal(String effect) {
        switch(effect) {
            case "release" -> jdbc.update("INSERT INTO platform_deployment_releases VALUES ('release','deployment',?)", TARGET);
            case "binding" -> bind("release");
            case "graph" -> jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES ('graph','deployment',?)", TARGET);
            case "allocation" -> jdbc.update("INSERT INTO provider_usage_allocations VALUES ('allocation','deployment',?)", TARGET);
            case "retirement" -> jdbc.update("INSERT INTO platform_target_retirement_operations VALUES ('retirement','deployment',?)", TARGET);
            case "deletion" -> jdbc.update("INSERT INTO platform_deployment_deletion_operations VALUES ('delete','deployment')");
            case "consumer" -> jdbc.update("INSERT INTO platform_consumers(id,bound_deployment_id,bound_target_profile_id) VALUES ('consumer','deployment',?)", TARGET);
            case "handle" -> jdbc.update("INSERT INTO deployment_provider_resource_handles(id,deployment_id,target_profile_id,resource_kind) VALUES ('handle','deployment',?,'UNKNOWN_RESOURCE')", TARGET);
            case "authority" -> jdbc.update("INSERT INTO platform_runtime_authority_snapshots VALUES ('authority','deployment',?,'version','release','fingerprint','{}')", TARGET);
            case "event" -> jdbc.update("INSERT INTO deployment_admission_events VALUES ('event',?,1,'UNKNOWN_EFFECT','owner','proof',?)", original.id(), java.sql.Timestamp.from(NOW));
            case "gate" -> jdbc.update("INSERT INTO platform_target_retirement_gates VALUES ('gate','deployment',?,'RETIRING',1,'operation',0)", TARGET);
        }
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> { })).isInstanceOf(AdmissionFailure.class);
        assertThat(proofCount()).isZero();
        assertThat(store.require(original.id())).isEqualTo(original);
    }

    @Test void staleGenerationRevisionStateWorkerAndExpiredReservationCannotRenew() {
        for (Fence bad : List.of(new Fence(original.id(), 1, original.generation()), new Fence(original.id(), 0, "other-generation")))
            assertThatThrownBy(() -> renew(bad, freshScope, () -> { })).hasMessageContaining("STALE_RESERVATION_FENCE");
        for (String field : List.of("workerToken", "failureCode", "retirementOperationId")) {
            ObjectNode tree = mapper.valueToTree(original); tree.put(field, "unknown-work");
            jdbc.update("UPDATE deployment_capacity_reservations SET payload_json=? WHERE id=?", tree.toString(), original.id());
            assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> { })).hasMessageContaining("INITIAL_RENEWAL_REQUIRES_UNUSED_RESERVATION");
        }
        jdbc.update("UPDATE deployment_capacity_reservations SET payload_json=? WHERE id=?", json.write(original), original.id());
        clock.now = original.expiresAt();
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> { })).hasMessageContaining("INITIAL_RENEWAL_REQUIRES_UNUSED_RESERVATION");
        assertThat(proofCount()).isZero();
    }

    @Test void expiryOrGrantMutationDuringAuthorityRecheckCommitsNothing() {
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> clock.now=NOW.plusSeconds(6000)))
            .isInstanceOf(AdmissionFailure.class);
        clock.now=NOW.plusSeconds(120); assertUnchanged();
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> jdbc.update("UPDATE provider_usage_grants SET fingerprint='changed'")))
            .hasMessageContaining("INITIAL_RENEWAL_EFFECTS_CHANGED");
        assertUnchanged();
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> jdbc.update("INSERT INTO provider_usage_allocations VALUES ('racing','deployment',?)", TARGET)))
            .isInstanceOf(AdmissionFailure.class);
        assertUnchanged();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_usage_allocations", Long.class)).isZero();
    }

    @Test void concurrentRefreshesCannotAppendTwoProofsForOneExpectedRevision() throws Exception {
        try(var workers=Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> refresh=()->{start.await();try{renew(original.fence(),freshScope,()->{});return true;}catch(AdmissionFailure denied){return false;}};
            var a=workers.submit(refresh);var b=workers.submit(refresh);start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(store.require(original.id()).revision()).isEqualTo(1); assertThat(proofCount()).isEqualTo(1); assertImmutableOriginals();
    }

    @Test void deploymentOwnedByApplyMakesRenewalBusyWithoutWaitingOrMutating() throws Exception {
        var locked = new CountDownLatch(1);var release = new CountDownLatch(1);
        try(var worker=Executors.newSingleThreadExecutor()) {
            var pending=worker.submit(()->new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx->{
                jdbc.queryForObject("SELECT id FROM platform_deployments WHERE id='deployment' FOR UPDATE",String.class);locked.countDown();
                try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            try{assertThatThrownBy(()->renew(original.fence(),freshScope,()->{})).hasMessageContaining("INITIAL_RENEWAL_BUSY");}
            finally{release.countDown();}pending.get(10,TimeUnit.SECONDS);
        }
        assertUnchanged();
    }

    @Test void corruptedRenewalRelationFailsClosedInsteadOfFallingBackToOriginal() {
        var renewed=renew(original.fence(),freshScope,()->{});
        jdbc.update("UPDATE deployment_admission_reviews SET fingerprint='corrupted' WHERE kind='INITIAL_PROOF'");
        assertThatThrownBy(()->store.requireCustomerAuthorization(renewed,freshScope)).hasMessageContaining("INITIAL_RENEWAL_RECORD_CHANGED");
        assertImmutableOriginals();
    }

    @Test void canonicalRenewalFingerprintSurvivesUnorderedSetJsonRoundTrip() {
        var receipt = json.read(originalAuthorization, AuthorizationReceipt.class);
        var proof = new InitialAdmissionEvidenceRenewal(original.id(), original.generation(), receipt.requestFingerprint(),
            receipt.contextFingerprint(), 0, 1, freshScope, evidence(admitted, clock.instant()), "owner", clock.instant());
        var roundTrip = json.read(json.write(proof), InitialAdmissionEvidenceRenewal.class);
        assertThat(json.fingerprint(roundTrip)).isEqualTo(json.fingerprint(proof));
    }

    @ParameterizedTest @ValueSource(strings={"deleted-latest","rekeyed-latest","event-digest","deleted-event","deleted-review-and-event"})
    void committedRenewalHeadCannotFallBackToOlderOrOriginalEvidence(String defect) {
        var first = renew(original.fence(), freshScope, () -> { });
        clock.now = NOW.plusSeconds(130);
        var latestScope = scope("placementEvidenceId", "P3");
        var latest = renew(first.fence(), latestScope, () -> { });
        String key = jdbc.queryForObject("SELECT record_key FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF' ORDER BY record_key DESC LIMIT 1", String.class);
        switch (defect) {
            case "deleted-latest" -> jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF' AND record_key=?", key);
            case "rekeyed-latest" -> jdbc.update("UPDATE deployment_admission_reviews SET record_key='wrong-scope' WHERE kind='INITIAL_PROOF' AND record_key=?", key);
            case "event-digest" -> jdbc.update("UPDATE deployment_admission_events SET evidence_reference='unproven-head' WHERE reservation_id=? AND revision=2", original.id());
            case "deleted-event" -> jdbc.update("DELETE FROM deployment_admission_events WHERE reservation_id=? AND revision=2", original.id());
            case "deleted-review-and-event" -> {
                jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF' AND record_key=?", key);
                jdbc.update("DELETE FROM deployment_admission_events WHERE reservation_id=? AND revision=2", original.id());
            }
        }
        assertThatThrownBy(() -> store.requireCustomerAuthorization(latest, latestScope)).hasMessageContaining("INITIAL_RENEWAL_RECORD_CHANGED");
        assertThatThrownBy(() -> store.requireCustomerAuthorization(latest, freshScope)).hasMessageContaining("INITIAL_RENEWAL_RECORD_CHANGED");
        assertImmutableOriginals();
    }

    @Test void renewedUnusedAuthorizationCanBeCancelledAndCancellationReplaysWithoutRewritingOriginalReceipt() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V195__unused_admission_reservation_cancellation.sql")).execute(dataSource);
        var renewed = renew(original.fence(), freshScope, () -> { });
        var authorization = json.read(originalAuthorization, AuthorizationReceipt.class);
        var cancel = new CancelUnusedReservation(renewed.fence(), "cancel-renewed", "apply", authorization.requestFingerprint(),
            renewed.manifestFingerprint(), "version", "config-hash", renewed.bindingFingerprint());
        var receipt = store.cancelUnused("deployment", TARGET, cancel, "staff");
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(store.cancelUnused("deployment", TARGET, cancel, "staff")).isEqualTo(receipt);
        assertThat(proofCount()).isEqualTo(1);
        assertImmutableOriginals();
        assertThatThrownBy(() -> bind("late-release")).hasMessageContaining("ALLOCATION_NOT_CURRENT");
        var provider = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(store, json, mapper,
            org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository.class),
            org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository.class),
            org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository.class),
            org.mockito.Mockito.mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService.class),
            org.mockito.Mockito.mock(CustomerSourcePrerequisiteEvidenceStore.class),
            org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService.class), clock);
        assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
            .execute(tx -> provider.resolve(freshScope))).hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
    }

    @Test void requiredConfigurationOnlyConsumerAndDraftAreProvenAndPreservedWithoutDetachment() {
        configurationOnlyConsumer();
        var consumer = jdbc.queryForMap("SELECT * FROM platform_consumers");
        var history = jdbc.queryForMap("SELECT * FROM platform_consumer_binding_history");
        var draft = jdbc.queryForMap("SELECT * FROM ai_workspace_installations");
        var renewed = renew(original.fence(), freshScope, () -> { });
        assertThat(renewed.revision()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM platform_consumers")).isEqualTo(consumer);
        assertThat(jdbc.queryForMap("SELECT * FROM platform_consumer_binding_history")).isEqualTo(history);
        assertThat(jdbc.queryForMap("SELECT * FROM ai_workspace_installations")).isEqualTo(draft);
        assertImmutableOriginals();
    }

    @ParameterizedTest @ValueSource(strings={"active-release","binding-revision","foreign-owner","missing-history","foreign-offer","missing-receipt","activated-app","disabled-app","live-app"})
    void onlyServerProvenNeverActivatedInitialConsumerAssignmentsAreAllowed(String defect) {
        configurationOnlyConsumer();
        switch (defect) {
            case "active-release" -> jdbc.update("UPDATE platform_consumers SET bound_release_id='release'");
            case "binding-revision" -> jdbc.update("UPDATE platform_consumers SET binding_revision=2");
            case "foreign-owner" -> jdbc.update("UPDATE platform_consumers SET customer_id='other'");
            case "missing-history" -> jdbc.update("DELETE FROM platform_consumer_binding_history");
            case "foreign-offer" -> jdbc.update("UPDATE platform_customer_offer_entitlements SET offer_id='other'");
            case "missing-receipt" -> jdbc.update("DELETE FROM platform_customer_entitlement_commands");
            case "activated-app" -> jdbc.update("UPDATE ai_workspace_installations SET activated_at=CURRENT_TIMESTAMP");
            case "disabled-app" -> jdbc.update("UPDATE ai_workspace_installations SET disabled_at=CURRENT_TIMESTAMP");
            case "live-app" -> jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE'");
        }
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> { })).isInstanceOf(AdmissionFailure.class);
        assertUnchanged();
    }

    @Test void evenConfigurationOnlyConsumerOrDraftChangesDuringRecheckRollBack() {
        configurationOnlyConsumer();
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> jdbc.update("UPDATE platform_consumers SET row_version=7")))
            .hasMessageContaining("INITIAL_RENEWAL_EFFECTS_CHANGED");
        assertThat(jdbc.queryForObject("SELECT row_version FROM platform_consumers", Long.class)).isZero();
        assertThatThrownBy(() -> renew(original.fence(), freshScope, () -> jdbc.update("UPDATE ai_workspace_installations SET row_version=8")))
            .hasMessageContaining("INITIAL_RENEWAL_EFFECTS_CHANGED");
        assertThat(jdbc.queryForObject("SELECT row_version FROM ai_workspace_installations", Long.class)).isEqualTo(3);
        assertUnchanged();
    }

    void configurationOnlyConsumer() {
        jdbc.update("INSERT INTO platform_consumers VALUES ('consumer','config-consumer','customer','ACTIVE','deployment',?,NULL,1,0)", TARGET);
        jdbc.update("INSERT INTO platform_consumer_binding_history VALUES ('history','consumer','customer',NULL,'deployment',NULL,NULL,NULL,?,0,1,'create-config')", TARGET);
        jdbc.update("INSERT INTO platform_customer_offer_entitlements VALUES ('entitlement','customer','offer')");
        jdbc.update("INSERT INTO platform_customer_offer_deployments VALUES ('deployment','customer','tenant','offer')");
        String result = "{\"consumerId\":\"config-consumer\",\"customerId\":\"customer\",\"boundDeploymentId\":\"deployment\",\"boundTargetProfileId\":\"" + TARGET
            + "\",\"boundReleaseId\":null,\"bindingRevision\":1}";
        jdbc.update("INSERT INTO platform_customer_entitlement_commands VALUES ('create','entitlement','create-config','CREATE_STAGING_CONSUMER',?)", result);
        jdbc.update("INSERT INTO ai_workspace_installations VALUES ('draft','customer','consumer','DRAFT',NULL,NULL,3)");
    }

    Reservation renewCurrentFixture() { return renew(original.fence(), freshScope, () -> { }); }

    private Reservation renew(Fence fence, CustomerOfferContracts.AdmissionContext scope, Runnable guard) {
        return store.renewInitialCustomerEvidence(fence,admitted,scope,evidence(admitted,clock.instant()),
            store.initialProviderGrantsFingerprint(store.require(original.id())),"owner",clock.instant(),guard);
    }
    private CustomerOfferContracts.AdmissionContext scope(String field,Object value) {
        ObjectNode tree=mapper.valueToTree(originalScope);tree.set(field,mapper.valueToTree(value));
        return json.read(tree.toString(),CustomerOfferContracts.AdmissionContext.class);
    }
    private ReleaseBinding bind(String id) {
        var d=new DeploymentEntity();d.setId("deployment");d.setCustomerId("customer");d.setTenantId("tenant");
        var v=new DeploymentVersionEntity();v.setId("version");v.setConfigHash("config-hash");
        var r=new DeploymentReleaseEntity();r.setId(id);r.setTargetProfileId(TARGET);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx->store.bindRelease(d,v,r));
        return store.releaseBinding(id);
    }
    private long proofCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF'",Long.class);}
    private void assertImmutableOriginals(){
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_authorizations",String.class)).isEqualTo(originalAuthorization);
        assertThat(store.initialProviderGrantsFingerprint(original)).isEqualTo(originalGrant);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations",Long.class)).isEqualTo(1);
    }
    private void assertUnchanged(){assertThat(store.require(original.id())).isEqualTo(original);assertThat(proofCount()).isZero();assertImmutableOriginals();}
    private static final class MutableClock extends Clock {
        volatile Instant now;MutableClock(Instant now){this.now=now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}

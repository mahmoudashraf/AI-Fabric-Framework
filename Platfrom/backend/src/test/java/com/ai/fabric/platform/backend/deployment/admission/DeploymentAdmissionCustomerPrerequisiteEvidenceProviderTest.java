package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedLimits;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedTopology;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeploymentAdmissionCustomerPrerequisiteEvidenceProviderTest extends AdmissionFixture {
    private DeploymentRepository deployments;
    private DeploymentVersionRepository versions;
    private DeploymentTargetProfileRepository targets;
    private CustomerTargetPlacementService placement;
    private DeploymentTargetContextService targetContexts;
    private CustomerSourcePrerequisiteEvidenceStore sourceEvidence;
    private DeploymentAdmissionCustomerPrerequisiteEvidenceProvider provider;
    private DeploymentTargetProfileEntity targetProfile;
    private FleetPolicy reviewedPolicy;
    private String reviewedProfileFingerprint;
    private com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext customerContext;

    @BeforeEach
    void bridge() throws Exception {
        deployments = mock(DeploymentRepository.class);
        versions = mock(DeploymentVersionRepository.class);
        targets = mock(DeploymentTargetProfileRepository.class);
        placement = mock(CustomerTargetPlacementService.class);
        targetContexts = mock(DeploymentTargetContextService.class);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource(
            "db/migration/V180__release_scoped_source_prerequisite_evidence.sql")).execute(dataSource);
        sourceEvidence = new CustomerSourcePrerequisiteEvidenceStore(jdbc, json);

        targetProfile = new DeploymentTargetProfileEntity();
        targetProfile.setId(TARGET);
        targetProfile.setName("Reviewed staging");
        targetProfile.setActive(true);
        targetProfile.setProviderType(DeploymentProviderType.COOLIFY);
        targetProfile.setEnvironmentName("customer-staging");
        targetProfile.setSourceStrategy("IMAGE_SOURCE");
        targetProfile.setProviderConfigJson(mapper.writeValueAsString(Map.of(
            "serverUuid", STAGING.serverUuid(), "destinationUuid", STAGING.destinationUuid())));
        targetProfile.setNetworkPolicyJson("{}");
        targetProfile.setResourceDefaultsJson("{}");
        when(targets.findById(TARGET)).thenReturn(Optional.of(targetProfile));

        reviewedProfileFingerprint = DeploymentTargetProfileIdentity.fingerprint(mapper, targetProfile);
        Budget budget = new Budget(capacity(4), vector(0), vector(0), "measurement-proof",
            NOW.minusSeconds(1), NOW.plusSeconds(10_000));
        reviewedPolicy = new FleetPolicy("policy1", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, reviewedProfileFingerprint, budget)),
            policy(4).lifecycle(), "restore-proof", "privacy-proof", "support-owner");
        store.approvePolicy(reviewedPolicy, "operator", NOW);
        store.approveGrant(customerGrant(4), "operator", NOW);
        store.approveManifest(manifest("manifest1", "deployment1"), "operator", NOW);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("deployment1"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        deployment.setStatus("ACTIVE");
        when(deployments.findById("deployment1")).thenReturn(Optional.of(deployment));
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment1"); version.setConfigHash("config-hash");
        version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));

        customerContext = context("customer", "tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation", "offer-fingerprint", limits("measurement-proof"));
        when(placement.requireEvidenceById(eq(targetProfile), eq(TargetIntent.CUSTOMER_STAGING), eq("placement-proof")))
            .thenReturn(new CustomerPlacementEvidenceEntity("placement-proof", TARGET, TargetIntent.CUSTOMER_STAGING,
                reviewedProfileFingerprint, STAGING.serverUuid(), STAGING.ip(), STAGING.destinationUuid(),
                NOW.minusSeconds(1), NOW.plusSeconds(120)));
        provider = providerAt(NOW);
    }

    @Test
    void exactHeldReservationProvidesAllPrerequisitesAndReplaysDurably() {
        Reserve command = new Reserve("operation", "manifest1", customerContext);
        Reservation first = scopedReserve(command);
        Reservation replay = scopedReserve(command);

        assertThat(replay).isEqualTo(first);
        var evidence = provider.resolve(customerContext);
        assertThat(evidence.capacityReservationId()).isEqualTo(first.id());
        assertThat(evidence.context()).isEqualTo(customerContext);
        assertThat(evidence.passed()).containsExactlyInAnyOrder(Prerequisite.values());
        assertThat(evidence.evidenceReference()).contains(first.id(), first.generation());
        verify(placement).requireEvidenceById(targetProfile, TargetIntent.CUSTOMER_STAGING, "placement-proof");
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test
    void manifestMustCoverTheCompleteReviewedOfferTopology() {
        var incomplete = context("customer", "tenant", "deployment1", TARGET,
            TargetIntent.CUSTOMER_STAGING, Operation.APPLY, "incomplete-topology",
            "offer-fingerprint", limits("measurement-proof"), topology(2));
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        store.reserve(admission, new Reserve("incomplete-topology", "manifest1", incomplete),
            evidence(admission, NOW), "owner", NOW);

        assertRejected(incomplete, "CUSTOMER_REVIEWED_TOPOLOGY_INCOMPLETE");
    }

    @Test
    void sourceOperationsReuseTheExactActiveReleaseEvidenceWithoutAllocatingMoreCapacity() {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        var sourceContext = sourceContext(Operation.SOURCE_PREVIEW, null, "release-1", "offer-fingerprint", 2);
        sourceEvidence(sourceContext, NOW.plusSeconds(3), NOW.plusSeconds(60));

        var resolved = providerAt(NOW.plusSeconds(3)).resolve(sourceContext);

        assertThat(resolved.context()).isEqualTo(sourceContext);
        assertThat(resolved.capacityReservationId()).isEqualTo(allocation.id());
        assertThat(resolved.checkedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(resolved.passed()).contains(Prerequisite.STORAGE, Prerequisite.IDENTITY);
        assertThat(store.held()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class))
            .isEqualTo(1L);
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"SOURCE_DISCOVER", "SOURCE_PREVIEW", "SOURCE_INDEX", "SOURCE_REFRESH", "SOURCE_REMOVE"})
    void activeProductionSourceOperationsOutliveAdmissionExpiryWithoutRenewingTheAllocation(Operation operation) throws Exception {
        useProductionTarget();
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        Reservation active = store.require(allocation.id());
        ReleaseBinding binding = store.releaseBinding("release-1");
        var grant = store.customerGrant("customer");
        Instant later = active.expiresAt().plusSeconds(1);
        var source = sourceContext(operation, "daily-source-operation", "release-1", "offer-fingerprint", 2);
        freshSourceEvidence(source, later, later.plusSeconds(45));

        var snapshot = providerAt(later).sourceSnapshot(source);
        var resolved = providerAt(later).resolve(source);

        assertThat(snapshot.expiresAt()).isEqualTo(grant.validUntil());
        assertThat(resolved.expiresAt()).isEqualTo(later.plusSeconds(45));
        assertThat(resolved.capacityReservationId()).isEqualTo(active.id());
        assertThat(resolved.passed()).contains(Prerequisite.STORAGE).doesNotContain(Prerequisite.CAPACITY);
        assertThat(store.require(active.id())).isEqualTo(active);
        assertThat(store.releaseBinding("release-1")).isEqualTo(binding);
        assertThat(store.customerGrant("customer")).isEqualTo(grant);
        assertThat(store.held()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(1L);
    }

    @Test
    void activeStagingSourceOperationsRemainAdmissionExpiryFenced() {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        Reservation active = store.require(allocation.id());
        var source = sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1", "offer-fingerprint", 2);

        assertThatThrownBy(() -> providerAt(active.expiresAt()).sourceSnapshot(source))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        assertThat(store.require(active.id())).isEqualTo(active);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void productionSourceCandidatesRemainAdmissionExpiryFenced(boolean failedVerification) throws Exception {
        useProductionTarget();
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        Reservation candidate = provisionRelease("candidate-1");
        if (failedVerification) {
            candidate = store.outcome(candidate.fence(), State.OUTCOME_UNKNOWN, "verification-failed", "worker", NOW.plusSeconds(2));
            store.releaseBindingState("candidate-1", "OUTCOME_UNKNOWN");
            targetContexts.resolve("deployment1", TARGET, "candidate-1", false).release().setStatus("APPLIED_VERIFICATION_FAILED");
        }
        Reservation before = candidate;
        var source = sourceContext(Operation.SOURCE_INDEX, "candidate-index", "candidate-1", "offer-fingerprint", 2);

        assertThatThrownBy(() -> providerAt(before.expiresAt()).sourceSnapshot(source))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        assertThat(store.require(before.id())).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = ExpiryState.class, mode = EnumSource.Mode.EXCLUDE, names = "CURRENT")
    void productionExpiryFencesCannotUseTheActiveAllocationException(ExpiryState expiryState) throws Exception {
        useProductionTarget();
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        Reservation active = store.require(allocation.id());
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(active);
        payload.put("expiryState", expiryState.name());
        jdbc.update("UPDATE deployment_capacity_reservations SET expiry_state=?,payload_json=? WHERE id=?",
            expiryState.name(), json.write(payload), active.id());
        var source = sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1", "offer-fingerprint", 2);

        assertThatThrownBy(() -> providerAt(active.expiresAt()).sourceSnapshot(source))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERSEDED", "RETIRED"})
    void supersededOrRetiredProductionReleaseCannotUseTheActiveAllocationException(String state) throws Exception {
        useProductionTarget();
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        store.releaseBindingState("release-1", state);
        var source = sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1", "offer-fingerprint", 2);

        assertThatThrownBy(() -> providerAt(store.require(allocation.id()).expiresAt()).sourceSnapshot(source))
            .hasMessageContaining("ACTIVE_RELEASE_PREREQUISITE_EVIDENCE_REQUIRED");
    }

    @Test
    void activeProductionStillRequiresAnUnexpiredQuotaGrant() throws Exception {
        useProductionTarget();
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        var source = sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1", "offer-fingerprint", 2);

        assertThatThrownBy(() -> providerAt(store.customerGrant("customer").validUntil()).sourceSnapshot(source))
            .hasMessageContaining("ACTIVE_CUSTOMER_QUOTA_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void activeProductionSourceProofOutlivesAdmissionMeasurementsButRetainsPolicyAuthority(boolean continued) throws Exception {
        useProductionTarget();
        CustomerGrant grant = customerGrant(4);
        store.approveGrant(new CustomerGrant(grant.customerId(), grant.fleetLimit(), grant.targetLimits(), NOW.plusSeconds(20_000),
            grant.ownerContactEvidenceReference(), grant.payerReference(), grant.keepStagingEntitlementReference()), "operator", NOW);
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        Reservation active = store.require(allocation.id());
        if (continued) {
            var next = AdmissionPolicyContinuityTest.continuation(store, json, "production-next", List.of(active), NOW,
                NOW.plusSeconds(300));
            AdmissionPolicyContinuityTest.publishFixture(store, jdbc, next, NOW);
        }
        FleetPolicy policy = store.activePolicy();
        var policyRecords = jdbc.queryForList("SELECT * FROM deployment_admission_reviews WHERE kind IN ('POLICY','POLICY_CONT') ORDER BY kind,record_key");
        Instant later = reviewedPolicy.fleet().validUntil().plusSeconds(1);
        var source = sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1", "offer-fingerprint", 2);
        freshSourceEvidence(source, later, later.plusSeconds(45));

        assertThat(providerAt(later).sourceSnapshot(source).expiresAt()).isEqualTo(NOW.plusSeconds(20_000));
        assertThat(providerAt(later).resolve(source).expiresAt()).isEqualTo(later.plusSeconds(45));
        assertThat(store.require(active.id())).isEqualTo(active);
        assertThat(store.activePolicy()).isEqualTo(policy);
        assertThat(jdbc.queryForList("SELECT * FROM deployment_admission_reviews WHERE kind IN ('POLICY','POLICY_CONT') ORDER BY kind,record_key"))
            .isEqualTo(policyRecords);
        if (continued) {
            jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='POLICY_CONT'");
            assertThatThrownBy(() -> providerAt(later).sourceSnapshot(source)).hasMessageContaining("POLICY_CONTINUITY_REQUIRED");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fleet", "target", "both"})
    void activeProductionSourceExpiryIsNotCappedByEitherHistoricBudget(String expired) throws Exception {
        useProductionTarget();
        usePolicyDeadlines("target".equals(expired) ? NOW.plusSeconds(10_000) : NOW.plusSeconds(30),
            "fleet".equals(expired) ? NOW.plusSeconds(10_000) : NOW.plusSeconds(30));
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        var source = sourceContext(Operation.SOURCE_INDEX, "daily-index", "release-1", "offer-fingerprint", 2);
        freshSourceEvidence(source, NOW.plusSeconds(31), NOW.plusSeconds(90));

        assertThat(providerAt(NOW.plusSeconds(31)).sourceSnapshot(source).expiresAt()).isEqualTo(store.customerGrant("customer").validUntil());
        assertThat(providerAt(NOW.plusSeconds(31)).resolve(source).expiresAt()).isEqualTo(NOW.plusSeconds(90));
    }

    @ParameterizedTest
    @ValueSource(strings = {"staging", "provisioning", "failed-verification"})
    void stagingAndProductionCandidatesStillRequireFreshCapacityMeasurements(String lifecycle) throws Exception {
        if (!"staging".equals(lifecycle)) useProductionTarget();
        usePolicyDeadlines(NOW.plusSeconds(30), NOW.plusSeconds(30));
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        if ("staging".equals(lifecycle)) activateRelease("release-1", allocation);
        else {
            Reservation candidate = provisionRelease("release-1");
            if ("failed-verification".equals(lifecycle)) {
                store.outcome(candidate.fence(), State.OUTCOME_UNKNOWN, "verification-failed", "worker", NOW.plusSeconds(2));
                store.releaseBindingState("release-1", "OUTCOME_UNKNOWN");
                targetContexts.resolve("deployment1", TARGET, "release-1", false).release().setStatus("APPLIED_VERIFICATION_FAILED");
            }
        }
        var source = sourceContext(Operation.SOURCE_INDEX, "source-index", "release-1", "offer-fingerprint", 2);

        assertThatThrownBy(() -> providerAt(NOW.plusSeconds(31)).sourceSnapshot(source))
            .hasMessageContaining("FRESH_MEASURED_CAPACITY_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"identity", "window", "vector", "lifecycle", "restore-reference", "fingerprint"})
    void activeProductionDoesNotTrustMalformedOrChangedPolicyRecords(String changed) throws Exception {
        useProductionTarget();
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(reviewedPolicy);
        String expected = switch (changed) {
            case "identity" -> { payload.put("id", "different-policy"); yield "CUSTOMER_ADMISSION_POLICY_IDENTITY_CHANGED"; }
            case "window" -> {
                ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("fleet")).put("validUntil", NOW.minusSeconds(2).toString());
                yield "CUSTOMER_ADMISSION_POLICY_STRUCTURE_INVALID";
            }
            case "vector" -> {
                ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("fleet").path("measured").path("amounts")).remove("MEMORY_BYTES");
                yield "COMPLETE_RESOURCE_ENVELOPE_REQUIRED";
            }
            case "lifecycle" -> {
                ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("lifecycle")).put("readbackMaxAgeSeconds", 0);
                yield "EXPLICIT_LIFECYCLE_POLICY_REQUIRED";
            }
            case "restore-reference" -> { payload.put("restoreEvidenceReference", ""); yield "ADMISSION_INPUT_INVALID"; }
            case "fingerprint" -> { payload.put("supportReference", "changed-support"); yield "POLICY_CONTINUITY_RECORD_CHANGED"; }
            default -> throw new AssertionError(changed);
        };
        FleetPolicy corrupt = json.read(payload.toString(), FleetPolicy.class);
        jdbc.update("UPDATE deployment_admission_reviews SET payload_json=?,fingerprint=? WHERE kind='POLICY' AND record_key=?",
            json.write(corrupt), "fingerprint".equals(changed) ? json.fingerprint(reviewedPolicy) : json.fingerprint(corrupt), reviewedPolicy.id());
        var source = sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1", "offer-fingerprint", 2);

        assertThatThrownBy(() -> providerAt(NOW.plusSeconds(1001)).sourceSnapshot(source)).hasMessageContaining(expected);
    }

    @Test
    void activeProductionAfterAdmissionExpiryStillNeedsFreshExactSourceAndPlacementProof() throws Exception {
        useProductionTarget();
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        Instant later = store.require(allocation.id()).expiresAt().plusSeconds(1);
        var source = sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1", "offer-fingerprint", 2);
        assertThatThrownBy(() -> providerAt(later).resolve(source)).hasMessageContaining("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        freshSourceEvidence(source, later.minusSeconds(61), later.plusSeconds(45));
        assertThatThrownBy(() -> providerAt(later).resolve(source)).hasMessageContaining("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        freshSourceEvidence(source, later, later.plusSeconds(45));
        assertThatThrownBy(() -> providerAt(later).resolve(sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1",
            "changed-offer", 2))).hasMessageContaining("ACTIVE_RELEASE_PREREQUISITE_SCOPE_CHANGED");
        assertThatThrownBy(() -> providerAt(later).resolve(sourceContext(Operation.SOURCE_REFRESH, "daily-refresh", "release-1",
            "offer-fingerprint", 3))).hasMessageContaining("ACTIVE_RELEASE_PREREQUISITE_SCOPE_CHANGED");
        targetContexts.resolve("deployment1", TARGET, "release-1", false).release().setDocumentBindingFingerprint("changed-storage");
        assertThatThrownBy(() -> providerAt(later).resolve(source)).hasMessageContaining("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
    }

    @ParameterizedTest @EnumSource(value=Operation.class, names={"SOURCE_DISCOVER","SOURCE_PREVIEW","SOURCE_INDEX","SOURCE_REFRESH","SOURCE_REMOVE"})
    void sourceAdministrationUsesExplicitCurrentMeasuredPolicyWithoutRewritingReleaseProvenance(Operation operation) {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        var active = store.require(allocation.id());
        var next = AdmissionPolicyContinuityTest.continuation(store, json, "policy-next", List.of(active), NOW, NOW.plusSeconds(300));
        AdmissionPolicyContinuityTest.publishFixture(store, jdbc, next, NOW);
        var source = sourceContext(operation, "source-after-rollover", "release-1", "offer-fingerprint", 2);
        sourceEvidence(source, NOW.plusSeconds(3), NOW.plusSeconds(60));
        var resolved = providerAt(NOW.plusSeconds(3)).resolve(source);
        assertThat(resolved.capacityReservationId()).isEqualTo(active.id());
        assertThat(store.require(active.id())).isEqualTo(active);
        assertThat(store.releaseBinding("release-1").reservationId()).isEqualTo(active.id());
        jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='POLICY_CONT'");
        assertThatThrownBy(() -> providerAt(NOW.plusSeconds(3)).resolve(source)).hasMessageContaining("POLICY_CONTINUITY_REQUIRED");
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"SOURCE_DISCOVER", "SOURCE_PREVIEW", "SOURCE_INDEX", "SOURCE_REFRESH", "SOURCE_REMOVE"})
    void sourceOperationsCanPrepareTheExactProvisionedCandidateBeforeActivation(Operation operation) {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        Reservation candidate = provisionRelease("candidate-1");
        var source = sourceContext(operation, "source-operation", "candidate-1", "offer-fingerprint", 2);
        sourceEvidence(source, NOW.plusSeconds(3), NOW.plusSeconds(60));

        var resolved = providerAt(NOW.plusSeconds(3)).resolve(source);

        assertThat(resolved.capacityReservationId()).isEqualTo(allocation.id());
        assertThat(resolved.passed()).contains(Prerequisite.STORAGE, Prerequisite.IDENTITY);
        assertThat(store.require(allocation.id())).isEqualTo(candidate);
        assertThat(candidate.state()).isEqualTo(State.PROVISIONING);
        assertThat(store.releaseBinding("candidate-1").state()).isEqualTo("PROVISIONING");
        assertThat(deployments.findById("deployment1").orElseThrow().getActiveVersionId()).isNull();
        assertThat(store.held()).hasSize(1);
    }

    @Test
    void sourceIndexingCanRepairAProvisionedCandidateAfterItsDocumentVerificationFailed() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        Reservation candidate = provisionRelease("candidate-1");
        Reservation failedVerification = store.outcome(candidate.fence(), State.OUTCOME_UNKNOWN,
            "verification-failed", "worker", NOW.plusSeconds(2));
        store.releaseBindingState("candidate-1", "OUTCOME_UNKNOWN");
        var runtime = targetContexts.resolve("deployment1", TARGET, "candidate-1", false);
        runtime.release().setStatus("APPLIED_VERIFICATION_FAILED");
        var source = sourceContext(Operation.SOURCE_INDEX, "source-index", "candidate-1", "offer-fingerprint", 2);
        sourceEvidence(source, NOW.plusSeconds(3), NOW.plusSeconds(60));

        assertThat(providerAt(NOW.plusSeconds(3)).resolve(source).passed()).contains(Prerequisite.STORAGE);
        assertThat(store.require(candidate.id())).isEqualTo(failedVerification);
        assertThat(runtime.release().getStatus()).isEqualTo("APPLIED_VERIFICATION_FAILED");
        assertThat(deployments.findById("deployment1").orElseThrow().getActiveVersionId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "NO_EFFECT", "SUPERSEDED"})
    void unprovisionedOrFencedCandidateCannotObtainSourceAuthority(String bindingState) {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        provisionRelease("candidate-1");
        store.releaseBindingState("candidate-1", bindingState);
        var source = sourceContext(Operation.SOURCE_PREVIEW, null, "candidate-1", "offer-fingerprint", 2);
        assertRejected(source, "ACTIVE_RELEASE_PREREQUISITE_EVIDENCE_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "APPLIED_VERIFICATION_BLOCKED", "QUEUED", "APPLIED_VERIFICATION_FAILED"})
    void provisioningAllocationDoesNotGrantSourceAuthorityForUnsettledOrFailedRuntimeStates(String status) {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        provisionRelease("candidate-1");
        targetContexts.resolve("deployment1", TARGET, "candidate-1", false).release().setStatus(status);
        var source = sourceContext(Operation.SOURCE_PREVIEW, null, "candidate-1", "offer-fingerprint", 2);
        assertRejected(source, "SOURCE_RELEASE_NOT_PROVISIONED_OR_ALLOCATION_UNSETTLED");
    }

    @Test
    void uncertainProvisioningOrChangedCandidateGenerationCannotUseSourceEvidence() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        Reservation candidate = provisionRelease("candidate-1");
        var source = sourceContext(Operation.SOURCE_INDEX, "source-index", "candidate-1", "offer-fingerprint", 2);
        sourceEvidence(source, NOW.plusSeconds(3), NOW.plusSeconds(60));
        var runtime = targetContexts.resolve("deployment1", TARGET, "candidate-1", false);
        runtime.release().setProvisioningStatus("AWAITING_CONFIRMATION");
        assertRejected(source, "SOURCE_RELEASE_NOT_PROVISIONED_OR_ALLOCATION_UNSETTLED");
        runtime.release().setProvisioningStatus("ACTIVE");
        store.outcome(candidate.fence(), State.OUTCOME_UNKNOWN, "uncertain-provider", "worker", NOW.plusSeconds(2));
        store.releaseBindingState("candidate-1", "OUTCOME_UNKNOWN");
        assertRejected(source, "SOURCE_RELEASE_NOT_PROVISIONED_OR_ALLOCATION_UNSETTLED");
        jdbc.update("UPDATE deployment_admission_release_bindings SET generation='foreign-generation' WHERE release_id='candidate-1'");
        assertRejected(source, "ACTIVE_RELEASE_PREREQUISITE_EVIDENCE_REQUIRED");
    }

    @Test
    void sourceAuthorityDoesNotExtendTheProvisioningCandidatesCapacityLifetime() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        Reservation candidate = provisionRelease("candidate-1");
        var source = sourceContext(Operation.SOURCE_PREVIEW, null, "candidate-1", "offer-fingerprint", 2);
        sourceEvidence(source, NOW.plusSeconds(3), NOW.plusSeconds(60));

        assertThatThrownBy(() -> providerAt(candidate.expiresAt().plusSeconds(1)).resolve(source))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        assertThat(store.require(candidate.id())).isEqualTo(candidate);
    }

    @Test
    void freshReleaseScopedSourceProbeReplacesExpiredProvisioningProofWithoutChangingIt() {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        Reservation before = store.require(allocation.id());
        var sourceContext = sourceContext(Operation.SOURCE_PREVIEW, null, "release-1", "offer-fingerprint", 2);
        sourceEvidence(sourceContext, NOW.plusSeconds(80), NOW.plusSeconds(110));

        var resolved = providerAt(NOW.plusSeconds(80)).resolve(sourceContext);

        assertThat(resolved.checkedAt()).isEqualTo(NOW.plusSeconds(80));
        assertThat(resolved.passed()).contains(Prerequisite.STORAGE, Prerequisite.IDENTITY);
        assertThat(before.prerequisiteReadback().validUntil()).isBefore(NOW.plusSeconds(80));
        assertThat(store.require(allocation.id())).isEqualTo(before);
        assertThat(store.held()).hasSize(1);
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test
    void missingExpiredOrTamperedRecurringSourceProofFailsClosed() {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        var source = sourceContext(Operation.SOURCE_PREVIEW, null, "release-1", "offer-fingerprint", 2);
        assertRejected(source, "SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        sourceEvidence(source, NOW.minusSeconds(61), NOW.plusSeconds(30));
        assertRejected(source, "SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        sourceEvidence(source, NOW, NOW.plusSeconds(30));
        jdbc.update("UPDATE deployment_source_prerequisite_evidence SET payload_fingerprint='tampered'");
        assertRejected(source, "SOURCE_PREREQUISITE_RECORD_MISMATCH");
    }

    @Test
    void storageOrCredentialRevisionDriftCannotReuseRecurringSourceProof() {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);
        var source = sourceContext(Operation.SOURCE_PREVIEW, null, "release-1", "offer-fingerprint", 2);
        sourceEvidence(source, NOW, NOW.plusSeconds(30));
        var runtime = targetContexts.resolve("deployment1", TARGET, "release-1", false);
        runtime.release().setDocumentBindingFingerprint("changed-storage-or-credentials");
        assertRejected(source, "SOURCE_PREREQUISITE_REFRESH_REQUIRED");
    }

    @Test
    void sourceEvidenceFailsClosedForSupersededForeignOrDriftedReleaseScope() {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);

        assertRejected(sourceContext(Operation.SOURCE_PREVIEW, null, "foreign-release", "offer-fingerprint", 2),
            "ACTIVE_RELEASE_PREREQUISITE_EVIDENCE_REQUIRED");
        assertRejected(sourceContext(Operation.SOURCE_INDEX, "index-key", "release-1", "changed-offer", 2),
            "ACTIVE_RELEASE_PREREQUISITE_SCOPE_CHANGED");
        assertRejected(sourceContext(Operation.SOURCE_INDEX, "index-key", "release-1", "offer-fingerprint", 3),
            "ACTIVE_RELEASE_PREREQUISITE_SCOPE_CHANGED");

        store.releaseBindingState("release-1", "SUPERSEDED");
        assertRejected(sourceContext(Operation.SOURCE_PREVIEW, null, "release-1", "offer-fingerprint", 2),
            "ACTIVE_RELEASE_PREREQUISITE_EVIDENCE_REQUIRED");
    }

    @Test
    void mutatingSourceOperationsStillRequireAnOperationKey() {
        Reservation allocation = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        activateRelease("release-1", allocation);

        assertRejected(sourceContext(Operation.SOURCE_REFRESH, null, "release-1", "offer-fingerprint", 2),
            "CUSTOMER_PREREQUISITE_CONTEXT_INVALID");
    }

    @Test
    void capacityReservationDoesNotInventUnmappedCustomerPrerequisites() {
        Manifest full = manifest("manifest-partial", "deployment1");
        Manifest partial = new Manifest(full.id(), full.deploymentId(), full.versionId(), full.versionConfigHash(),
            full.targetProfileId(), full.bindingFingerprint(), full.topologyReference(), full.demand(),
            full.prerequisiteReferences(), Map.of(Prerequisite.STORAGE, "storage"));
        store.approveManifest(partial, "operator", NOW);
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", partial.id());
        Reservation reservation = store.reserve(admission, new Reserve("operation", partial.id(), customerContext),
            evidence(admission, NOW), "owner", NOW);

        var resolved = provider.resolve(customerContext);

        assertThat(resolved.capacityReservationId()).isEqualTo(reservation.id());
        assertThat(resolved.passed()).containsExactlyInAnyOrder(Prerequisite.CAPACITY, Prerequisite.STORAGE);
        assertThat(resolved.passed()).doesNotContain(Prerequisite.IDENTITY, Prerequisite.APPROVED_POLICY,
            Prerequisite.IMMUTABLE_ARTIFACTS, Prerequisite.NOTIFICATIONS, Prerequisite.OPERATING_POLICY);
    }

    @Test
    void provisioningReservationRefreshesAndRetainsExactPrerequisiteReadback() {
        Reservation held = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        PrerequisiteReadback refreshed = evidence(admission, NOW.plusSeconds(1));
        Reservation provisioning = store.beginProvisioning(held.fence(), admission, refreshed, "operator", NOW.plusSeconds(1), () -> { });

        assertThat(provisioning.state()).isEqualTo(State.PROVISIONING);
        assertThat(providerAt(NOW.plusSeconds(2)).resolve(customerContext).checkedAt()).isEqualTo(refreshed.observedAt());
    }

    @Test
    void unknownOrNonHeldReservationCannotBecomeCustomerEvidence() throws Exception {
        assertRejected(customerContext, "CUSTOMER_CAPACITY_RESERVATION_AMBIGUOUS_OR_MISSING");

        scopedReserve(new Reserve("operation", "manifest1"));
        assertRejected(customerContext, "CUSTOMER_CAPACITY_RESERVATION_AMBIGUOUS_OR_MISSING");

        database();
        bridgeAfterDatabaseReset();
        Reservation held = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        store.outcome(held.fence(), State.OUTCOME_UNKNOWN, "uncertain-provider-outcome", "operator", NOW.plusSeconds(1));
        assertRejected(customerContext, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
    }

    @Test
    void multipleExactReservationMatchesFailClosedBeforeOtherEvidenceIsRead() {
        Reservation held = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        AdmissionStore ambiguousStore = mock(AdmissionStore.class);
        when(ambiguousStore.customerPrerequisiteReservations(customerContext)).thenReturn(List.of(held, held));
        var ambiguousProvider = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(ambiguousStore, json,
            mapper, deployments, versions, targets, placement, sourceEvidence, targetContexts, Clock.fixed(NOW, ZoneOffset.UTC));
        clearInvocations(deployments, versions, targets, placement);

        assertThatThrownBy(() -> ambiguousProvider.resolve(customerContext))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_AMBIGUOUS_OR_MISSING");

        verifyNoInteractions(deployments, versions, targets, placement);
    }

    @Test
    void activeReservationCannotBeReusedAsPendingCustomerPrerequisiteEvidence() {
        Reservation held = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        Reservation provisioning = store.beginProvisioning(held.fence(), admission, evidence(admission, NOW.plusSeconds(1)),
            "operator", NOW.plusSeconds(1), () -> { });
        Reservation active = store.reconcile(provisioning.fence(),
            readback(provisioning, NOW.plusSeconds(2), false, 0L, 0L), false, "operator", NOW.plusSeconds(2));

        assertThat(active.state()).isEqualTo(State.ACTIVE);
        assertThatThrownBy(() -> providerAt(NOW.plusSeconds(3)).resolve(customerContext))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
    }

    @Test
    void unreviewedReadbackReferenceFailsClosed() {
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        PrerequisiteReadback reviewed = evidence(admission, NOW);
        var references = new java.util.HashSet<>(reviewed.verifiedPrerequisiteReferences());
        references.add("unreviewed-reference");
        PrerequisiteReadback unknown = new PrerequisiteReadback(reviewed.observedAt(), reviewed.validUntil(),
            reviewed.evidenceReference(), reviewed.placement(), reviewed.profileFingerprint(),
            reviewed.manifestFingerprint(), reviewed.bindingFingerprint(), references,
            reviewed.restoreEvidenceReference(), reviewed.privacyEvidenceReference(),
            reviewed.ownerContactEvidenceReference(), reviewed.noticeTransportReference());
        store.reserve(admission, new Reserve("operation", "manifest1", customerContext), unknown, "owner", NOW);

        assertRejected(customerContext, "CUSTOMER_PREREQUISITE_EVIDENCE_UNKNOWN");
    }

    @Test
    void expiryAndChangedContextFingerprintFailClosed() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        assertThatThrownBy(() -> providerAt(NOW.plusSeconds(1_001)).resolve(customerContext))
            .hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");

        var changedOffer = context("customer", "tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation", "changed-offer", limits("measurement-proof"));
        assertRejected(changedOffer, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
        var changedLimits = context("customer", "tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation", "offer-fingerprint", limits("other-measurement"));
        assertRejected(changedLimits, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
    }

    @Test
    void operationCustomerAndTargetMustMatchExactly() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        var differentOperation = context("customer", "tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.ACTIVATE, "operation", "offer-fingerprint", limits("measurement-proof"));
        assertRejected(differentOperation, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
        var foreignCustomer = context("other-customer", "tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation", "offer-fingerprint", limits("measurement-proof"));
        assertRejected(foreignCustomer, "AMBIGUOUS_OR_MISSING");
        var foreignTarget = context("customer", "tenant", "deployment1", "other-target", TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation", "offer-fingerprint", limits("measurement-proof"));
        assertRejected(foreignTarget, "AMBIGUOUS_OR_MISSING");
        var foreignTenant = context("customer", "other-tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation", "offer-fingerprint", limits("measurement-proof"));
        assertRejected(foreignTenant, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
        var foreignDeployment = context("customer", "tenant", "other-deployment", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation", "offer-fingerprint", limits("measurement-proof"));
        assertRejected(foreignDeployment, "AMBIGUOUS_OR_MISSING");
    }

    @Test
    void mismatchedPlacementEvidenceCannotSatisfyTheExactTarget() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        when(placement.requireEvidenceById(targetProfile, TargetIntent.CUSTOMER_STAGING, "placement-proof"))
            .thenReturn(new CustomerPlacementEvidenceEntity("placement-proof", TARGET, TargetIntent.CUSTOMER_STAGING,
                reviewedProfileFingerprint, STAGING.serverUuid(), "other-server", STAGING.destinationUuid(),
                NOW.minusSeconds(1), NOW.plusSeconds(120)));

        assertRejected(customerContext, "CUSTOMER_PLACEMENT_EVIDENCE_MISMATCH");
    }

    @Test
    void changedOrExpiredPlacementSnapshotCannotSatisfyReviewedTargetEvidence() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        when(placement.requireEvidenceById(targetProfile, TargetIntent.CUSTOMER_STAGING, "placement-proof"))
            .thenReturn(new CustomerPlacementEvidenceEntity("placement-proof", TARGET, TargetIntent.CUSTOMER_STAGING,
                "changed-profile-fingerprint", STAGING.serverUuid(), STAGING.ip(), STAGING.destinationUuid(),
                NOW.minusSeconds(1), NOW.plusSeconds(120)));
        assertRejected(customerContext, "CUSTOMER_PLACEMENT_EVIDENCE_MISMATCH");

        when(placement.requireEvidenceById(targetProfile, TargetIntent.CUSTOMER_STAGING, "placement-proof"))
            .thenReturn(new CustomerPlacementEvidenceEntity("placement-proof", TARGET, TargetIntent.CUSTOMER_STAGING,
                reviewedProfileFingerprint, STAGING.serverUuid(), STAGING.ip(), STAGING.destinationUuid(),
                NOW.minusSeconds(120), NOW));
        assertRejected(customerContext, "CUSTOMER_PLACEMENT_EVIDENCE_MISMATCH");
    }

    @Test
    void sameOperationKeyCannotReplayForAnotherOperationOrTargetScope() {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));
        var changedOperation = context("customer", "tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_STAGING,
            Operation.ACTIVATE, "operation", "offer-fingerprint", limits("measurement-proof"));
        assertThatThrownBy(() -> scopedReserve(new Reserve("operation", "manifest1", changedOperation)))
            .hasMessageContaining("OPERATION_KEY_REUSED");

        var changedTarget = context("customer", "tenant", "deployment1", "other-target", TargetIntent.CUSTOMER_STAGING,
            Operation.APPLY, "operation-2", "offer-fingerprint", limits("measurement-proof"));
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        assertThatThrownBy(() -> store.reserve(admission, new Reserve("operation-2", "manifest1", changedTarget),
            evidence(admission, NOW), "owner", NOW)).hasMessageContaining("CUSTOMER_ADMISSION_SCOPE_INVALID");
    }

    @Test
    void reviewedPolicyManifestVersionAndProfileDriftFailClosed() throws Exception {
        scopedReserve(new Reserve("operation", "manifest1", customerContext));

        targetProfile.setProviderConfigJson(mapper.writeValueAsString(Map.of(
            "serverUuid", STAGING.serverUuid(), "destinationUuid", "changed-destination")));
        assertRejected(customerContext, "CUSTOMER_TARGET_PROFILE_CHANGED");
        targetProfile.setProviderConfigJson(mapper.writeValueAsString(Map.of(
            "serverUuid", STAGING.serverUuid(), "destinationUuid", STAGING.destinationUuid())));

        versions.findById("version").orElseThrow().setConfigHash("changed-config");
        assertRejected(customerContext, "CUSTOMER_REVIEWED_VERSION_CHANGED");
        versions.findById("version").orElseThrow().setConfigHash("config-hash");

        Budget budget = reviewedPolicy.fleet();
        FleetPolicy replacement = new FleetPolicy("policy2", budget, reviewedPolicy.targets(), reviewedPolicy.lifecycle(),
            reviewedPolicy.restoreEvidenceReference(), reviewedPolicy.privacyEvidenceReference(), reviewedPolicy.supportReference());
        assertThatThrownBy(() -> store.approvePolicy(replacement, "operator", NOW.plusSeconds(1)))
            .hasMessageContaining("POLICY_CONTINUITY_COVERAGE_REQUIRED");
        assertThat(provider.resolve(customerContext).capacityReservationId()).isNotBlank();
    }

    @Test
    void concurrentIdenticalReservationsProduceOneDurableReplay() throws Exception {
        Reserve command = new Reserve("operation", "manifest1", customerContext);
        CountDownLatch start = new CountDownLatch(1);
        List<Reservation> results = java.util.Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); results.add(scopedReserve(command)); return null; });
            var second = executor.submit(() -> { start.await(); results.add(scopedReserve(command)); return null; });
            start.countDown(); first.get(); second.get();
        }
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Reservation::id).containsOnly(results.getFirst().id());
        assertThat(store.held()).hasSize(1);
        assertThat(provider.resolve(customerContext).capacityReservationId()).isEqualTo(results.getFirst().id());
    }

    @Test
    void customerCommandReadLocksHeldReservationUntilItsTransactionCompletes() throws Exception {
        Reservation held = scopedReserve(new Reserve("operation", "manifest1", customerContext));
        CountDownLatch evidenceResolved = new CountDownLatch(1);
        CountDownLatch allowCustomerCommit = new CountDownLatch(1);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var customerCommand = executor.submit(() -> transaction.execute(status -> {
                var evidence = provider.resolve(customerContext);
                evidenceResolved.countDown();
                try {
                    if (!allowCustomerCommit.await(5, TimeUnit.SECONDS)) throw new AssertionError("customer command latch timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
                return evidence;
            }));
            assertThat(evidenceResolved.await(5, TimeUnit.SECONDS)).isTrue();

            var stateTransition = executor.submit(() -> store.outcome(held.fence(), State.OUTCOME_UNKNOWN,
                "uncertain-provider-outcome", "operator", NOW.plusSeconds(1)));
            assertThatThrownBy(() -> stateTransition.get(150, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

            allowCustomerCommit.countDown();
            assertThat(customerCommand.get(5, TimeUnit.SECONDS).capacityReservationId()).isEqualTo(held.id());
            assertThat(stateTransition.get(5, TimeUnit.SECONDS).state()).isEqualTo(State.OUTCOME_UNKNOWN);
        } finally {
            allowCustomerCommit.countDown();
        }
    }

    private void bridgeAfterDatabaseReset() throws Exception {
        String profileFingerprint = DeploymentTargetProfileIdentity.fingerprint(mapper, targetProfile);
        Budget budget = new Budget(capacity(4), vector(0), vector(0), "measurement-proof", NOW.minusSeconds(1), NOW.plusSeconds(10_000));
        reviewedPolicy = new FleetPolicy("policy1", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, profileFingerprint, budget)),
            policy(4).lifecycle(), "restore-proof", "privacy-proof", "support-owner");
        store.approvePolicy(reviewedPolicy, "operator", NOW); store.approveGrant(customerGrant(4), "operator", NOW);
        store.approveManifest(manifest("manifest1", "deployment1"), "operator", NOW);
        provider = providerAt(NOW);
    }

    private Reservation scopedReserve(Reserve command) {
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        return store.reserve(admission, command, evidence(admission, NOW), "owner", NOW);
    }

    private void useProductionTarget() throws Exception {
        Placement production = new Placement("kvufjk78dj4wyhjgp1mlxecr", "r3thf2xmxcjn1tt2bclabebz", "46.225.162.106");
        targetProfile.setName("Reviewed production");
        targetProfile.setEnvironmentName("customer-production");
        targetProfile.setProviderConfigJson(mapper.writeValueAsString(Map.of(
            "serverUuid", production.serverUuid(), "destinationUuid", production.destinationUuid())));
        reviewedProfileFingerprint = DeploymentTargetProfileIdentity.fingerprint(mapper, targetProfile);
        reviewedPolicy = new FleetPolicy("production-policy", reviewedPolicy.fleet(),
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_PRODUCTION, production,
                reviewedProfileFingerprint, reviewedPolicy.fleet())), reviewedPolicy.lifecycle(),
            reviewedPolicy.restoreEvidenceReference(), reviewedPolicy.privacyEvidenceReference(), reviewedPolicy.supportReference());
        store.approvePolicy(reviewedPolicy, "operator", NOW);
        customerContext = context("customer", "tenant", "deployment1", TARGET, TargetIntent.CUSTOMER_PRODUCTION,
            Operation.PROMOTION_APPLY, "operation", "offer-fingerprint", limits("measurement-proof"));
        when(placement.requireEvidenceById(targetProfile, TargetIntent.CUSTOMER_PRODUCTION, "placement-proof"))
            .thenReturn(new CustomerPlacementEvidenceEntity("placement-proof", TARGET, TargetIntent.CUSTOMER_PRODUCTION,
                reviewedProfileFingerprint, production.serverUuid(), production.ip(), production.destinationUuid(),
                NOW.minusSeconds(1), NOW.plusSeconds(120)));
    }

    private void usePolicyDeadlines(Instant fleetUntil, Instant targetUntil) {
        Budget before = reviewedPolicy.fleet();
        Budget fleet = new Budget(before.measured(), before.baseline(), before.headroom(), before.measurementReference(),
            before.measuredAt(), fleetUntil);
        var target = reviewedPolicy.targets().get(TARGET);
        Budget targetBudget = new Budget(before.measured(), before.baseline(), before.headroom(), before.measurementReference(),
            before.measuredAt(), targetUntil);
        reviewedPolicy = new FleetPolicy("short-policy", fleet,
            Map.of(TARGET, new TargetBudget(TARGET, target.intent(), target.placement(), target.profileFingerprint(), targetBudget)),
            reviewedPolicy.lifecycle(), reviewedPolicy.restoreEvidenceReference(), reviewedPolicy.privacyEvidenceReference(), reviewedPolicy.supportReference());
        store.approvePolicy(reviewedPolicy, "operator", NOW);
    }

    private void activateRelease(String releaseId, Reservation allocation) {
        Reservation provisioning = provisionRelease(releaseId);
        Reservation active = store.reconcile(provisioning.fence(),
            new ResourceReadback(provisioning.id(), provisioning.generation(), provisioning.revision(), provisioning.workerToken(),
                provisioning.manifestFingerprint(), provisioning.bindingFingerprint(), reviewedPolicy.targets().get(TARGET).placement(),
                NOW.plusSeconds(2), "inventory-and-drain-proof", true, true, false, true, true, true, 0, 0L, 0L, 0L, 0L),
            false, "worker", NOW.plusSeconds(2));
        assertThat(active.id()).isEqualTo(allocation.id());
        store.releaseBindingState(releaseId, "ACTIVE");
        targetContexts.resolve("deployment1", TARGET, releaseId, false).release().setStatus("APPLIED_VERIFIED");
    }

    private Reservation provisionRelease(String releaseId) {
        DeploymentEntity deployment = deployments.findById("deployment1").orElseThrow();
        DeploymentVersionEntity version = versions.findById("version").orElseThrow();
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId(releaseId);
        release.setDeploymentId(deployment.getId());
        release.setDeploymentVersionId(version.getId());
        release.setTargetProfileId(TARGET);
        release.setStatus("VERIFYING");
        release.setProvisioningStatus("ACTIVE");
        release.setProviderResourceHandleId("runtime-handle");
        release.setDocumentBindingFingerprint("frozen-storage-binding");
        release.setDocumentBindingSnapshotJson("""
            {"resourceHandleId":"storage-handle","safeMetadata":{"bindingRevision":1,
            "connectorType":"S3_COMPATIBLE_OBJECT_STORAGE"},"secretRevisions":{"ACCESS_KEY":"revision-1"}}
            """);
        when(targetContexts.resolve("deployment1", TARGET, releaseId, false)).thenReturn(
            new DeploymentTargetContextService.Context(deployment, targetProfile, release, version,
                "https://runtime.example", "https://connector.example"));
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
            .executeWithoutResult(status -> store.bindRelease(deployment, version, release));
        ReleaseBinding binding = store.releaseBinding(releaseId);
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        return store.beginReleaseProvisioning(binding, admission,
            evidence(admission, NOW.plusSeconds(1)), "worker", NOW.plusSeconds(1));
    }

    private DeploymentAdmissionCustomerPrerequisiteEvidenceProvider providerAt(Instant instant) {
        return new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(store, json, mapper, deployments, versions,
            targets, placement, sourceEvidence, targetContexts, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void sourceEvidence(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context,
        Instant checkedAt, Instant expiresAt) {
        var snapshot = provider.sourceSnapshot(context);
        sourceEvidence.save(new CustomerSourcePrerequisiteEvidence("source-" + java.util.UUID.randomUUID(), snapshot.scope(),
            "placement-proof", snapshot.passed(), "connector-proof-digest", checkedAt, expiresAt));
    }

    private void freshSourceEvidence(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context,
        Instant checkedAt, Instant expiresAt) {
        Placement physical = reviewedPolicy.targets().get(TARGET).placement();
        when(placement.requireEvidenceById(targetProfile, context.intent(), "placement-proof"))
            .thenReturn(new CustomerPlacementEvidenceEntity("placement-proof", TARGET, context.intent(), reviewedProfileFingerprint,
                physical.serverUuid(), physical.ip(), physical.destinationUuid(), checkedAt, expiresAt));
        sourceEvidence(context, checkedAt, expiresAt);
    }

    private com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context(
        String customer, String tenant, String deployment, String target, TargetIntent intent, Operation operation,
        String operationKey, String offerFingerprint, ReviewedLimits limits) {
        return context(customer, tenant, deployment, target, intent, operation, operationKey,
            offerFingerprint, limits, topology(1));
    }

    private com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context(
        String customer, String tenant, String deployment, String target, TargetIntent intent, Operation operation,
        String operationKey, String offerFingerprint, ReviewedLimits limits, ReviewedTopology topology) {
        return new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            customer, tenant, deployment, null, target, intent, "offer", offerFingerprint, 2,
            "placement-proof", operation, operationKey, limits, topology);
    }

    private com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext sourceContext(
        Operation operation, String operationKey, String releaseId, String offerFingerprint, long entitlementRevision) {
        return new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", "deployment1", releaseId, TARGET, customerContext.intent(),
            "offer", offerFingerprint, entitlementRevision, "placement-proof", operation, operationKey,
            limits("measurement-proof"), topology(1));
    }

    private ReviewedLimits limits(String measurementReference) {
        return new ReviewedLimits(measurementReference, 1, 1, 4, 1, 1, 1, 1, 1, 1, 24, 24, 24);
    }

    private ReviewedTopology topology(long cpuMillis) {
        return new ReviewedTopology(1, cpuMillis, 1, 1, 1, 1, 1, 1);
    }

    private Vector capacity(long units) {
        return with(with(vector(units), Dimension.MEMORY_BYTES, units * 1024L * 1024L),
            Dimension.DISK_BYTES, units * 1024L * 1024L);
    }

    private CustomerGrant customerGrant(long units) {
        Vector capacity = capacity(units);
        return new CustomerGrant("customer", capacity, Map.of(TARGET, capacity), NOW.plusSeconds(5000),
            "verified-owner", "payer", "keep-entitlement");
    }

    @Override
    Manifest manifest(String id, String deployment) {
        Manifest base = super.manifest(id, deployment);
        return new Manifest(base.id(), base.deploymentId(), base.versionId(), base.versionConfigHash(),
            base.targetProfileId(), base.bindingFingerprint(), base.topologyReference(), capacity(1),
            base.prerequisiteReferences(), base.customerPrerequisiteReferences());
    }

    private void assertRejected(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context, String code) {
        assertThatThrownBy(() -> provider.resolve(context)).hasMessageContaining(code);
    }
}

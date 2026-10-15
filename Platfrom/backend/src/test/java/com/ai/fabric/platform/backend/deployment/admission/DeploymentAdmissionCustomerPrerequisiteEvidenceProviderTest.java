package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedLimits;
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

        reviewedProfileFingerprint = json.fingerprint(Map.of("id", TARGET, "provider", DeploymentProviderType.COOLIFY,
            "environment", "customer-staging", "sourceStrategy", "IMAGE_SOURCE", "credentialReference", "",
            "providerConfig", mapper.readTree(targetProfile.getProviderConfigJson()),
            "networkPolicy", mapper.readTree("{}"), "resourceDefaults", mapper.readTree("{}")));
        Budget budget = new Budget(vector(4), vector(0), vector(0), "measurement-proof",
            NOW.minusSeconds(1), NOW.plusSeconds(10_000));
        reviewedPolicy = new FleetPolicy("policy1", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, reviewedProfileFingerprint, budget)),
            policy(4).lifecycle(), "restore-proof", "privacy-proof", "support-owner");
        store.approvePolicy(reviewedPolicy, "operator", NOW);
        store.approveGrant(grant(4), "operator", NOW);
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
        store.approvePolicy(replacement, "operator", NOW.plusSeconds(1));
        assertRejected(customerContext, "CUSTOMER_ADMISSION_FINGERPRINT_CHANGED");
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
        String profileFingerprint = json.fingerprint(Map.of("id", TARGET, "provider", DeploymentProviderType.COOLIFY,
            "environment", "customer-staging", "sourceStrategy", "IMAGE_SOURCE", "credentialReference", "",
            "providerConfig", mapper.readTree(targetProfile.getProviderConfigJson()),
            "networkPolicy", mapper.readTree("{}"), "resourceDefaults", mapper.readTree("{}")));
        Budget budget = new Budget(vector(4), vector(0), vector(0), "measurement-proof", NOW.minusSeconds(1), NOW.plusSeconds(10_000));
        reviewedPolicy = new FleetPolicy("policy1", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, profileFingerprint, budget)),
            policy(4).lifecycle(), "restore-proof", "privacy-proof", "support-owner");
        store.approvePolicy(reviewedPolicy, "operator", NOW); store.approveGrant(grant(4), "operator", NOW);
        store.approveManifest(manifest("manifest1", "deployment1"), "operator", NOW);
        provider = providerAt(NOW);
    }

    private Reservation scopedReserve(Reserve command) {
        AdmissionContext admission = store.context("customer", "tenant", "deployment1", "manifest1");
        return store.reserve(admission, command, evidence(admission, NOW), "owner", NOW);
    }

    private void activateRelease(String releaseId, Reservation allocation) {
        Reservation provisioning = provisionRelease(releaseId);
        Reservation active = store.reconcile(provisioning.fence(),
            readback(provisioning, NOW.plusSeconds(2), false, 0L, 0L), false, "worker", NOW.plusSeconds(2));
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

    private com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context(
        String customer, String tenant, String deployment, String target, TargetIntent intent, Operation operation,
        String operationKey, String offerFingerprint, ReviewedLimits limits) {
        return new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            customer, tenant, deployment, null, target, intent, "offer", offerFingerprint, 2,
            "placement-proof", operation, operationKey, limits);
    }

    private com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext sourceContext(
        Operation operation, String operationKey, String releaseId, String offerFingerprint, long entitlementRevision) {
        return new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", "deployment1", releaseId, TARGET, TargetIntent.CUSTOMER_STAGING,
            "offer", offerFingerprint, entitlementRevision, "placement-proof", operation, operationKey,
            limits("measurement-proof"));
    }

    private ReviewedLimits limits(String measurementReference) {
        return new ReviewedLimits(measurementReference, 1, 1, 4, 1, 1, 1, 1, 1, 1, 24, 24, 24);
    }

    private void assertRejected(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context, String code) {
        assertThatThrownBy(() -> provider.resolve(context)).hasMessageContaining(code);
    }
}

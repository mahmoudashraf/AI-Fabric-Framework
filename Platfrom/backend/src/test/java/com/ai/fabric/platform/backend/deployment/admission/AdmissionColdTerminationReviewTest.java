package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ConsumerExpectation;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ProviderVolumeGeneration;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ReleaseGeneration;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ResourceGeneration;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Snapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionColdTerminationReview.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AdmissionColdTerminationReviewTest {
    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final Placement STAGING = new Placement("zf25hgk9694bt7q0zwb98ado",
        "xjhfu65nacrr30xax5cp0ry7", "46.224.145.148");

    @Test void expiredUsedPilotFreezesLossReviewWithoutDrainOrSettlement() {
        var capture = capture();
        var review = freeze(expected(), capture, acknowledgement(), NOW);
        assertThat(review.expectation()).isEqualTo(expected());
        assertThat(review.frozenAt()).isEqualTo(NOW);
        assertThat(review.semantics()).isEqualTo(Semantics.PHYSICAL_TERMINATION_WITH_LOSS_NOT_DRAIN);
        assertThat(review.accountingDisposition())
            .isEqualTo(AccountingDisposition.PRESERVE_HISTORY_AND_ACTUAL_USAGE_NO_REFUND_OR_BUDGET_RESET);
        assertThat(review.capture().reservation()).isEqualTo(capture.reservation());
        assertThat(review.capture().reservation().expiresAt()).isBefore(NOW);
        assertThat(review.capture().reservation().recoveryUntil()).isAfter(NOW);
        assertThat(review.capture().reservation().consumesCapacity()).isTrue();
        assertThat(review.capture().reservation().expiryState()).isEqualTo(ExpiryState.CLAIMED);
        assertThat(review.capture().reservation().retirementOperationId()).isNull();
        assertThat(review.capture().reservation().retentionSettlement()).isNull();
        assertThat(review.capture().reservation().chargedDemand()).isEqualTo(capture.reservation().chargedDemand());
        assertThat(review.nativeTerminationCandidates()).extracting(ResourceGeneration::resourceKind)
            .containsExactly("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE");
        assertThat(review.preservedExternalResources()).extracting(ResourceGeneration::resourceKind)
            .containsExactly(PreservedExternalBindingKinds.DOCUMENT_STORAGE, PreservedExternalBindingKinds.VECTOR_STORAGE);
        assertThat(review.capture().releaseBindings()).extracting(ReleaseBinding::state)
            .containsExactly("OUTCOME_UNKNOWN", "NO_EFFECT");
        assertThat(review.capture().snapshot().consumers()).containsExactly(new ConsumerExpectation("draft-consumer", 1));
    }

    @Test void elapsedRecoveryStillDoesNotBecomeDrainOrZeroWorkEvidence() {
        var capture = changed(capture(), "reservation", changed(reservation(), "recoveryUntil", NOW.minusSeconds(1)));
        assertThat(freeze(expected(), capture, acknowledgement(), NOW).semantics())
            .isEqualTo(Semantics.PHYSICAL_TERMINATION_WITH_LOSS_NOT_DRAIN);
    }

    @ParameterizedTest @ValueSource(longs = {1801, 31536000})
    void finiteButOverlongReviewCannotAuthorizeTermination(long seconds) {
        rejected(changed(capture(), "validUntil", NOW.plusSeconds(seconds)), "WINDOW_TOO_LONG");
    }

    @Test void thirtyMinuteReviewBoundaryStillRequiresUnexpiredAcknowledgement() {
        var capture = changed(capture(), "validUntil", NOW.plusSeconds(1800));
        var acknowledgement = changed(acknowledgement(), "validUntil", NOW.plusSeconds(1800));
        assertThat(freeze(expected(), capture, acknowledgement, NOW).frozenAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> freeze(expected(), capture, acknowledgement, NOW.plusSeconds(1800)))
            .hasMessageContaining("FINITE_CURRENT_ACKNOWLEDGEMENT_REQUIRED");
    }

    @Test void persistedEnumSetsHaveStableOrderAcrossProcesses() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode tree = mapper.valueToTree(freeze(expected(), capture(), acknowledgement(), NOW).payload());
        var confirmations = new ArrayList<String>();
        tree.path("capture").path("confirmations").forEach(value -> confirmations.add(value.asText()));
        assertThat(confirmations).containsExactly(Arrays.stream(Confirmation.values()).map(Enum::name).toArray(String[]::new));
        var losses = new ArrayList<String>();
        tree.path("acknowledgement").path("acceptedLosses").forEach(value -> losses.add(value.asText()));
        assertThat(losses).containsExactly(Arrays.stream(LossAcknowledgement.values()).map(Enum::name).toArray(String[]::new));
    }

    @ParameterizedTest @EnumSource(State.class)
    void onlySupportedFailedUsedStatesAreEligible(State state) {
        var capture = changed(capture(), "reservation", changed(reservation(), "state", state));
        if (state == State.OUTCOME_UNKNOWN || state == State.CLEANUP_FAILED)
            assertThat(freeze(expected(), capture, acknowledgement(), NOW).capture().reservation().state()).isEqualTo(state);
        else rejected(capture, "FAILED_USED_STAGING_REQUIRED");
    }

    @ParameterizedTest @EnumSource(value = ExpiryState.class, names = "CLAIMED", mode = EnumSource.Mode.EXCLUDE)
    void exactClaimedExpiryIsRequired(ExpiryState state) {
        rejected(changed(capture(), "reservation", changed(reservation(), "expiryState", state)), "EXACT_EXPIRY_CLAIM_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"customerId", "tenantId", "deploymentId", "targetProfileId", "expiryWorkerToken"})
    void expectationMustMatchExactServerScopeAndWorker(String field) {
        assertThatThrownBy(() -> freeze(changed(expected(), field, "foreign"), capture(), acknowledgement(), NOW))
            .hasMessageContaining(field.equals("expiryWorkerToken") ? "EXACT_EXPIRY_CLAIM_REQUIRED" : "SCOPE_CHANGED");
    }

    @Test void fenceIncludesIdGenerationAndRevisionAndNoRetirementMayExist() {
        for (Fence fence : List.of(new Fence("other", 32, "generation"), new Fence("reservation", 31, "generation"),
            new Fence("reservation", 32, "other"), new Fence("reservation", -1, "generation")))
            assertThatThrownBy(() -> freeze(changed(expected(), "fence", fence), capture(), acknowledgement(), NOW))
                .hasMessageContaining("SCOPE_CHANGED");
        rejected(changed(capture(), "reservation", changed(reservation(), "workerToken", null)), "EXACT_EXPIRY_CLAIM_REQUIRED");
        rejected(changed(capture(), "reservation", changed(reservation(), "retirementOperationId", "existing")),
            "EXACT_EXPIRY_CLAIM_REQUIRED");
        rejected(changed(capture(), "reservation", changed(reservation(), "intent", Intent.CUSTOMER_PRODUCTION)),
            "FAILED_USED_STAGING_REQUIRED");
    }

    @ParameterizedTest @EnumSource(Confirmation.class)
    void heldExpiryAndExpiredRepairHistoryNeverSubstituteForServerConfirmations(Confirmation missing) {
        var confirmations = EnumSet.allOf(Confirmation.class);
        confirmations.remove(missing);
        rejected(changed(capture(), "confirmations", confirmations), "SERVER_CONFIRMATIONS_REQUIRED");
    }

    @ParameterizedTest @EnumSource(LossAcknowledgement.class)
    void everyLossAndRecoveryWaiverMustBeExplicit(LossAcknowledgement missing) {
        var losses = EnumSet.allOf(LossAcknowledgement.class);
        losses.remove(missing);
        assertThatThrownBy(() -> freeze(expected(), capture(), changed(acknowledgement(), "acceptedLosses", losses), NOW))
            .hasMessageContaining("EXPLICIT_LOSS_ACKNOWLEDGEMENT_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"reviewReference", "staffActorId", "reason"})
    void attributableStaffAcknowledgementIsRequired(String field) {
        assertThatThrownBy(() -> freeze(expected(), capture(), changed(acknowledgement(), field, " "), NOW))
            .hasMessageContaining("ADMISSION_INPUT_INVALID");
    }

    @Test void acknowledgementMustBeForThisImmutableCapture() {
        assertThatThrownBy(() -> freeze(expected(), capture(), changed(acknowledgement(), "captureReference", "other"), NOW))
            .hasMessageContaining("EXPLICIT_LOSS_ACKNOWLEDGEMENT_REQUIRED");
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidTimes() {
        return Stream.of(
            arguments("reservation", "expiresAt", null), arguments("reservation", "expiresAt", Instant.MAX),
            arguments("reservation", "expiresAt", NOW.plusSeconds(1)),
            arguments("reservation", "expiresAt", NOW.minusSeconds(200000)),
            arguments("reservation", "createdAt", Instant.MIN), arguments("reservation", "updatedAt", NOW.plusSeconds(1)),
            arguments("reservation", "keepUntil", NOW.plusSeconds(1)), arguments("reservation", "drainDeadline", null),
            arguments("reservation", "recoveryUntil", Instant.MAX),
            arguments("reservation", "recoveryUntil", NOW.minusSeconds(200000)),
            arguments("capture", "capturedAt", NOW.plusSeconds(1)), arguments("capture", "validUntil", NOW),
            arguments("capture", "validUntil", Instant.MAX), arguments("ack", "acknowledgedAt", null),
            arguments("ack", "acknowledgedAt", NOW.minusSeconds(1)), arguments("ack", "acknowledgedAt", NOW.plusSeconds(1)),
            arguments("ack", "validUntil", null), arguments("ack", "validUntil", NOW),
            arguments("ack", "validUntil", Instant.MAX), arguments("ack", "validUntil", NOW.plusSeconds(301)));
    }

    @ParameterizedTest @MethodSource("invalidTimes")
    void finiteOriginalExpiryAndNewCaptureAndAcknowledgementBoundsAreRequired(String owner, String field, Instant value) {
        var capture = capture();
        var ack = acknowledgement();
        if (owner.equals("reservation")) capture = changed(capture, "reservation", changed(reservation(), field, value));
        else if (owner.equals("capture")) capture = changed(capture, field, value);
        else ack = changed(ack, field, value);
        var input = capture;
        var acceptance = ack;
        assertThatThrownBy(() -> freeze(expected(), input, acceptance, NOW))
            .hasMessageContaining(owner.equals("reservation") ? "BOUNDED_EXPIRED_RESERVATION_REQUIRED"
                : "FINITE_CURRENT_ACKNOWLEDGEMENT_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"customerId", "tenantId", "deploymentId", "targetProfileId"})
    void snapshotMustMatchExpectedOwnerAndTarget(String field) {
        rejected(changed(capture(), "snapshot", changed(capture().snapshot(), field, "foreign")), "SCOPE_CHANGED");
    }

    @ParameterizedTest @ValueSource(strings = {"reservationId", "generation", "customerId", "tenantId", "deploymentId", "targetProfileId", "versionId", "state"})
    void everyScopedReleaseBindingMustMatch(String field) {
        var bindings = new ArrayList<>(capture().releaseBindings());
        bindings.set(0, changed(bindings.getFirst(), field, "foreign"));
        rejected(changed(capture(), "releaseBindings", bindings), "RELEASE_SCOPE_CHANGED");
    }

    @Test void missingDuplicateAndUnfrozenReleaseInventoriesFailClosed() {
        var capture = capture();
        var snapshot = capture.snapshot();
        rejected(changed(capture, "releaseBindings", List.of(capture.releaseBindings().getFirst())), "RELEASE_SET_CHANGED");
        rejected(changed(capture, "releaseBindings", List.of(capture.releaseBindings().getFirst(), capture.releaseBindings().getFirst())),
            "DUPLICATE_RELEASE_BINDING");
        rejected(changed(capture, "snapshot", changed(snapshot, "releases", List.of(snapshot.releases().getFirst(), snapshot.releases().getFirst()))),
            "DUPLICATE_RELEASE");
        rejected(changed(capture, "snapshot", changed(snapshot, "releases", List.of(changed(snapshot.releases().getFirst(), "generation", "")))),
            "ADMISSION_INPUT_INVALID");
    }

    @ParameterizedTest @ValueSource(strings = {"APPLICATION", "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION", "RUNTIME_POSTGRES_DATABASE"})
    void onlyExactSupportedNativeKindsCanBeCandidates(String kind) {
        var capture = resources(List.of(nativeResource(kind, "owned")));
        assertThat(freeze(expected(), capture, acknowledgement(), NOW).nativeTerminationCandidates()).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"BUSINESS_POSTGRES_DATABASE", "DATABASE", "EXTERNAL_OTHER_STORAGE_BINDING", "application", "APPLICATION "})
    void foreignBusinessAndUnknownKindsAreNeverAuthorized(String kind) {
        rejected(resources(List.of(changed(nativeResource("APPLICATION", "owned"), "resourceKind", kind))),
            kind.endsWith(" ") ? "ADMISSION_INPUT_INVALID" : "NATIVE_FAILED_RESOURCE_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"handleId", "providerResourceUuid", "serverUuid", "projectUuid", "environmentUuid", "destinationUuid", "providerName", "generation", "providerIdentityFingerprint"})
    void nativeIdentitiesCannotBeOmitted(String field) {
        rejected(resources(List.of(changed(nativeResource("APPLICATION", "owned"), field, ""))), "ADMISSION_INPUT_INVALID");
    }

    @ParameterizedTest @ValueSource(strings = {"serverUuid", "projectUuid", "environmentUuid", "destinationUuid", "deploymentMarker"})
    void nativeOwnershipAndPlacementCannotDrift(String field) {
        rejected(resources(List.of(changed(nativeResource("APPLICATION", "owned"), field, "foreign"))), "NATIVE_IDENTITY_CHANGED");
    }

    @Test void nativeResourcesCannotBelongToNoEffectOrUnscopedReleasesOrOtherProviders() {
        var resource = nativeResource("APPLICATION", "owned");
        rejected(resources(List.of(changed(resource, "releaseId", "no-effect-release"))), "NATIVE_FAILED_RESOURCE_REQUIRED");
        rejected(resources(List.of(changed(resource, "releaseId", "foreign"))), "RESOURCE_RELEASE_CHANGED");
        rejected(resources(List.of(changed(resource, "releaseId", null))), "NATIVE_FAILED_RESOURCE_REQUIRED");
        rejected(resources(List.of(changed(resource, "providerType", DeploymentProviderType.RAILWAY_STUB))), "NATIVE_FAILED_RESOURCE_REQUIRED");
        rejected(changed(capture(), "nativePlacement", new Placement("production", "destination", "address")), "PHYSICAL_PLACEMENT_FORBIDDEN");
    }

    @ParameterizedTest @ValueSource(strings = {PreservedExternalBindingKinds.DOCUMENT_STORAGE,
        PreservedExternalBindingKinds.VECTOR_STORAGE, PreservedExternalBindingKinds.AI_STATE_STORAGE})
    void externalSourceVectorAndAiStateHandlesRemainPreservedNeverDeletionCandidates(String kind) {
        var external = externalResource(kind);
        var capture = resources(List.of(nativeResource("APPLICATION", "owned"), external));
        var review = freeze(expected(), capture, acknowledgement(), NOW);
        assertThat(review.preservedExternalResources()).containsExactly(external);
        assertThat(review.nativeTerminationCandidates()).doesNotContain(external);
        rejected(resources(List.of(changed(external, "preserved", false))), "EXTERNAL_PRESERVATION_REQUIRED");
        rejected(resources(List.of(external)), "PROVISIONED_USED_RESOURCE_REQUIRED");
        rejected(resources(List.of(changed(nativeResource("APPLICATION", "owned"), "preserved", true))), "EXTERNAL_PRESERVATION_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"storageUuid", "name", "mountPath", "generation", "type"})
    void databaseVolumeIdentityMustBeImmutableAndComplete(String field) {
        var resource = nativeResource("RUNTIME_POSTGRES_DATABASE", "database");
        rejected(resources(List.of(changed(resource, "volumes", List.of(changed(resource.volumes().getFirst(), field, ""))))),
            field.equals("type") ? "VOLUME_IDENTITY_OR_OWNERSHIP_INVALID" : "ADMISSION_INPUT_INVALID");
    }

    @Test void databaseVolumesAreMandatoryAndCannotBeSharedHostBackedOrPreserved() {
        var resource = nativeResource("RUNTIME_POSTGRES_DATABASE", "database");
        var volume = resource.volumes().getFirst();
        rejected(resources(List.of(changed(resource, "volumes", List.of()))), "DATABASE_VOLUMES_REQUIRED");
        rejected(resources(List.of(changed(resource, "volumes", List.of(changed(volume, "hostPath", "/customer-business"))))),
            "VOLUME_IDENTITY_OR_OWNERSHIP_INVALID");
        rejected(resources(List.of(changed(resource, "volumes", List.of(changed(volume, "preserved", true))))),
            "VOLUME_IDENTITY_OR_OWNERSHIP_INVALID");
        rejected(resources(List.of(resource, changed(nativeResource("RUNTIME_POSTGRES_DATABASE", "other"), "volumes", resource.volumes()))),
            "VOLUME_IDENTITY_OR_OWNERSHIP_INVALID");
        rejected(resources(List.of(resource, changed(resource, "handleId", "alias"))), "NATIVE_IDENTITY_CHANGED");
        rejected(resources(List.of(resource, resource)), "RESOURCE_IDENTITY_INVALID");
    }

    @Test void explicitlyPreservedApplicationHostVolumeStaysPreserved() {
        var retained = new ProviderVolumeGeneration("retained", "persistent", "source", "/source", "/customer/source", "volume-generation", true);
        var resource = changed(nativeResource("APPLICATION", "runtime"), "volumes", List.of(retained));
        assertThat(freeze(expected(), resources(List.of(resource)), acknowledgement(), NOW)
            .nativeTerminationCandidates().getFirst().volumes()).containsExactly(retained);
    }

    @Test void nativeIdentityCannotAliasAPreservedExternalHandle() {
        var nativeResource = nativeResource("RUNTIME_POSTGRES_DATABASE", "database");
        var external = changed(externalResource(PreservedExternalBindingKinds.AI_STATE_STORAGE),
            "providerResourceUuid", nativeResource.providerResourceUuid());
        rejected(resources(List.of(nativeResource, external)), "NATIVE_IDENTITY_CHANGED");
    }

    @Test void incompleteAndNullInventoryIsNotAnEmptyOrUnusedSuccess() {
        var capture = capture();
        rejected(changed(capture, "snapshot", changed(capture.snapshot(), "resources", List.of())), "COMPLETE_INVENTORY_REQUIRED");
        rejected(changed(capture, "snapshot", changed(capture.snapshot(), "resources", null)), "COMPLETE_INVENTORY_REQUIRED");
        rejected(changed(capture, "snapshot", changed(capture.snapshot(), "consumers", null)), "COMPLETE_INVENTORY_REQUIRED");
        rejected(changed(capture, "releaseBindings", null), "COMPLETE_INVENTORY_REQUIRED");
        assertThatThrownBy(() -> resources(Arrays.asList((ResourceGeneration) null))).hasMessageContaining("NULL_INVENTORY_ENTRY");
        assertThatThrownBy(() -> freeze(expected(), capture, null, NOW)).hasMessageContaining("INPUT_REQUIRED");
    }

    @Test void frozenReviewDefensivelyCopiesNestedCollectionsIncludingReservationEvidence() {
        var capture = capture();
        var sourceReferences = new HashSet<>(Set.of("original-proof"));
        var readback = new PrerequisiteReadback(NOW, NOW.plusSeconds(300), "proof", STAGING, "profile", "manifest", "binding",
            sourceReferences, "restore", "privacy", "owner", "notice");
        var resources = new ArrayList<>(capture.snapshot().resources());
        var releases = new ArrayList<>(capture.snapshot().releases());
        var consumers = new ArrayList<>(capture.snapshot().consumers());
        var bindings = new ArrayList<>(capture.releaseBindings());
        var confirmations = EnumSet.allOf(Confirmation.class);
        var losses = EnumSet.allOf(LossAcknowledgement.class);
        var rawSnapshot = new Snapshot("customer", "tenant", "deployment", "staging", "target-generation", 7, releases, resources, consumers);
        var rawCapture = new ServerCapture("capture", NOW, NOW.plusSeconds(300), changed(reservation(), "prerequisiteReadback", readback),
            rawSnapshot, bindings, STAGING, "project", "environment", confirmations);
        var review = freeze(expected(), rawCapture, changed(acknowledgement(), "acceptedLosses", losses), NOW);
        resources.clear(); releases.clear(); consumers.clear(); bindings.clear(); confirmations.clear(); losses.clear(); sourceReferences.clear();
        assertThat(review.nativeTerminationCandidates()).hasSize(3);
        assertThat(review.capture().snapshot().releases()).hasSize(2);
        assertThat(review.capture().snapshot().consumers()).hasSize(1);
        assertThat(review.capture().releaseBindings()).hasSize(2);
        assertThat(review.capture().confirmations()).hasSize(Confirmation.values().length);
        assertThat(review.acknowledgement().acceptedLosses()).hasSize(LossAcknowledgement.values().length);
        assertThat(review.capture().reservation().prerequisiteReadback().verifiedPrerequisiteReferences()).containsExactly("original-proof");
        assertThatThrownBy(() -> review.capture().snapshot().resources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> review.nativeTerminationCandidates().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> review.preservedExternalResources().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void explicitRecordPayloadSerializesThroughExistingAdmissionJsonWithoutCreatingAuthority() {
        var review = freeze(expected(), capture(), acknowledgement(), NOW);
        var json = new AdmissionJson(new ObjectMapper().registerModule(new JavaTimeModule()));
        String serialized = json.write(review.payload());
        JsonNode tree = json.read(serialized, JsonNode.class);
        assertThat(tree.path("expectation").path("fence").path("revision").asLong()).isEqualTo(32);
        assertThat(tree.path("capture").path("snapshot").path("resources").size()).isEqualTo(5);
        assertThat(tree.path("acknowledgement").path("staffActorId").asText()).isEqualTo("staff");
        assertThat(tree.path("semantics").asText()).isEqualTo("PHYSICAL_TERMINATION_WITH_LOSS_NOT_DRAIN");
        assertThat(tree.path("accountingDisposition").asText()).contains("NO_REFUND_OR_BUDGET_RESET");
        assertThat(tree.has("drainComplete")).isFalse();
        assertThat(json.read(serialized, Payload.class)).isEqualTo(review.payload());
        assertThatThrownBy(() -> json.read(serialized, AdmissionColdTerminationReview.class))
            .hasMessageContaining("ADMISSION_RECORD_INVALID");
    }

    private static Expectation expected() {
        return new Expectation(new Fence("reservation", 32, "generation"), "customer", "tenant", "deployment", "staging", "expiry-worker");
    }

    private static Reservation reservation() {
        return new Reservation("reservation", "customer", "tenant", "deployment", "staging", Intent.CUSTOMER_STAGING,
            "authorization", "request", "generation", 32, State.OUTCOME_UNKNOWN, new Vector(Map.of(Dimension.DISK_BYTES, 50L)),
            "policy", "manifest", "manifest-fingerprint", "binding-fingerprint", NOW.minusSeconds(172800), NOW.minusSeconds(80000),
            NOW.minusSeconds(172800), NOW.minusSeconds(86400), 0, null, null, ExpiryState.CLAIMED, "notice-cycle", null, 1,
            "expiry-worker", NOW.minusSeconds(79000), NOW.plusSeconds(28800), null, "failure-evidence", "FAILED",
            "customer-context", null, null);
    }

    private static ServerCapture capture() {
        var snapshot = new Snapshot("customer", "tenant", "deployment", "staging", "target-generation", 7,
            List.of(new ReleaseGeneration("used-release", "version", "used-generation"),
                new ReleaseGeneration("no-effect-release", "version", "no-effect-generation")),
            List.of(nativeResource("APPLICATION", "runtime"), nativeResource("CONNECTOR_APPLICATION", "connector"),
                nativeResource("RUNTIME_POSTGRES_DATABASE", "database"), externalResource(PreservedExternalBindingKinds.DOCUMENT_STORAGE),
                externalResource(PreservedExternalBindingKinds.VECTOR_STORAGE)), List.of(new ConsumerExpectation("draft-consumer", 1)));
        return new ServerCapture("capture", NOW, NOW.plusSeconds(300), reservation(), snapshot,
            List.of(binding("used-release", "OUTCOME_UNKNOWN"), binding("no-effect-release", "NO_EFFECT")),
            STAGING, "project", "environment", EnumSet.allOf(Confirmation.class));
    }

    private static ReleaseBinding binding(String release, String state) {
        return new ReleaseBinding(release, "reservation", "generation", "authorization", "version", "config-hash",
            "customer", "tenant", "deployment", "staging", state);
    }

    private static Acknowledgement acknowledgement() {
        return new Acknowledgement("review", "capture", "staff", "Terminate failed pilot with acknowledged loss", NOW,
            NOW.plusSeconds(180), EnumSet.allOf(LossAcknowledgement.class));
    }

    private static ResourceGeneration nativeResource(String kind, String id) {
        boolean database = kind.equals("RUNTIME_POSTGRES_DATABASE");
        var volumes = database ? List.of(new ProviderVolumeGeneration(id + "-storage", "persistent", id + "-data",
            "/var/lib/postgresql/data", null, "volume-generation", false)) : List.<ProviderVolumeGeneration>of();
        return new ResourceGeneration(id, "used-release", DeploymentProviderType.COOLIFY, kind, id + "-uuid", STAGING.serverUuid(),
            "project", "environment", STAGING.destinationUuid(), id,
            (database ? "Durable runtime chat database for AI Fabric deployment " : "Managed by AI Fabric deployment ") + "deployment",
            id + "-generation", id + "-native-identity", volumes, false);
    }

    private static ResourceGeneration externalResource(String kind) {
        return new ResourceGeneration(kind, null, DeploymentProviderType.COOLIFY, kind, kind + "-external", null, "retained-generation", true);
    }

    private static ServerCapture resources(List<ResourceGeneration> resources) {
        return changed(capture(), "snapshot", changed(capture().snapshot(), "resources", resources));
    }

    private static void rejected(ServerCapture capture, String code) {
        assertThatThrownBy(() -> freeze(expected(), capture, acknowledgement(), NOW)).hasMessageContaining(code);
    }

    /** Vary one immutable record component without duplicating its large canonical constructor. */
    @SuppressWarnings("unchecked")
    private static <T extends Record> T changed(T original, String field, Object replacement) {
        var components = original.getClass().getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = new Object[components.length];
        boolean found = false;
        try {
            for (int i = 0; i < components.length; i++) {
                boolean match = components[i].getName().equals(field);
                found |= match;
                values[i] = match ? replacement : components[i].getAccessor().invoke(original);
            }
            if (!found) throw new AssertionError("Unknown record component: " + field);
            return (T) original.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError(ex.getCause());
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
}

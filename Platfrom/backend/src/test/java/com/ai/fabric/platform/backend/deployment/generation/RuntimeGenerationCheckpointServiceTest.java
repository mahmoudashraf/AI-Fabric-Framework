package com.ai.fabric.platform.backend.deployment.generation;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.Checkpoint;
import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.Phase;
import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeGenerationCheckpointServiceTest {
    private static final Instant COLD_TIME = Instant.parse("2026-01-01T00:00:02Z");
    private static final String NEXT_TOPOLOGY = "c".repeat(64);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentReleaseEntity release = release();
    private RuntimeGenerationCheckpointService service;

    @BeforeEach
    void setUp() {
        when(releases.findById("release-new")).thenReturn(Optional.of(release));
        when(releases.findByIdForUpdate("release-new")).thenReturn(Optional.of(release));
        when(releases.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new RuntimeGenerationCheckpointService(releases, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void persistsFencedStopAndAdoptedPhasesWithExactReplay() {
        var fenced = service.recordFenced("release-new", prior(), receipt(), "c".repeat(64));
        assertThat(fenced.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.FENCED);
        assertThat(service.read("release-new")).contains(fenced);
        assertThat(service.recordFenced("release-new", prior(), receipt(), "c".repeat(64))).isEqualTo(fenced);

        var stopped = service.recordProviderStop("release-new", service.fingerprint(fenced), "coolify-stop:runtime-1:proof");
        assertThat(stopped.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.STOP_CONFIRMED);
        assertThat(stopped.providerStopEvidenceReference()).isEqualTo("coolify-stop:runtime-1:proof");

        var refreshed = service.refreshProviderStop("release-new", service.fingerprint(stopped),
            "coolify-stop:runtime-1:fresh-proof");
        assertThat(refreshed.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.STOP_CONFIRMED);
        assertThat(refreshed.providerStopEvidenceReference()).isEqualTo("coolify-stop:runtime-1:fresh-proof");

        Instant adoptedAt = Instant.now().minusSeconds(1);
        var adopted = service.recordAdopted("release-new", service.fingerprint(refreshed), adoptedAt);
        assertThat(adopted.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.ADOPTED);
        assertThat(service.read("release-new")).contains(adopted);
    }

    @Test
    void rejectsCheckpointDriftAndOutOfOrderMutation() {
        var fenced = service.recordFenced("release-new", prior(), receipt(), "c".repeat(64));
        assertThatThrownBy(() -> service.recordAdopted("release-new", service.fingerprint(fenced), Instant.now()))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_STALE");
        assertThatThrownBy(() -> service.refreshProviderStop("release-new", service.fingerprint(fenced), "proof"))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_STALE");

        release.setRuntimeGenerationTransitionFingerprint("0".repeat(64));
        assertThatThrownBy(() -> service.read("release-new"))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_DRIFT");
    }

    @Test
    void rejectsCrossReleaseReceiptAndChangedInitialCheckpoint() {
        var foreign = new DeploymentRuntimeGenerationService.DrainReceipt(
            new Target("dep-1", "staging", "release-old"),
            new Target("dep-1", "staging", "release-other"),
            "tenant-1", "replace:release-new", "a".repeat(64), 3,
            "b".repeat(64), "runtime-evidence", null
        );
        assertThatThrownBy(() -> service.recordFenced("release-new", prior(), foreign, "c".repeat(64)))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");

        service.recordFenced("release-new", prior(), receipt(), "c".repeat(64));
        assertThatThrownBy(() -> service.recordFenced("release-new",
            new RuntimeGenerationCheckpointService.PriorRuntime("release-old", "runtime-2", "https://runtime.example"),
            receipt(), "c".repeat(64)))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_ALREADY_EXISTS");
    }

    @ParameterizedTest
    @EnumSource(value = Phase.class, names = {"FENCED", "STOP_CONFIRMED", "ADOPTED"})
    void preservesOriginalSixFieldJsonAndFingerprints(Phase phase) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        String stop = phase == Phase.FENCED ? null : "coolify-stop:runtime-1:proof";
        Instant adopted = phase == Phase.ADOPTED ? COLD_TIME : null;
        var legacy = new LegacyCheckpoint(phase, prior(), receipt(), NEXT_TOPOLOGY, stop, adopted);
        String legacyJson = mapper.writeValueAsString(legacy);
        var checkpoint = new Checkpoint(phase, prior(), receipt(), NEXT_TOPOLOGY, stop, adopted);
        assertThat(mapper.writeValueAsString(checkpoint)).isEqualTo(legacyJson)
            .doesNotContain("coldMaintenanceReceipt");
        assertThat(service.fingerprint(checkpoint)).isEqualTo(digest(legacyJson));
        if (phase == Phase.FENCED) {
            assertThat(service.fingerprint(checkpoint))
                .isEqualTo("849a59316113770bd48d2b3f012732c1e103542dd1c6fd573604158fb53ca3f3");
        }
        store(phase, legacyJson);
        assertThat(service.read("release-new")).contains(checkpoint);
        assertThat(mapper.readValue(legacyJson, Checkpoint.class).coldMaintenanceReceipt()).isNull();
    }

    @Test
    void recordsPhysicalColdAdoptionWithOldScopedInventoryAndExactReplayOnly() {
        var receipt = coldReceipt();
        var checkpoint = service.recordColdAdopted("release-new", prior(), receipt, NEXT_TOPOLOGY);
        assertThat(checkpoint.phase()).isEqualTo(Phase.COLD_ADOPTED);
        assertThat(checkpoint.drainReceipt()).isNull();
        assertThat(checkpoint.coldMaintenanceReceipt()).isEqualTo(receipt);
        assertThat(checkpoint.nextTopologyFingerprint()).isEqualTo(NEXT_TOPOLOGY);
        assertThat(checkpoint.providerStopEvidenceReference()).isEqualTo(receipt.transition().providerStopEvidenceReference());
        assertThat(checkpoint.adoptedAt()).isEqualTo(COLD_TIME);
        assertThat(checkpoint.coldMaintenanceReceipt().observedInventory().inventoryComplete()).isFalse();
        assertThat(checkpoint.coldMaintenanceReceipt().observedInventory().workerFencingComplete()).isFalse();
        assertThat(checkpoint.coldMaintenanceReceipt().observedInventory().target().target().releaseId())
            .isEqualTo("release-old");
        assertThat(service.read("release-new")).contains(checkpoint);
        assertThat(service.recordColdAdopted("release-new", prior(), receipt)).isEqualTo(checkpoint);
        verify(releases, times(1)).saveAndFlush(release);

        var changedApproval = new ColdMaintenanceTransitionResult(receipt.transition(), receipt.observedInventory(),
            receipt.evidenceKind(), "another-reviewed-approval", true);
        assertThatThrownBy(() -> service.recordColdAdopted("release-new", prior(), changedApproval, NEXT_TOPOLOGY))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_ALREADY_EXISTS");
        assertThatThrownBy(() -> service.recordProviderStop("release-new", service.fingerprint(checkpoint), "another-stop"))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_STALE");
        verify(releases, times(1)).saveAndFlush(release);
    }

    @Test
    void refusesToOverwriteAnExistingDrainCheckpointWithColdAdoption() {
        var original = service.recordFenced("release-new", prior(), receipt(), NEXT_TOPOLOGY);
        assertThatThrownBy(() -> service.recordColdAdopted("release-new", prior(), coldReceipt(), NEXT_TOPOLOGY))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_ALREADY_EXISTS");
        assertThat(service.read("release-new")).contains(original);
        verify(releases, times(1)).saveAndFlush(release);
    }

    @Test
    void acceptsTheInitialZeroEpochOnlyWithAnExactIncrement() {
        var original = coldReceipt();
        var previous = readback(original.transition().previous(), original.transition().previous().target(),
            "tenant-1", State.DRAINING, 0, false, false);
        var current = readback(original.transition().current(), original.transition().current().target(),
            "tenant-1", State.ACTIVE, 1, false, false);
        var receipt = inventory(transition(original, previous, current, COLD_TIME, "coolify-stop:runtime-1:proof"),
            previous, NEXT_TOPOLOGY);
        assertThat(service.recordColdAdopted("release-new", prior(), receipt).coldMaintenanceReceipt())
            .isEqualTo(receipt);
    }

    @ParameterizedTest
    @MethodSource("storedAdoptionTimes")
    void acceptsOnlyExactOrPostgresMicrosecondAdoptionTimes(Instant transitionAt, Instant storedAt) {
        var receipt = currentUpdatedAt(transitionAt, storedAt);
        var checkpoint = service.recordColdAdopted("release-new", prior(), receipt, NEXT_TOPOLOGY);
        assertThat(checkpoint.adoptedAt()).isEqualTo(transitionAt);
        assertThat(checkpoint.coldMaintenanceReceipt().transition().current().updatedAt()).isEqualTo(storedAt);
        assertThat(service.read("release-new")).contains(checkpoint);
    }

    @ParameterizedTest
    @MethodSource("invalidStoredAdoptionTimes")
    void rejectsMissingOrNonCanonicalStoredAdoptionTimes(Instant storedAt) {
        var receipt = currentUpdatedAt(COLD_TIME.plusNanos(123456789), storedAt);
        assertThatThrownBy(() -> service.recordColdAdopted("release-new", prior(), receipt, NEXT_TOPOLOGY))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        verify(releases, never()).saveAndFlush(any());
    }

    @Test
    void refusesColdCheckpointHashAndOwningReleaseScopeDrift() {
        var checkpoint = service.recordColdAdopted("release-new", prior(), coldReceipt());
        release.setDeploymentId("dep-other");
        assertThatThrownBy(() -> service.read("release-new")).hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        assertThatThrownBy(() -> service.recordColdAdopted("release-new", prior(), coldReceipt()))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        release.setDeploymentId("dep-1");
        release.setTargetProfileId("production");
        assertThatThrownBy(() -> service.read("release-new")).hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        release.setTargetProfileId("staging");
        release.setRuntimeGenerationTransitionFingerprint("0".repeat(64));
        assertThatThrownBy(() -> service.read("release-new")).hasMessage("RUNTIME_GENERATION_CHECKPOINT_DRIFT");
        assertThat(checkpoint.drainReceipt()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidColdReceipts")
    void rejectsInvalidColdReceiptsWithoutWriting(String description, ColdMaintenanceTransitionResult receipt) {
        assertThatThrownBy(() -> service.recordColdAdopted("release-new", prior(), receipt, NEXT_TOPOLOGY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageStartingWith("RUNTIME_GENERATION_");
        verify(releases, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExplicitTopologyMismatchAndMalformedTopology() {
        assertThatThrownBy(() -> service.recordColdAdopted("release-new", prior(), coldReceipt(), "d".repeat(64)))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        assertThatThrownBy(() -> service.recordColdAdopted("release-new", prior(), coldReceipt(), "not-a-fingerprint"))
            .hasMessage("RUNTIME_GENERATION_FINGERPRINT_INVALID");
        verify(releases, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(Phase.class)
    void rejectsMixedColdAndDrainReceiptShapesEvenWithAValidStoredHash(Phase phase) throws Exception {
        var checkpoint = new Checkpoint(phase, prior(), receipt(), NEXT_TOPOLOGY,
            "coolify-stop:runtime-1:proof", COLD_TIME, coldReceipt());
        store(phase, new ObjectMapper().findAndRegisterModules().writeValueAsString(checkpoint));
        assertThatThrownBy(() -> service.read("release-new")).hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
    }

    @Test
    void rejectsStoredColdStopOrAdoptionTimestampMismatchEvenWithValidHashes() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var receipt = coldReceipt();
        store(Phase.COLD_ADOPTED, mapper.writeValueAsString(new Checkpoint(Phase.COLD_ADOPTED, prior(), null,
            NEXT_TOPOLOGY, "different-provider-stop", COLD_TIME, receipt)));
        assertThatThrownBy(() -> service.read("release-new")).hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        store(Phase.COLD_ADOPTED, mapper.writeValueAsString(new Checkpoint(Phase.COLD_ADOPTED, prior(), null,
            NEXT_TOPOLOGY, receipt.transition().providerStopEvidenceReference(), COLD_TIME.plusSeconds(1), receipt)));
        assertThatThrownBy(() -> service.read("release-new")).hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");
    }

    private static Stream<Arguments> invalidColdReceipts() {
        var receipt = coldReceipt();
        var previous = receipt.transition().previous();
        var current = receipt.transition().current();
        return Stream.of(
            Arguments.of("missing receipt", null),
            Arguments.of("missing transition", new ColdMaintenanceTransitionResult(null, receipt.observedInventory(),
                receipt.evidenceKind(), receipt.approvalReference(), true)),
            Arguments.of("missing inventory", new ColdMaintenanceTransitionResult(receipt.transition(), null,
                receipt.evidenceKind(), receipt.approvalReference(), true)),
            Arguments.of("wrong evidence kind", new ColdMaintenanceTransitionResult(receipt.transition(), receipt.observedInventory(),
                "DRAIN_RECEIPT", receipt.approvalReference(), true)),
            Arguments.of("unacknowledged loss", new ColdMaintenanceTransitionResult(receipt.transition(), receipt.observedInventory(),
                receipt.evidenceKind(), receipt.approvalReference(), false)),
            Arguments.of("missing approval", new ColdMaintenanceTransitionResult(receipt.transition(), receipt.observedInventory(),
                receipt.evidenceKind(), " ", true)),
            Arguments.of("previous active", transition(receipt, readback(previous, previous.target(), previous.tenantId(),
                State.ACTIVE, 3, false, false), current, COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("previous revoked", transition(receipt, readback(previous, previous.target(), previous.tenantId(),
                State.DRAINING, 3, true, false), current, COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("previous reclaiming", transition(receipt, readback(previous, previous.target(), previous.tenantId(),
                State.DRAINING, 3, false, true), current, COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("current draining", transition(receipt, previous, readback(current, current.target(), current.tenantId(),
                State.DRAINING, 4, false, false), COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("current revoked", transition(receipt, previous, readback(current, current.target(), current.tenantId(),
                State.ACTIVE, 4, true, false), COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("foreign deployment", transition(receipt, previous, readback(current,
                new Target("dep-other", "staging", "release-new"), current.tenantId(), State.ACTIVE, 4, false, false),
                COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("foreign target", transition(receipt, previous, readback(current,
                new Target("dep-1", "production", "release-new"), current.tenantId(), State.ACTIVE, 4, false, false),
                COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("foreign tenant", transition(receipt, previous, readback(current, current.target(), "tenant-other",
                State.ACTIVE, 4, false, false), COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("wrong next release", transition(receipt, previous, readback(current,
                new Target("dep-1", "staging", "release-other"), current.tenantId(), State.ACTIVE, 4, false, false),
                COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("same release", transition(receipt, previous, readback(current, previous.target(), current.tenantId(),
                State.ACTIVE, 4, false, false), COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("wrong prior release", transition(receipt, readback(previous,
                new Target("dep-1", "staging", "release-other"), previous.tenantId(), State.DRAINING, 3, false, false),
                current, COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("skipped epoch", transition(receipt, previous, readback(current, current.target(), current.tenantId(),
                State.ACTIVE, 5, false, false), COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("negative epoch", transition(receipt, readback(previous, previous.target(), previous.tenantId(),
                State.DRAINING, -1, false, false), current, COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("overflow epoch", transition(receipt, readback(previous, previous.target(), previous.tenantId(),
                State.DRAINING, Long.MAX_VALUE, false, false), readback(current, current.target(), current.tenantId(),
                State.ACTIVE, Long.MIN_VALUE, false, false), COLD_TIME, "coolify-stop:runtime-1:proof")),
            Arguments.of("missing stop evidence", transition(receipt, previous, current, COLD_TIME, null)),
            Arguments.of("missing transition time", transition(receipt, previous, current, null, "coolify-stop:runtime-1:proof")),
            Arguments.of("future transition time", transition(receipt, previous, current, Instant.now().plusSeconds(60),
                "coolify-stop:runtime-1:proof")),
            Arguments.of("transition before inventory", transition(receipt, previous, current, COLD_TIME.minusSeconds(2),
                "coolify-stop:runtime-1:proof")),
            Arguments.of("inventory wrong scope", inventory(receipt, current, NEXT_TOPOLOGY)),
            Arguments.of("inventory wrong topology", inventory(receipt, previous, "d".repeat(64)))
        );
    }

    private static ColdMaintenanceTransitionResult coldReceipt() {
        var previous = new Readback(new Target("dep-1", "staging", "release-old"), "tenant-1", State.DRAINING, 3,
            "replace:release-new", "a".repeat(64), COLD_TIME.plusSeconds(60), false, false, COLD_TIME.minusSeconds(2));
        var current = new Readback(new Target("dep-1", "staging", "release-new"), "tenant-1", State.ACTIVE, 4,
            null, null, null, false, false, COLD_TIME);
        var zero = new Metric(0L, true, List.of(new Observation("store", 0L, true, List.of())));
        var background = new Metric(0L, false, List.of(new Observation("ai_specialist_execution", 0L, false,
            List.of("DRAIN_PROVIDER_UNSUPPORTED:ai_specialist_execution"))));
        var observed = new Snapshot("snapshot-cold", "cold-inventory-reference", previous, COLD_TIME.minusSeconds(1),
            zero, zero, zero, background, 0, false, false, false, false, false, NEXT_TOPOLOGY, List.of(),
            List.of("DRAIN_PROVIDER_UNSUPPORTED:ai_specialist_execution",
                "FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED"));
        return new ColdMaintenanceTransitionResult(new GenerationTransitionResult(previous, current,
            "coolify-stop:runtime-1:proof", COLD_TIME, false), observed,
            "PHYSICAL_STOP_COLD_MAINTENANCE_V1", "reviewed-physical-stop-approval", true);
    }

    private static Stream<Arguments> storedAdoptionTimes() {
        return Stream.of(
            Arguments.of(COLD_TIME.plusNanos(123456789), COLD_TIME.plusNanos(123456789)),
            Arguments.of(COLD_TIME.plusNanos(123456789), COLD_TIME.plusNanos(123456000)),
            Arguments.of(COLD_TIME.plusNanos(123456789), COLD_TIME.plusNanos(123457000)),
            Arguments.of(COLD_TIME.plusNanos(123456500), COLD_TIME.plusNanos(123457000)),
            Arguments.of(COLD_TIME.plusNanos(123456499), COLD_TIME.plusNanos(123456000)),
            Arguments.of(COLD_TIME.plusNanos(999999789), COLD_TIME.plusSeconds(1))
        );
    }

    private static Stream<Arguments> invalidStoredAdoptionTimes() {
        return Stream.of(
            Arguments.of((Instant) null),
            Arguments.of(COLD_TIME.plusNanos(123456790)),
            Arguments.of(COLD_TIME.plusNanos(123455000)),
            Arguments.of(COLD_TIME.plusNanos(123458000))
        );
    }

    private static ColdMaintenanceTransitionResult currentUpdatedAt(Instant transitionAt, Instant storedAt) {
        var original = coldReceipt();
        var current = original.transition().current();
        var updated = new Readback(current.target(), current.tenantId(), current.state(), current.fenceEpoch(),
            current.operationId(), current.requestFingerprint(), current.drainDeadline(), current.authorityRevoked(),
            current.reclaimStarted(), storedAt);
        return transition(original, original.transition().previous(), updated, transitionAt,
            original.transition().providerStopEvidenceReference());
    }

    private static ColdMaintenanceTransitionResult transition(ColdMaintenanceTransitionResult receipt,
                                                               Readback previous, Readback current,
                                                               Instant time, String stop) {
        return new ColdMaintenanceTransitionResult(new GenerationTransitionResult(previous, current, stop, time, false),
            receipt.observedInventory(), receipt.evidenceKind(), receipt.approvalReference(), receipt.inMemoryStateLossAcknowledged());
    }

    private static Readback readback(Readback original, Target target, String tenant, State state,
                                      long epoch, boolean revoked, boolean reclaiming) {
        return new Readback(target, tenant, state, epoch, original.operationId(), original.requestFingerprint(),
            original.drainDeadline(), revoked, reclaiming, original.updatedAt());
    }

    private static ColdMaintenanceTransitionResult inventory(ColdMaintenanceTransitionResult receipt,
                                                              Readback target, String topology) {
        var original = receipt.observedInventory();
        var observed = new Snapshot(original.snapshotId(), original.evidenceReference(), target, original.observedAt(),
            original.openConversations(), original.pendingConfirmations(), original.humanWork(), original.backgroundWork(),
            original.inFlightRequests(), original.inventoryComplete(), original.workerFencingComplete(),
            original.localDrainComplete(), original.reclaimable(), original.deadlineElapsed(), topology,
            original.remoteObligations(), original.blockers());
        return new ColdMaintenanceTransitionResult(receipt.transition(), observed, receipt.evidenceKind(), receipt.approvalReference(), true);
    }

    private void store(Phase phase, String json) throws Exception {
        release.setRuntimeGenerationTransitionState(phase.name());
        release.setRuntimeGenerationTransitionJson(json);
        release.setRuntimeGenerationTransitionFingerprint(digest(json));
    }

    private String digest(String json) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
    }

    private record LegacyCheckpoint(Phase phase, RuntimeGenerationCheckpointService.PriorRuntime priorRuntime,
                                    DeploymentRuntimeGenerationService.DrainReceipt drainReceipt,
                                    String nextTopologyFingerprint, String providerStopEvidenceReference, Instant adoptedAt) { }

    private RuntimeGenerationCheckpointService.PriorRuntime prior() {
        return new RuntimeGenerationCheckpointService.PriorRuntime(
            "release-old", "runtime-1", "https://runtime.example"
        );
    }

    private DeploymentRuntimeGenerationService.DrainReceipt receipt() {
        return new DeploymentRuntimeGenerationService.DrainReceipt(
            new Target("dep-1", "staging", "release-old"),
            new Target("dep-1", "staging", "release-new"),
            "tenant-1", "replace:release-new", "a".repeat(64), 3,
            "b".repeat(64), "runtime-evidence", null
        );
    }

    private DeploymentReleaseEntity release() {
        var value = new DeploymentReleaseEntity();
        value.setId("release-new");
        value.setDeploymentId("dep-1");
        value.setDeploymentVersionId("version-new");
        value.setTargetProfileId("staging");
        value.setUpdatedAt(Instant.parse("2026-10-14T00:00:00Z"));
        return value;
    }
}

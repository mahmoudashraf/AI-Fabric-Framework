package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter.NativeLifecycleAdapter;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter.NativeLifecycleAdapter.NativeSnapshot;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter.NativeLifecycleAdapter.Observation;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.runtimeadapter.RuntimeLifecycleGateway;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseVerificationService;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeLifecycleEffectsTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final NativeLifecycleAdapter nativeAdapter = mock(NativeLifecycleAdapter.class);
    private final RuntimeLifecycleGateway runtime = mock(RuntimeLifecycleGateway.class);
    private final LifecycleCommandStore store = mock(LifecycleCommandStore.class);
    private final DeploymentVerificationRunRepository runs = mock(DeploymentVerificationRunRepository.class);
    private final DeploymentReleaseVerificationService verification = mock(DeploymentReleaseVerificationService.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final NativeLifecycleEffects effects = new NativeLifecycleEffects(nativeAdapter, runtime, store,
        deployments, releases, versions,
        runs, verification, mock(DeploymentReleaseEndpointService.class), mock(PlatformConsumerRepository.class), mapper);

    @BeforeEach void intents() {
        when(store.beforeEffect(any(), anyString())).thenReturn(true);
        when(store.recoveryVerificationEffect(any())).thenReturn("actual-release-verification");
    }

    @Test void recoveryCannotPassVerificationMerelyBecauseItsNativeInventoryIsReadable() {
        var work = work(Goal.RECOVER, Mode.COLD, Phase.VERIFY);
        when(nativeAdapter.observe(any())).thenReturn(observation(true, false, false, false));
        var result = effects.execute(work);
        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.code()).isEqualTo("RECOVERY_NATIVE_READINESS_PENDING");
        verifyNoInteractions(verification, runs);
        verify(store, never()).completed(any(), anyString());
    }

    @Test void failedActualVerificationIsNotTurnedIntoARecoverySuccess() {
        var work = work(Goal.RECOVER, Mode.COLD, Phase.VERIFY);
        when(nativeAdapter.observe(any())).thenReturn(observation(true, true, false, false));
        when(store.providerOperation(work, "actual-release-verification")).thenReturn("vrf-real");
        var run = new DeploymentVerificationRunEntity(); run.setId("vrf-real"); run.setReleaseId("release");
        run.setDeploymentVersionId("version"); run.setStatus("FAILED");
        when(runs.findById("vrf-real")).thenReturn(Optional.of(run));
        assertThat(effects.execute(work).status()).isEqualTo(Status.BLOCKED);
        assertThat(effects.execute(work).status()).isEqualTo(Status.BLOCKED);
        verifyNoInteractions(verification);
    }

    @Test void explicitRetryVerifiesOnceAndContinuationUsesThatExactReportNotTheOldFailedAttempt() {
        var work = work(Goal.RECOVER, Mode.COLD, Phase.VERIFY);
        String effect = "actual-release-verification:retry:1";
        when(store.recoveryVerificationEffect(work)).thenReturn(effect);
        actualVerificationFixture();
        var recorded = new HashMap<String, String>();
        recorded.put("actual-release-verification", "old-failed-run");
        when(store.providerOperation(eq(work), anyString())).thenAnswer(call -> recorded.get(call.getArgument(1)));
        doAnswer(call -> { recorded.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(store).dispatched(eq(work), anyString(), anyString());
        assertThat(effects.execute(work).status()).isEqualTo(Status.SUCCEEDED);
        assertThat(effects.execute(work).status()).isEqualTo(Status.SUCCEEDED);
        verify(verification, times(1)).verify(any(), any(), any(), eq("OPERATIONS_RECOVERY"));
        verify(store, times(1)).beforeEffect(work, effect);
        verify(store).dispatched(work, effect, "fresh-run");
        verify(store, never()).providerOperation(work, "actual-release-verification");
        verify(runs, never()).findById("old-failed-run");
        assertThat(recorded.get("actual-release-verification")).isEqualTo("old-failed-run");
        verify(nativeAdapter, never()).stop(any(), any());
        verify(nativeAdapter, never()).restart(any(), any());
        verifyNoInteractions(runtime);
    }

    @Test void unknownRetryIntentWithoutAReportDoesNotRunVerificationAgain() {
        var work = work(Goal.RECOVER, Mode.COLD, Phase.VERIFY);
        String effect = "actual-release-verification:retry:1";
        when(store.recoveryVerificationEffect(work)).thenReturn(effect);
        when(nativeAdapter.observe(any())).thenReturn(observation(true, true, false, false));
        doReturn(false).when(store).beforeEffect(work, effect);
        assertThat(effects.execute(work).status()).isEqualTo(Status.UNKNOWN);
        assertThat(effects.execute(work).status()).isEqualTo(Status.UNKNOWN);
        verifyNoInteractions(verification, runs);
        verify(store, never()).dispatched(any(), anyString(), anyString());
        verify(nativeAdapter, never()).stop(any(), any());
        verify(nativeAdapter, never()).restart(any(), any());
    }

    @Test void lostVerificationOutcomeRemainsUnknownWithinItsRevision() {
        var work = work(Goal.RECOVER, Mode.COLD, Phase.VERIFY);
        String effect = "actual-release-verification:retry:1";
        when(store.recoveryVerificationEffect(work)).thenReturn(effect);
        actualVerificationFixture();
        when(store.beforeEffect(work, effect)).thenReturn(true, false);
        doThrow(new IllegalStateException("synthetic lost verification outcome")).when(verification)
            .verify(any(), any(), any(), eq("OPERATIONS_RECOVERY"));
        assertThatThrownBy(() -> effects.execute(work)).hasMessageContaining("synthetic lost verification outcome");
        assertThat(effects.execute(work).status()).isEqualTo(Status.UNKNOWN);
        verify(verification, times(1)).verify(any(), any(), any(), eq("OPERATIONS_RECOVERY"));
        verify(store, never()).dispatched(any(), anyString(), anyString());
    }

    @Test void aRetryStillRejectsAVerificationReportFromAnotherRelease() {
        var work = work(Goal.RECOVER, Mode.COLD, Phase.VERIFY);
        String effect = "actual-release-verification:retry:2";
        when(store.recoveryVerificationEffect(work)).thenReturn(effect);
        when(nativeAdapter.observe(any())).thenReturn(observation(true, true, false, false));
        when(store.providerOperation(work, effect)).thenReturn("foreign-report");
        var foreign = new DeploymentVerificationRunEntity(); foreign.setId("foreign-report");
        foreign.setReleaseId("other-release"); foreign.setDeploymentVersionId("version"); foreign.setStatus("PASSED");
        when(runs.findById("foreign-report")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> effects.execute(work)).hasMessageContaining("RECOVERY_VERIFICATION_SCOPE_CHANGED");
        verifyNoInteractions(verification);
    }

    @Test void retirementRequiresActualStorageAbsenceNotOnlyMissingApplications() {
        var work = work(Goal.RETIRE, Mode.COLD, Phase.VERIFY);
        when(nativeAdapter.verifyAbsent(any())).thenReturn(observation(true, false, true, false));
        var result = effects.execute(work);
        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.code()).isEqualTo("NATIVE_STORAGE_ABSENCE_UNPROVEN");
    }

    @Test void deleteCheckpointUsesTheRealIntentJournalEvenWhenNoProviderJobWasRecorded() {
        var work = work(Goal.RETIRE, Mode.COLD, Phase.DELETE);
        String effect = "native:snapshot-hash:DELETE:runtime";
        when(store.effectKnown(work, effect)).thenReturn(true);
        when(nativeAdapter.delete(any(), eq(true), any())).thenAnswer(call -> {
            NativeLifecycleAdapter.Checkpoint checkpoint = call.getArgument(2);
            assertThat(checkpoint.isKnown(effect)).isTrue();
            assertThat(checkpoint.isComplete(effect)).isFalse();
            assertThat(checkpoint.isKnown("native:other-snapshot:DELETE:runtime")).isFalse();
            return observation(false, false, false, false);
        });
        assertThat(effects.execute(work).status()).isEqualTo(Status.PENDING);
        verify(store).effectKnown(work, effect);
        verify(store, never()).providerOperation(any(), anyString());
        verify(store, never()).beforeEffect(any(), anyString());
        verify(store, never()).dispatched(any(), anyString(), anyString());
    }

    @Test void unknownExternalWorkerCannotBeAuthorizedByAnEvidenceString() {
        var work = work(Goal.RETIRE, Mode.GRACEFUL, Phase.FENCE);
        when(runtime.execute(eq(Phase.OBSERVE_DRAIN), any(), any())).thenReturn(
            new RuntimeLifecycleGateway.Result(Status.SUCCEEDED, "RUNTIME_LOCAL_DRAIN_COMPLETE", List.of(), "real-snapshot", 1, List.of("EXTERNAL_INBOX")));
        assertThat(effects.execute(work).status()).isEqualTo(Status.BLOCKED);
        verify(nativeAdapter, never()).quiesceWorkers(any(), any());
        verify(runtime, never()).execute(eq(Phase.FENCE), any(), any());
        verify(store, never()).dispatched(any(), anyString(), anyString());
    }

    @Test void runtimeFencingUsesADurableProofOnlyAfterActualOwnedWorkersAreStopped() {
        var work = work(Goal.RETIRE, Mode.GRACEFUL, Phase.FENCE);
        when(runtime.execute(eq(Phase.OBSERVE_DRAIN), any(), any())).thenReturn(
            new RuntimeLifecycleGateway.Result(Status.SUCCEEDED, "RUNTIME_LOCAL_DRAIN_COMPLETE", List.of(), "snapshot", 1, List.of("CONNECTOR")));
        when(nativeAdapter.supportsRemoteDependency(any(), eq("CONNECTOR"))).thenReturn(true);
        when(nativeAdapter.quiesceWorkers(any(), any())).thenReturn(observation(true, false, false, false));
        when(runtime.execute(eq(Phase.FENCE), any(), any())).thenAnswer(call -> {
            RuntimeLifecycleGateway.Operation operation = call.getArgument(2);
            assertThat(operation.externalEvidenceReference()).isEqualTo("native-fence:command-FENCE");
            return new RuntimeLifecycleGateway.Result(Status.SUCCEEDED, "RUNTIME_AUTHORITY_REVOKED", List.of(), "snapshot", 2);
        });
        assertThat(effects.execute(work).status()).isEqualTo(Status.SUCCEEDED);
        var order = inOrder(nativeAdapter, store, runtime);
        order.verify(runtime).execute(eq(Phase.OBSERVE_DRAIN), any(), any());
        order.verify(nativeAdapter).supportsRemoteDependency(any(), eq("CONNECTOR"));
        order.verify(nativeAdapter).quiesceWorkers(any(), any());
        order.verify(store).beforeEffect(work, "authoritative-native-workers-fenced");
        order.verify(store).dispatched(work, "authoritative-native-workers-fenced", "native-fence:command-FENCE");
        order.verify(store).completed(work, "authoritative-native-workers-fenced");
        order.verify(store).beforeEffect(work, "runtime-FENCE");
        order.verify(runtime).execute(eq(Phase.FENCE), any(), any());
    }

    @Test void uncertainFenceReobservesTheSameNativeProofRatherThanStartingAnotherDrain() {
        var work = work(Goal.RETIRE, Mode.GRACEFUL, Phase.FENCE);
        when(store.providerOperation(work, "authoritative-native-workers-fenced")).thenReturn("native-fence:command-FENCE");
        when(nativeAdapter.quiesceWorkers(any(), any())).thenReturn(observation(true, false, false, false));
        when(runtime.execute(eq(Phase.FENCE), any(), any())).thenReturn(
            new RuntimeLifecycleGateway.Result(Status.SUCCEEDED, "RUNTIME_AUTHORITY_REVOKED", List.of(), null, 2));
        assertThat(effects.execute(work).status()).isEqualTo(Status.SUCCEEDED);
        verify(runtime, never()).execute(eq(Phase.OBSERVE_DRAIN), any(), any());
        verify(store, never()).dispatched(any(), anyString(), anyString());
    }

    @Test void recoveryWaitsForExactStopsBeforeRestartAndDoesNotRepeatTheStopAfterRestart() {
        var work = work(Goal.RECOVER, Mode.COLD, Phase.RECOVER);
        when(nativeAdapter.stop(any(), any())).thenReturn(observation(false, false, false, false));
        assertThat(effects.execute(work).status()).isEqualTo(Status.PENDING);
        verify(nativeAdapter, never()).restart(any(), any());
        when(store.effectComplete(work, "recovery-existing-resources-stopped")).thenReturn(true);
        when(nativeAdapter.restart(any(), any())).thenReturn(observation(true, true, false, false));
        assertThat(effects.execute(work).status()).isEqualTo(Status.SUCCEEDED);
        verify(nativeAdapter, times(1)).stop(any(), any());
        verify(nativeAdapter, times(1)).restart(any(), any());
    }

    private LifecycleCommandStore.Work work(Goal goal, Mode mode, Phase phase) {
        var job = new JobRequest("1", "operation", "grant", "customer", "tenant", "deployment", "staging", "release",
            "a".repeat(64), goal, mode, "policy", Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now());
        var nativeScope = new NativeSnapshot("customer", "tenant", "deployment", "staging", "release", "target-hash",
            "release-hash", List.of(), List.of(), List.of(), "snapshot-hash", Instant.now());
        var scope = new LifecycleCommandStore.Scope(job, "version", "b".repeat(64), "c".repeat(64),
            mapper.valueToTree(new NativeLifecycleEffects.Frozen(nativeScope, "https://runtime.example.invalid")), true);
        return new LifecycleCommandStore.Work(new Command("1", "command-" + phase, "operation", "grant", "a".repeat(64), phase),
            new LifecycleCommandStore.Grant("grant", scope, "OPEN", true), "worker");
    }
    private void actualVerificationFixture() {
        when(nativeAdapter.observe(any())).thenReturn(observation(true, true, false, false));
        when(store.transaction(any())).thenAnswer(call -> call.getArgument(0, Supplier.class).get());
        var deployment = new DeploymentEntity(); deployment.setId("deployment");
        var release = new DeploymentReleaseEntity(); release.setId("release");
        var version = new DeploymentVersionEntity(); version.setId("version");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var run = new DeploymentVerificationRunEntity(); run.setId("fresh-run"); run.setReleaseId("release");
        run.setDeploymentVersionId("version"); run.setStatus("PASSED");
        when(verification.verify(deployment, version, release, "OPERATIONS_RECOVERY")).thenReturn(run);
        when(runs.findById("fresh-run")).thenReturn(Optional.of(run));
    }
    private Observation observation(boolean complete, boolean ready, boolean absent, boolean reclaimed) {
        return new Observation("snapshot-hash", Instant.now(), List.of(), false, absent, ready, reclaimed, complete, List.of());
    }
}

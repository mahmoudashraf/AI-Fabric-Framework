package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
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
    private final NativeLifecycleEffects effects = new NativeLifecycleEffects(nativeAdapter, runtime, store,
        mock(DeploymentRepository.class), mock(DeploymentReleaseRepository.class), mock(DeploymentVersionRepository.class),
        runs, verification, mock(DeploymentReleaseEndpointService.class), mock(PlatformConsumerRepository.class), mapper);

    @BeforeEach void intents() { when(store.beforeEffect(any(), anyString())).thenReturn(true); }

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
        verifyNoInteractions(verification);
    }

    @Test void retirementRequiresActualStorageAbsenceNotOnlyMissingApplications() {
        var work = work(Goal.RETIRE, Mode.COLD, Phase.VERIFY);
        when(nativeAdapter.verifyAbsent(any())).thenReturn(observation(true, false, true, false));
        var result = effects.execute(work);
        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.code()).isEqualTo("NATIVE_STORAGE_ABSENCE_UNPROVEN");
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
    private Observation observation(boolean complete, boolean ready, boolean absent, boolean reclaimed) {
        return new Observation("snapshot-hash", Instant.now(), List.of(), false, absent, ready, reclaimed, complete, List.of());
    }
}

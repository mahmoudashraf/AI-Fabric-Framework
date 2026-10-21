package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentProvider;
import com.ai.fabric.platform.backend.deployment.service.RuntimeArtifactRepairInboxOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class RuntimeArtifactRepairOperationsTest {
    @ParameterizedTest @EnumSource(Effect.class)
    void eachCommandDispatchesExactlyOneNativeMutationAndReconciliationDispatchesNone(Effect effect) {
        var h = new Harness(effect);
        assertThatCode(() -> h.ops.perform(h.plan, effect, "admin")).doesNotThrowAnyException();
        int runtimeStops = effect == Effect.QUIESCE_CONNECTOR || effect == Effect.STOP_RUNTIME ? 1 : 0;
        verify(h.provider, times(runtimeStops)).stopRuntimeArtifactRepairApplication(any(), anyBoolean());
        verify(h.provider, times(effect == Effect.PATCH_IMAGE ? 1 : 0)).patchRuntimeArtifactRepairImage(any(), any());
        verify(h.provider, times(effect == Effect.PATCH_PROVENANCE ? 1 : 0)).patchRuntimeArtifactRepairProvenance(any(), anyString(), anyString());
        verify(h.provider, times(effect == Effect.DEPLOY_RUNTIME || effect == Effect.RESUME_CONNECTOR ? 1 : 0))
            .deployRuntimeArtifactRepairApplication(any(), anyBoolean());
        verify(h.inbox, times(effect == Effect.QUIESCE_INBOX ? 1 : 0)).stop(any(), anyString());
        verify(h.inbox, times(effect == Effect.RESUME_INBOX ? 1 : 0)).start(any(), anyString());
        verify(h.runtime, times(effect == Effect.BEGIN_DRAIN || effect == Effect.CANCEL_DRAIN ? 1 : 0)).command(anyString(), any(), anyMap());
        clearInvocations(h.provider, h.inbox, h.runtime);
        assertThatCode(() -> h.ops.reconcile(h.plan, effect)).doesNotThrowAnyException();
        verify(h.provider, never()).stopRuntimeArtifactRepairApplication(any(), anyBoolean());
        verify(h.provider, never()).patchRuntimeArtifactRepairImage(any(), any());
        verify(h.provider, never()).patchRuntimeArtifactRepairProvenance(any(), anyString(), anyString());
        verify(h.provider, never()).deployRuntimeArtifactRepairApplication(any(), anyBoolean());
        verify(h.inbox, never()).stop(any(), anyString()); verify(h.inbox, never()).start(any(), anyString());
        verify(h.runtime, never()).command(anyString(), any(), anyMap());
    }
    @ParameterizedTest @EnumSource(value = Effect.class, names = {"BEGIN_DRAIN", "STOP_RUNTIME", "DEPLOY_RUNTIME", "CANCEL_DRAIN"})
    void explicitIdleCheckpointRestartPreservesTerminalFailureHistoryWithoutClaimingFencing(Effect effect) {
        var h = new Harness(effect, Mode.IDLE_CHECKPOINT_COLD_RESTART);
        assertThatCode(() -> h.ops.perform(h.plan, effect, "admin")).doesNotThrowAnyException();
        verify(h.budgets, atLeastOnce()).indexingOverview(anyString(), anyMap());
        verify(h.budgets, never()).initialize(anyString(), any(), anyMap());
        verify(h.budgets, never()).activate(anyString(), any(), anyMap());
        verify(h.budgets, never()).extendValidity(anyString(), any(), anyMap());
    }
    static class Harness {
        static final Instant NOW = AdmissionFixture.NOW;
        final AdmissionJson json = new AdmissionJson(new ObjectMapper().registerModule(new JavaTimeModule()));
        final CoolifyDeploymentProvider provider = mock(CoolifyDeploymentProvider.class);
        final RuntimeArtifactRepairInboxOperations inbox = mock(RuntimeArtifactRepairInboxOperations.class);
        final RuntimeGenerationTransitionClient runtime = mock(RuntimeGenerationTransitionClient.class);
        final ProviderUsageRuntimeClient budgets = mock(ProviderUsageRuntimeClient.class);
        final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
        final RuntimeArtifactRepairJournal journal = mock(RuntimeArtifactRepairJournal.class);
        final Map<Effect, Map<String, String>> dispatches = new HashMap<>();
        final AtomicBoolean runtimeStarted = new AtomicBoolean(), connectorStarted = new AtomicBoolean(), inboxStarted = new AtomicBoolean(), cancelled = new AtomicBoolean();
        final Plan plan;
        final RuntimeArtifactRepairOperations ops;
        Harness(Effect effect) { this(effect, Mode.FULL_DRAIN); }
        Harness(Effect effect, Mode mode) {
            var scope = new Scope("customer", "tenant", "deployment1", AdmissionFixture.TARGET, "release", "version", "config", "authority", "reservation", "generation", "https://runtime.example.test", "runtime-app", "connector-app", "runtime-pg");
            var old = new ArtifactPin("old", "old-pin", "reviewed/runtime", "sha256:" + "a".repeat(64), "a".repeat(40), "ci-old", "staging", "old-manifest");
            var next = new ArtifactPin("next", "new-pin", "reviewed/runtime", "sha256:" + "b".repeat(64), "b".repeat(40), "ci-new", "staging", "new-manifest");
            var request = new Request(new AdmissionContracts.Fence("reservation", 1, "generation"), "next", "offer", 2, "repair", "reviewed fix", true, mode);
            var local = new LocalEvidence(scope, request, old, next, Map.of("graph", "unchanged"), NOW.plusSeconds(300), NOW.plusSeconds(1800));
            plan = new Plan("plan", "admin", local, new NativeEvidence(Map.of("inventory", json.write(inventory(false, false, "running:healthy", false)),
                "inbox", json.write(inboxInventory(true)), "inboxJobs", json.write(List.of(oldJob("inbox-app")))),
                RuntimeArtifactRepairWalletTest.wallet(null, 3), readback(scope, RuntimeGenerationTransitionContracts.State.ACTIVE, 0), NOW), NOW, NOW.plusSeconds(300));
            ops = new RuntimeArtifactRepairOperations(provider, budgets, runtime, authority, inbox, journal, json, Clock.fixed(NOW, ZoneOffset.UTC));
            when(journal.fingerprint(plan)).thenReturn("plan-hash");
            when(journal.dispatch(eq(plan), any())).thenAnswer(i -> dispatches.get(i.getArgument(1)));
            doAnswer(i -> { dispatches.put(i.getArgument(1), i.getArgument(2)); return null; }).when(journal).dispatched(eq(plan), any(), anyMap(), eq("admin"));
            when(journal.confirmed(eq(plan), any())).thenAnswer(i -> {
                Effect e = i.getArgument(1);
                if (e == Effect.BEGIN_DRAIN && mode == Mode.IDLE_CHECKPOINT_COLD_RESTART)
                    return new EffectReceipt("plan", e, Outcome.CONFIRMED, "request", Map.of("idleQueue", json.write(new RuntimeArtifactRepairIdleCheckpoint.Queue(0, 0, 0, 36, 0, 36))), NOW);
                return new EffectReceipt("plan", e, Outcome.CONFIRMED, "request", e == Effect.QUIESCE_CONNECTOR
                    ? Map.of("CONNECTOR", "connector-stop") : Map.of("HUMAN_INBOX", "inbox-stop"), NOW);
            });
            if (effect.ordinal() > Effect.DEPLOY_RUNTIME.ordinal()) {
                runtimeStarted.set(true); dispatches.put(Effect.DEPLOY_RUNTIME, Map.of("jobUuid", "runtime-job"));
            }
            when(provider.runtimeArtifactRepairInventory(scope)).thenAnswer(i -> {
                boolean replacement = effect.ordinal() >= Effect.PATCH_IMAGE.ordinal();
                String status = effect == Effect.STOP_RUNTIME || effect == Effect.PATCH_IMAGE || effect == Effect.PATCH_PROVENANCE || effect == Effect.DEPLOY_RUNTIME && !runtimeStarted.get()
                    ? "stopped" : "running:healthy";
                return inventory(replacement, runtimeStarted.get(), status, connectorStarted.get());
            });
            when(provider.deployRuntimeArtifactRepairApplication(scope, false)).thenAnswer(i -> { runtimeStarted.set(true); return "runtime-job"; });
            when(provider.deployRuntimeArtifactRepairApplication(scope, true)).thenAnswer(i -> { connectorStarted.set(true); return "connector-job"; });
            when(inbox.observe(local)).thenAnswer(i -> inboxInventory(effect != Effect.QUIESCE_INBOX && effect != Effect.RESUME_INBOX || inboxStarted.get()));
            when(inbox.jobs(local)).thenAnswer(i -> inboxStarted.get() ? List.of(oldJob("inbox-app"), newJob("inbox-job", "inbox-app")) : List.of(oldJob("inbox-app")));
            when(inbox.start(plan, "admin")).thenAnswer(i -> { inboxStarted.set(true); return "inbox-job"; });
            when(authority.issueRuntimeArtifactRepairHeaders(eq("plan"), anyBoolean())).thenReturn(Map.of("Authorization", "synthetic-only"));
            when(budgets.read(anyString(), anyMap())).thenReturn(RuntimeArtifactRepairWalletTest.wallet(null, 3));
            when(budgets.indexingOverview(anyString(), anyMap())).thenReturn(new RuntimeArtifactRepairIdleCheckpointTest().overview());
            when(runtime.status(anyString(), any(), anyMap())).thenAnswer(i -> readback(scope,
                cancelled.get() ? RuntimeGenerationTransitionContracts.State.ACTIVE : RuntimeGenerationTransitionContracts.State.DRAINING, cancelled.get() ? 2 : 1));
            when(runtime.command(anyString(), any(), anyMap())).thenAnswer(i -> {
                var command = (RuntimeGenerationTransitionContracts.Command) i.getArgument(1);
                if (command.type() == RuntimeGenerationTransitionContracts.CommandType.CANCEL_DRAIN) cancelled.set(true);
                var current = readback(scope, cancelled.get() ? RuntimeGenerationTransitionContracts.State.ACTIVE : RuntimeGenerationTransitionContracts.State.DRAINING, cancelled.get() ? 2 : 1);
                return new RuntimeGenerationTransitionContracts.CommandResult(current, current, false);
            });
            var idle = new RuntimeArtifactRepairIdleCheckpointTest().snapshot("valid");
            when(runtime.snapshot(anyString(), any(), anyMap())).thenAnswer(i -> new RuntimeGenerationTransitionContracts.Snapshot("snapshot", "drain-proof",
                readback(scope, RuntimeGenerationTransitionContracts.State.DRAINING, 1), NOW, idle.openConversations(), idle.pendingConfirmations(), idle.humanWork(), idle.backgroundWork(), 0, true,
                mode == Mode.FULL_DRAIN, mode == Mode.FULL_DRAIN, false, false,
                "topology-hash", List.of(new RuntimeGenerationTransitionContracts.RemoteObligation("CONNECTOR", target(scope), "topology", "stop"),
                    new RuntimeGenerationTransitionContracts.RemoteObligation("HUMAN_INBOX", target(scope), "topology", "stop")), mode == Mode.FULL_DRAIN ? List.of() : idle.blockers()));
        }
        NativeInventory inventory(boolean replacement, boolean deployed, String status, boolean connectorRunning) {
            return new NativeInventory(Map.of("ownedStorage", "unchanged", "runtimeSourceCommit", (replacement ? "b" : "a").repeat(40)), status, connectorRunning ? "running:healthy" : "stopped", "reviewed/runtime",
                "sha256-" + (replacement ? "b" : "a").repeat(64), deployed ? List.of(oldJob("runtime-app"), newJob("runtime-job", "runtime-app")) : List.of(oldJob("runtime-app")),
                connectorRunning ? List.of(oldJob("connector-app"), newJob("connector-job", "connector-app")) : List.of(oldJob("connector-app")), NOW);
        }
        Map<String, String> inboxInventory(boolean running) { return Map.of("applicationUuid", "inbox-app", "applicationStatus", running ? "running:healthy" : "stopped", "storage", "unchanged"); }
        NativeJob oldJob(String app) { return new NativeJob("old-" + app, app, "finished", NOW.minusSeconds(60), NOW.minusSeconds(30)); }
        NativeJob newJob(String uuid, String app) { return new NativeJob(uuid, app, "finished", NOW, NOW); }
        static RuntimeGenerationTransitionContracts.Target target(Scope scope) { return new RuntimeGenerationTransitionContracts.Target(scope.deploymentId(), scope.targetProfileId(), scope.releaseId()); }
        RuntimeGenerationTransitionContracts.Readback readback(Scope scope, RuntimeGenerationTransitionContracts.State state, long epoch) {
            return new RuntimeGenerationTransitionContracts.Readback(target(scope), scope.tenantId(), state, epoch,
                epoch == 0 ? null : "artifact-repair:plan", epoch == 0 ? null : "plan-hash", NOW.plusSeconds(1800), false, false, NOW);
        }
    }
}

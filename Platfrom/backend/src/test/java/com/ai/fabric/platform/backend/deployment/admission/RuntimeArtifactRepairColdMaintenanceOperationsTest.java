package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;
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
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class RuntimeArtifactRepairColdMaintenanceOperationsTest {
    @Test void attendedColdReplacementPreservesFalseDrainFlagsAndUsesOnlyTheDedicatedSystemTransition() {
        var h = new Harness(); h.prepare();
        for (Effect effect : effects(Mode.COLD_MAINTENANCE_REAPPLY)) {
            var observed = h.operations.perform(h.plan, effect, "admin");
            h.confirmed.put(effect, new EffectReceipt(h.plan.id(), effect, Outcome.CONFIRMED, "effect-hash", observed, Harness.NOW));
        }
        var proof = h.operations.finalProof(h.plan, h.local);
        var receipt = h.json.read(proof.finalNativeInventory().get("coldMaintenanceReceipt"), ColdMaintenanceTransitionResult.class);
        assertThat(receipt.evidenceKind()).isEqualTo("PHYSICAL_STOP_COLD_MAINTENANCE_V1");
        assertThat(receipt.observedInventory().inventoryComplete()).isFalse();
        assertThat(receipt.observedInventory().workerFencingComplete()).isFalse();
        assertThat(receipt.transition().previous().state()).isEqualTo(State.DRAINING);
        assertThat(receipt.transition().previous().authorityRevoked()).isFalse();
        assertThat(receipt.transition().current().fenceEpoch()).isEqualTo(2);
        verify(h.provider).stopRuntimeArtifactRepairApplication(h.scope, false);
        verify(h.provider, never()).stopRuntimeArtifactRepairApplication(h.scope, true);
        verify(h.inbox, never()).stop(any(), anyString());
        verify(h.authority).issueRuntimeArtifactRepairColdMaintenanceHeaders("cold-plan");
        verify(h.runtime).coldMaintenanceTransition(eq(h.scope.runtimeBaseUrl()), any(), eq(Map.of("Authorization", "cold-system-only")));
        verify(h.runtime, never()).command(anyString(), any(), anyMap());
        verify(h.budgets, never()).initialize(anyString(), any(), anyMap());
        verify(h.budgets, never()).activate(anyString(), any(), anyMap());
        verify(h.budgets, never()).extendValidity(anyString(), any(), anyMap());
        assertThat(h.dispatches.get(Effect.QUIESCE_CONNECTOR)).containsEntry("nativeCommandDispatched", "false");
        assertThat(h.dispatches.get(Effect.QUIESCE_INBOX)).containsEntry("nativeCommandDispatched", "false");
        clearInvocations(h.provider, h.runtime, h.inbox);
        h.operations.reconcile(h.plan, Effect.ADOPT_COLD_GENERATION);
        verify(h.runtime, never()).coldMaintenanceTransition(anyString(), any(), anyMap());
        verify(h.provider, never()).deployRuntimeArtifactRepairApplication(any(), anyBoolean());
    }

    @Test void liveConversationPreventsEvenPlanningTheColdReplacement() {
        var h = new Harness(); h.openConversations = 1;
        assertThatThrownBy(h::prepare).hasMessageContaining("COLD_FOREGROUND_WORK_REMAINS");
        verify(h.provider, never()).stopRuntimeArtifactRepairApplication(any(), anyBoolean());
        verify(h.runtime, never()).coldMaintenanceTransition(anyString(), any(), anyMap());
    }

    @ParameterizedTest @ValueSource(strings = {"connector", "inbox"})
    void writerRestartPreventsPhysicalRuntimeStop(String writer) {
        var h = new Harness(); h.prepare();
        if (writer.equals("connector")) {
            var prior = h.inventory();
            when(h.provider.runtimeArtifactRepairInventory(h.scope)).thenReturn(new NativeInventory(prior.fingerprints(),
                prior.runtimeStatus(), "running:healthy", prior.runtimeRepository(), prior.runtimeTag(), prior.runtimeJobs(), prior.connectorJobs(), prior.observedAt()));
        } else {
            h.inboxRunning = true;
            when(h.inbox.jobs(h.local)).thenReturn(List.of(h.oldJob("inbox-app")));
        }
        assertThatThrownBy(() -> h.operations.perform(h.plan, Effect.STOP_RUNTIME, "admin"))
            .hasMessageContaining("COLD_REMOTE_WRITERS_NOT_STOPPED");
        verify(h.provider, never()).stopRuntimeArtifactRepairApplication(any(), anyBoolean());
    }

    @Test void oldDrainCommandsAreNotApprovedEffectsForColdReplacement() {
        var h = new Harness(); h.prepare();
        for (var effect : List.of(Effect.BEGIN_DRAIN, Effect.CANCEL_DRAIN))
            assertThatThrownBy(() -> h.operations.perform(h.plan, effect, "admin")).hasMessageContaining("EFFECT_NOT_APPROVED");
        verify(h.runtime, never()).command(anyString(), any(), anyMap());
    }

    @Test void missingTransitionResponseCannotBeReconciledByRepeatingTheMutation() {
        var h = new Harness(); h.prepare(); h.patched = true; h.provenance = true; h.deployed = true;
        h.dispatches.put(Effect.DEPLOY_RUNTIME, Map.of("jobUuid", "new-runtime-job"));
        assertThatThrownBy(() -> h.operations.reconcile(h.plan, Effect.ADOPT_COLD_GENERATION))
            .hasMessageContaining("COLD_TRANSITION_RECEIPT_REQUIRED");
        verify(h.runtime, never()).coldMaintenanceTransition(anyString(), any(), anyMap());
    }

    private static final class Harness {
        static final Instant NOW = AdmissionFixture.NOW;
        final AdmissionJson json = new AdmissionJson(new ObjectMapper().registerModule(new JavaTimeModule()));
        final CoolifyDeploymentProvider provider = mock(CoolifyDeploymentProvider.class);
        final RuntimeArtifactRepairInboxOperations inbox = mock(RuntimeArtifactRepairInboxOperations.class);
        final RuntimeGenerationTransitionClient runtime = mock(RuntimeGenerationTransitionClient.class);
        final ProviderUsageRuntimeClient budgets = mock(ProviderUsageRuntimeClient.class);
        final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
        final RuntimeArtifactRepairJournal journal = mock(RuntimeArtifactRepairJournal.class);
        final Map<Effect, Map<String, String>> dispatches = new HashMap<>();
        final Map<Effect, EffectReceipt> confirmed = new HashMap<>();
        final Scope scope = new Scope("customer", "tenant", "deployment1", AdmissionFixture.TARGET, "candidate", "new-version", "config", "authority",
            "reservation", "generation", "https://runtime.example.test", "runtime-app", "connector-app", "runtime-pg");
        final Target previous = new Target(scope.deploymentId(), scope.targetProfileId(), "previous");
        final Target next = new Target(scope.deploymentId(), scope.targetProfileId(), scope.releaseId());
        final Readback draining = new Readback(previous, "tenant", State.DRAINING, 1, "replace:candidate", "d".repeat(64), NOW.plusSeconds(20), false, false, NOW);
        final Readback active = new Readback(next, "tenant", State.ACTIVE, 2, null, null, null, false, false, NOW);
        final LocalEvidence local;
        final RuntimeArtifactRepairOperations operations;
        Plan plan;
        boolean runtimeRunning = true, connectorRunning, inboxRunning, patched, provenance, deployed, adopted;
        long openConversations;
        Harness() {
            var old = new ArtifactPin("original", "old", "reviewed/runtime", "sha256:" + "a".repeat(64), "a".repeat(40), "ci-old", "staging", "old-manifest");
            var replacement = new ArtifactPin("replacement", "new", "reviewed/runtime", "sha256:" + "b".repeat(64), "b".repeat(40), "ci-new", "staging", "new-manifest");
            var request = new Request(new AdmissionContracts.Fence("reservation", 1, "generation"), "replacement", "offer", 2,
                "cold-repair", "Attended first staging pilot", true, Mode.COLD_MAINTENANCE_REAPPLY,
                new ColdMaintenanceAcknowledgement("previous", "owner-approved-downtime", true));
            var wallet = RuntimeArtifactRepairWalletTest.wallet(null, 3);
            var pins = new TreeMap<String, String>();
            pins.put("graph", "unchanged"); pins.put("coldPriorRuntime", json.write(new ColdPriorRuntime("previous", "old-version", "old-authority")));
            pins.put("walletApprovalId", wallet.approvalId()); pins.put("walletStoreUuid", wallet.storeUuid().toString());
            pins.put("walletRuntimeFingerprint", wallet.runtimeFingerprint());
            local = new LocalEvidence(scope, request, old, replacement, pins, NOW.plusSeconds(300), NOW.plusSeconds(1800));
            operations = new RuntimeArtifactRepairOperations(provider, budgets, runtime, authority, inbox, journal, json, Clock.fixed(NOW, ZoneOffset.UTC));
            when(provider.runtimeArtifactRepairInventory(scope)).thenAnswer(i -> inventory());
            when(provider.runtimeArtifactRepairTopology(scope)).thenReturn("f".repeat(64));
            doAnswer(i -> { runtimeRunning = false; return null; }).when(provider).stopRuntimeArtifactRepairApplication(scope, false);
            doAnswer(i -> { patched = true; return null; }).when(provider).patchRuntimeArtifactRepairImage(scope, replacement);
            doAnswer(i -> { provenance = true; return null; }).when(provider).patchRuntimeArtifactRepairProvenance(scope, old.gitCommit(), replacement.gitCommit());
            when(provider.deployRuntimeArtifactRepairApplication(scope, false)).thenAnswer(i -> { deployed = true; runtimeRunning = true; return "new-runtime-job"; });
            when(provider.deployRuntimeArtifactRepairApplication(scope, true)).thenAnswer(i -> { connectorRunning = true; return "new-connector-job"; });
            when(inbox.observe(local)).thenAnswer(i -> inboxInventory());
            when(inbox.jobs(local)).thenAnswer(i -> inboxRunning ? List.of(oldJob("inbox-app"), newJob("new-inbox-job", "inbox-app")) : List.of(oldJob("inbox-app")));
            when(inbox.start(any(), eq("admin"))).thenAnswer(i -> { inboxRunning = true; return "new-inbox-job"; });
            when(budgets.read(anyString(), anyMap())).thenReturn(wallet);
            when(authority.issueRuntimeArtifactRepairPriorReadHeaders(anyString())).thenReturn(Map.of("Authorization", "prior-read-only"));
            when(authority.issueRuntimeArtifactRepairHeaders(anyString(), anyBoolean())).thenReturn(Map.of("Authorization", "candidate-read"));
            when(authority.issueRuntimeArtifactRepairColdMaintenanceHeaders(anyString())).thenReturn(Map.of("Authorization", "cold-system-only"));
            when(runtime.status(anyString(), any(), anyMap())).thenAnswer(i -> adopted ? active : draining);
            when(runtime.snapshot(anyString(), any(), anyMap())).thenAnswer(i -> snapshot("e".repeat(64)));
            when(runtime.coldMaintenanceTransition(anyString(), any(), anyMap())).thenAnswer(i -> {
                var command = (ColdMaintenanceTransition) i.getArgument(1); adopted = true;
                return new ColdMaintenanceTransitionResult(new GenerationTransitionResult(draining, active,
                    command.transition().providerStopEvidenceReference(), NOW, false), snapshot("f".repeat(64)),
                    "PHYSICAL_STOP_COLD_MAINTENANCE_V1", command.approvalReference(), true);
            });
            when(journal.fingerprint(any())).thenReturn("c".repeat(64));
            when(journal.executionDeadline(any())).thenReturn(NOW.plusSeconds(1800));
            when(journal.dispatch(any(), any())).thenAnswer(i -> dispatches.get(i.getArgument(1)));
            when(journal.confirmed(any(), any())).thenAnswer(i -> confirmed.get(i.getArgument(1)));
            doAnswer(i -> { dispatches.put(i.getArgument(1), i.getArgument(2)); return null; }).when(journal).dispatched(any(), any(), anyMap(), eq("admin"));
        }
        void prepare() { plan = new Plan("cold-plan", "admin", local, operations.observe("preview", local), NOW, NOW.plusSeconds(300)); }
        NativeInventory inventory() {
            return new NativeInventory(Map.of("ownedStorage", "unchanged", "runtimeSourceCommit", (provenance ? "b" : "a").repeat(40)),
                runtimeRunning ? "running:healthy" : "stopped", connectorRunning ? "running:healthy" : "stopped", "reviewed/runtime",
                "sha256-" + (patched ? "b" : "a").repeat(64), deployed ? List.of(oldJob("runtime-app"), newJob("new-runtime-job", "runtime-app")) : List.of(oldJob("runtime-app")),
                connectorRunning ? List.of(oldJob("connector-app"), newJob("new-connector-job", "connector-app")) : List.of(oldJob("connector-app")), NOW);
        }
        Map<String, String> inboxInventory() {
            return Map.of("applicationUuid", "inbox-app", "databaseUuid", "inbox-db", "configuration", "same", "applicationStatus", inboxRunning ? "running:healthy" : "stopped");
        }
        Snapshot snapshot(String topology) {
            var zero = new Metric(0L, true, List.of(new Observation("complete-store", 0L, true, List.of())));
            var foreground = new Metric(openConversations, true, List.of(new Observation("chat_sessions", openConversations, true, List.of())));
            var incomplete = new Metric(null, false, List.of(new Observation("ai_specialist_execution", 0L, false, List.of("DRAIN_PROVIDER_UNSUPPORTED:ai_specialist_execution"))));
            return new Snapshot("snapshot", "actual-inventory", draining, NOW, foreground, zero, zero, incomplete, 0,
                false, false, false, false, false, topology, List.of(), List.of("DRAIN_PROVIDER_UNSUPPORTED:ai_specialist_execution"));
        }
        NativeJob oldJob(String app) { return new NativeJob("old-" + app, app, "finished", NOW.minusSeconds(100), NOW.minusSeconds(90)); }
        NativeJob newJob(String id, String app) { return new NativeJob(id, app, "finished", NOW, NOW); }
    }
}

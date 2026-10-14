package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeRetirementTestFixture.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RuntimeTargetRetirementServiceTest {
    @Test
    void explicitlyTransitionsAStoppedFencedGenerationAndPreservesRuntimeData() {
        var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
        f.session("retained", "alice", "CLOSED", "{}");
        f.service.status(TARGET);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY));

        var replacement = f.replacement("release-2");
        GenerationTransition transition = new GenerationTransition(TARGET, replacement.target(), "tenant-1", 2,
            f.inventory.topologyFingerprint(), replacement.inventory().topologyFingerprint(), "generation-2",
            "b".repeat(64), "coolify-readback:deployment-2:old-container-stopped");
        GenerationTransitionResult applied = replacement.service().transition(transition);

        assertThat(applied.replay()).isFalse();
        assertThat(applied.previous().state()).isEqualTo(State.AUTHORITY_REVOKED);
        assertThat(applied.current().target()).isEqualTo(replacement.target());
        assertThat(applied.current().state()).isEqualTo(State.ACTIVE);
        assertThat(applied.current().fenceEpoch()).isEqualTo(3);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE id = 'retained'", Long.class)).isOne();
        assertThat(replacement.service().transition(transition).replay()).isTrue();
        assertThat(replacement.service().status(replacement.target()).state()).isEqualTo(State.ACTIVE);
        assertThatThrownBy(() -> f.service.status(TARGET)).hasMessageContaining("PERSISTED_RUNTIME_GENERATION_MISMATCH");
    }

    @Test
    void generationTransitionFailsClosedWithoutDrainFenceOrExactEvidence() {
        var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
        f.service.status(TARGET);
        var replacement = f.replacement("release-2");
        GenerationTransition unfenced = new GenerationTransition(TARGET, replacement.target(), "tenant-1", 0,
            f.inventory.topologyFingerprint(), replacement.inventory().topologyFingerprint(), "generation-2",
            "b".repeat(64), "coolify-readback:old-container-stopped");
        assertThatThrownBy(() -> replacement.service().transition(unfenced))
            .hasMessageContaining("GENERATION_TRANSITION_EVIDENCE_REQUIRED");

        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY));
        GenerationTransition wrongTopology = new GenerationTransition(TARGET, replacement.target(), "tenant-1", 2,
            f.inventory.topologyFingerprint(), "c".repeat(64), "generation-2",
            "b".repeat(64), "coolify-readback:old-container-stopped");
        assertThatThrownBy(() -> replacement.service().transition(wrongTopology))
            .hasMessageContaining("GENERATION_TRANSITION_NEXT_TOPOLOGY_MISMATCH");
    }

    @Test
    void transitionReplayRejectsChangedPayloadAndDoesNotAdoptEnvironmentAlone() {
        var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
        f.service.status(TARGET);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY));
        var replacement = f.replacement("release-2");
        GenerationTransition transition = new GenerationTransition(TARGET, replacement.target(), "tenant-1", 2,
            f.inventory.topologyFingerprint(), replacement.inventory().topologyFingerprint(), "generation-2",
            "b".repeat(64), "coolify-readback:old-container-stopped");

        assertThatThrownBy(() -> replacement.service().status(replacement.target()))
            .hasMessageContaining("PERSISTED_RUNTIME_GENERATION_MISMATCH");
        replacement.service().transition(transition);
        GenerationTransition changed = new GenerationTransition(TARGET, replacement.target(), "tenant-1", 2,
            f.inventory.topologyFingerprint(), replacement.inventory().topologyFingerprint(), "generation-2",
            "d".repeat(64), "coolify-readback:different");
        assertThatThrownBy(() -> replacement.service().transition(changed))
            .hasMessageContaining("GENERATION_TRANSITION_COMMAND_REUSE");
    }

    @Test
    void drainSurvivesRestartAndExactCommandReplaysPersistedResult() {
        var f = new RuntimeRetirementTestFixture();
        Command command = f.command(1, 0, CommandType.BEGIN_DRAIN);
        CommandResult accepted = f.service.command(command);
        var restarted = f.restart();
        assertThat(restarted.status(TARGET).state()).isEqualTo(State.DRAINING);
        assertThat(restarted.command(command).applied()).isEqualTo(accepted.applied());
        assertThat(restarted.command(command).replay()).isTrue();
        assertThatThrownBy(restarted::requireNewSessionAllowed).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsChangedPayloadWrongTargetAndStaleFence() {
        var f = new RuntimeRetirementTestFixture();
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        assertThatThrownBy(() -> f.service.command(new Command(f.binding(1), 0, CommandType.BEGIN_DRAIN, Instant.now(), null)))
            .hasMessageContaining("RETIREMENT_COMMAND_REUSE");
        assertThatThrownBy(() -> f.service.command(f.command(2, 0, CommandType.CANCEL_DRAIN)))
            .hasMessageContaining("STALE_RETIREMENT_FENCE");
        assertThatThrownBy(() -> f.service.status(new Target("other", "staging", "release-1")))
            .hasMessageContaining("RUNTIME_TARGET_SCOPE_MISMATCH");
        var wrongGeneration = new RuntimeTargetRetirementIdentity(true, "dep-123", "staging", "release-2", "tenant-1", true);
        var replacement = new RuntimeTargetRetirementService(wrongGeneration, f.jdbc, f.schema, f.inventory, f.mapper,
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(f.dataSource));
        assertThatThrownBy(() -> replacement.status(wrongGeneration.target())).hasMessageContaining("PERSISTED_RUNTIME_GENERATION_MISMATCH");
    }

    @Test
    void allowsOwnedContinuationButNotNewForeignClosedOrTransientTurns() {
        var f = new RuntimeRetirementTestFixture();
        f.session("owned", "alice", "ACTIVE", "{}");
        f.session("closed", "alice", "CLOSED", "{}");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        TurnPermit permit = f.service.beginTurn(f.caller("alice"), "owned", false);
        f.service.verifyTurn(permit);
        for (String conversation : new String[]{"new", "closed"}) {
            assertThatThrownBy(() -> f.service.beginTurn(f.caller("alice"), conversation, false)).hasMessageContaining("NEW_CONVERSATIONS_BLOCKED");
        }
        assertThatThrownBy(() -> f.service.beginTurn(f.caller("bob"), "owned", false)).hasMessageContaining("NEW_CONVERSATIONS_BLOCKED");
        assertThatThrownBy(() -> f.service.beginTurn(f.caller("alice"), "owned", true)).hasMessageContaining("NEW_CONVERSATIONS_BLOCKED");
        assertThat(f.service.withExistingConversation("closed", "alice", () -> "history-or-explicit-deletion")).isEqualTo("history-or-explicit-deletion");
        assertThatThrownBy(() -> f.service.withExistingConversation("new", "alice", () -> "not reached"))
            .hasMessageContaining("NEW_CONVERSATIONS_BLOCKED");
        f.service.finishTurn(permit);
    }

    @Test
    void cancelRestoresOnlyOnNewFenceAndDoesNotRestoreOldTurnPermits() {
        var f = new RuntimeRetirementTestFixture();
        f.session("owned", "alice", "ACTIVE", "{}");
        TurnPermit late = f.service.beginTurn(f.caller("alice"), "owned", false);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        assertThatThrownBy(() -> f.service.guard(late, null).verifyBeforeAction(null, null)).hasMessageContaining("STALE_RUNTIME_TURN");
        f.service.command(f.command(2, 1, CommandType.CANCEL_DRAIN));
        f.service.requireNewSessionAllowed();
        assertThatThrownBy(() -> f.service.verifyTurn(late)).hasMessageContaining("STALE_RUNTIME_TURN");
        assertThat(f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN)).current().state()).isEqualTo(State.ACTIVE);
        f.service.finishTurn(late);
    }

    @Test
    void cannotCancelAfterExternalAuthorityRevocationOrReclaimBegins() {
        for (CommandType irreversible : new CommandType[]{CommandType.RECORD_AUTHORITY_REVOCATION, CommandType.RECORD_RECLAIM_STARTED}) {
            var f = new RuntimeRetirementTestFixture();
            f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            f.service.command(f.command(2, 1, irreversible));
            assertThatThrownBy(() -> f.restart().command(f.command(3, 2, CommandType.CANCEL_DRAIN)))
                .hasMessageContaining("CANNOT_RESTORE_INGRESS");
            assertThatThrownBy(() -> f.service.beginIngress("/api/private/ingestion/data-sync/batch", true))
                .hasMessageContaining("AUTHORITY_FENCED");
        }
    }

    @Test
    void incompleteInventoryCannotFenceAuthorityAndSnapshotsRequireExplicitCurrentReadback() {
        var f = new RuntimeRetirementTestFixture();
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot snapshot = f.service.snapshot(f.binding(1));
        assertThat(snapshot.reclaimable()).isFalse();
        assertThat(snapshot.backgroundWork().count()).isNull();
        assertThat(f.restart().readSnapshot(snapshot.snapshotId(), f.binding(1))).isEqualTo(snapshot);
        assertThatThrownBy(() -> f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY)))
            .hasMessageContaining("DRAIN_NOT_PROVED_COMPLETE");
        f.service.command(f.command(2, 1, CommandType.CANCEL_DRAIN));
        assertThatThrownBy(() -> f.service.readSnapshot(snapshot.snapshotId(), f.binding(1))).hasMessageContaining("STALE_RETIREMENT_READBACK");
    }

    @Test
    void crashLeavesUnsettledAdmissionsAndNewDocumentIntegrationWorkIsBlocked() {
        var f = new RuntimeRetirementTestFixture();
        TurnPermit accepted = f.service.beginIngress("/api/documents/sources", false);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        var restarted = f.restart();
        assertThat(restarted.snapshot(f.binding(1)).inFlightRequests()).isEqualTo(1);
        assertThatThrownBy(() -> restarted.beginIngress("/api/documents/sources", false)).hasMessageContaining("NEW_WORK_BLOCKED");
        assertThatThrownBy(() -> restarted.beginIngress("/api/internal/integrations/data-sync/batch", false)).hasMessageContaining("NEW_WORK_BLOCKED");
        TurnPermit continuation = restarted.beginIngress("/api/reviews/v1/tasks/{taskId}/decisions", true);
        restarted.finishTurn(continuation);
        restarted.finishTurn(accepted);
        assertThat(restarted.snapshot(f.binding(1)).inFlightRequests()).isZero();
    }

    @Test
    void conversationCreationAndDrainAreSerializedByDurableRowLock() throws Exception {
        var f = new RuntimeRetirementTestFixture();
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var create = executor.submit(() -> f.service.withConversationWrite("owned", "alice", () -> {
                writing.countDown();
                try { if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out"); }
                catch (InterruptedException exception) { throw new IllegalStateException(exception); }
                f.session("owned", "alice", "ACTIVE", "{}");
                return "created";
            }));
            assertThat(writing.await(2, TimeUnit.SECONDS)).isTrue();
            var drain = executor.submit(() -> f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN)));
            release.countDown();
            assertThat(create.get(3, TimeUnit.SECONDS)).isEqualTo("created");
            assertThat(drain.get(3, TimeUnit.SECONDS).current().state()).isEqualTo(State.DRAINING);
            assertThatThrownBy(() -> f.service.withConversationWrite("new", "alice", () -> "not reached"))
                .hasMessageContaining("NEW_CONVERSATIONS_BLOCKED");
            assertThat(f.service.withConversationWrite("owned", "alice", () -> "continued")).isEqualTo("continued");
        }
    }

    @Test
    void appOwnedScheduledWorkerIsActuallyProxiedAndSkippedAfterAuthorityFence() {
        var f = new RuntimeRetirementTestFixture();
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("retirement", f.service);
        var processor = RuntimeRetirementWorkerFencing.runtimeRetirementWorkerPostProcessor(
            beans.getBeanProvider(RuntimeTargetRetirementService.class), f.coverage);
        var worker = new com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryWorker(null);
        var fenced = (com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryWorker) processor.postProcessAfterInitialization(worker, "worker");
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(fenced)).isTrue();
        assertThat(f.coverage.covers("SmartBrainDeliveryWorker.deliver")).isTrue();
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.command(f.command(2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));
        // The null delegate would fail if the stale scheduled worker entered its effect.
        assertThatCode(fenced::deliver).doesNotThrowAnyException();
    }

    @Test
    void springSchedulerRegistersFencedWorkerRatherThanOriginalTarget() {
        var f = new RuntimeRetirementTestFixture();
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.command(f.command(2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "enabled-worker", java.util.Map.of("loomai.smart-brain.enabled", "true")));
            context.registerBean(RuntimeTargetRetirementService.class, () -> f.service);
            context.registerBean(RuntimeRetirementFencingCoverage.class, () -> f.coverage);
            context.registerBean(com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryWorker.class,
                () -> new com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryWorker(null));
            context.register(RuntimeRetirementWorkerFencing.class, ScheduledConfiguration.class);
            context.refresh();
            var scheduler = context.getBean(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class);
            assertThat(scheduler.getScheduledTasks()).hasSize(1);
            var runnable = scheduler.getScheduledTasks().iterator().next().getTask().getRunnable();
            assertThat(org.springframework.aop.support.AopUtils.isAopProxy(
                context.getBean(com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryWorker.class))).isTrue();
            assertThatCode(runnable::run).doesNotThrowAnyException();
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.scheduling.annotation.EnableScheduling
    static class ScheduledConfiguration { }
}

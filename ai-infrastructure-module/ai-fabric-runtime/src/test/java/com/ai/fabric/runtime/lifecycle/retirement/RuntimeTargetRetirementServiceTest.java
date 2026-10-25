package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeRetirementTestFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.web.server.ResponseStatusException;

class RuntimeTargetRetirementServiceTest {
    @Test
    void targetMetadataAloneDoesNotEnableOptionalGatesOrAddColdDeploymentPrerequisites() {
        var f = new RuntimeRetirementTestFixture();
        var disabled = new RuntimeTargetRetirementIdentity(null, "dep-123", "staging", "release-1", "tenant-1", true);
        var service = new RuntimeTargetRetirementService(disabled, f.jdbc, f.schema, f.inventory, f.mapper,
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(f.dataSource));
        assertThat(disabled.enabled()).isFalse();
        service.requireNewSessionAllowed();
        assertThat(service.beginIngress("ordinary-cold-path", false)).isNull();
        assertThat(service.withObservedWorkerBoundary("ordinary-worker", false, () -> "normal-work")).isEqualTo("normal-work");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_gate", Long.class)).isZero();
    }

    @Test
    void sameVersionColdReleaseStartsFreshForActiveDrainingAndFencedOldGatesWithoutClearingDataOrPermits() {
        for (State state : java.util.List.of(State.ACTIVE, State.DRAINING, State.AUTHORITY_REVOKED)) {
            var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
            f.session("retained", "alice", "CLOSED", "{}");
            var oldPermit = f.service.beginIngress("old-unsettled-at-native-stop", false);
            if (state != State.ACTIVE) f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            if (state == State.AUTHORITY_REVOKED) f.service.command(f.command(2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));
            var oldGate = f.jdbc.queryForMap("SELECT * FROM runtime_release_retirement_gate WHERE release_id = 'release-1'");
            var oldHistory = f.jdbc.queryForList("SELECT * FROM runtime_release_retirement_command WHERE release_id = 'release-1'");
            var legacy = f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement");

            // The provider already confirmed the old native writer stopped; the same artifact now has a new release ID.
            var replacement = f.replacement("release-2");
            var current = replacement.service().status(replacement.target());
            assertThat(current.state()).isEqualTo(State.ACTIVE);
            assertThat(current.fenceEpoch()).isZero();
            replacement.service().requireNewSessionAllowed();
            var accepted = replacement.service().beginIngress("new-release-work", false);
            replacement.service().verifyTurn(accepted);
            replacement.service().finishTurn(oldPermit);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_turn WHERE release_id='release-1'", Long.class)).isOne();
            var binding = new Binding(replacement.target(), 1, "retirement-1", FINGERPRINT);
            replacement.service().command(new Command(binding, 0, CommandType.BEGIN_DRAIN, Instant.parse("2030-01-01T00:00:00Z"), null));
            assertThat(replacement.service().snapshot(binding).inFlightRequests()).isOne();
            replacement.service().finishTurn(accepted);
            assertThat(replacement.service().snapshot(binding).localDrainComplete()).isTrue();
            replacement.service().command(new Command(new Binding(replacement.target(), 2, "retirement-1", FINGERPRINT),
                1, CommandType.FENCE_AUTHORITY, null, null));

            assertThat(f.jdbc.queryForMap("SELECT * FROM runtime_release_retirement_gate WHERE release_id = 'release-1'")).isEqualTo(oldGate);
            assertThat(f.jdbc.queryForList("SELECT * FROM runtime_release_retirement_command WHERE release_id = 'release-1'")).isEqualTo(oldHistory);
            assertThat(f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement")).isEqualTo(legacy);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE id = 'retained'", Long.class)).isOne();
        }
    }

    @Test
    void oldReleasePermitsAndSnapshotsCannotBeReadOrSettledByNewRelease() {
        var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
        var oldPermit = f.service.beginIngress("old-work", false);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        var oldSnapshot = f.service.snapshot(f.binding(1));
        var replacement = f.replacement("release-2");
        var binding = new Binding(replacement.target(), 1, "retirement-1", FINGERPRINT);
        replacement.service().command(new Command(binding, 0, CommandType.BEGIN_DRAIN, Instant.parse("2030-01-01T00:00:00Z"), null));
        assertThatThrownBy(() -> replacement.service().verifyTurn(oldPermit)).hasMessageContaining("STALE_RUNTIME_TURN");
        assertThatThrownBy(() -> replacement.service().readSnapshot(oldSnapshot.snapshotId(), binding)).hasMessageContaining("DRAIN_SNAPSHOT_NOT_FOUND");
        assertThatThrownBy(() -> replacement.service().readSnapshot(oldSnapshot.snapshotId(), f.binding(1))).hasMessageContaining("RUNTIME_TARGET_SCOPE_MISMATCH");
        assertThat(replacement.service().snapshot(binding).inFlightRequests()).isZero();
        assertThat(f.service.readSnapshot(oldSnapshot.snapshotId(), f.binding(1))).isEqualTo(oldSnapshot);
    }

    @Test
    void migrationPreservesFullyIdentifiedSameReleaseLegacyFenceAndUnsettledPermits() {
        String topology = new RuntimeRetirementTestFixture(true, true, java.util.List.of()).inventory.topologyFingerprint();
        Instant deadline = Instant.parse("2030-01-01T00:00:00Z");
        for (State state : java.util.List.of(State.DRAINING, State.AUTHORITY_REVOKED, State.RECLAIM_STARTED)) {
            var source = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000", "sa", "");
            var f = new RuntimeRetirementTestFixture(source, true, true, java.util.List.of(), false, jdbc -> {
                jdbc.update("""
                    UPDATE runtime_target_retirement SET tenant_id = 'tenant-1', deployment_id = ?, target_profile_id = ?,
                        release_id = ?, topology_fingerprint = ?, state = ?, fence_epoch = 3, operation_id = 'retirement-1',
                        request_fingerprint = ?, drain_deadline = ?, authority_revoked = ?, reclaim_started = ?
                    """, TARGET.deploymentId(), TARGET.targetProfileId(), TARGET.releaseId(), topology, state.name(), FINGERPRINT,
                    deadline.atOffset(java.time.ZoneOffset.UTC), state != State.DRAINING, state == State.RECLAIM_STARTED);
                jdbc.update("INSERT INTO runtime_target_retirement_turn VALUES ('legacy-unsettled', 0, 'old-work', 'alice', CURRENT_TIMESTAMP)");
            });
            var legacy = f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement");
            var legacyTurns = f.jdbc.queryForList("SELECT * FROM runtime_target_retirement_turn");
            var current = f.restart().status(TARGET);
            assertThat(current.state()).isEqualTo(state);
            assertThat(current.fenceEpoch()).isEqualTo(3);
            assertThat(current.drainDeadline()).isEqualTo(deadline);
            assertThatThrownBy(f.service::requireNewSessionAllowed).hasMessageContaining("TARGET_NEW_SESSIONS_BLOCKED");
            assertThat(f.service.snapshot(f.binding(3)).inFlightRequests()).isOne();
            var replacement = f.replacement("release-2");
            assertThat(replacement.service().status(replacement.target()).state()).isEqualTo(State.ACTIVE);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_turn WHERE release_id = 'release-2'", Long.class)).isZero();
            assertThat(f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement")).isEqualTo(legacy);
            assertThat(f.jdbc.queryForList("SELECT * FROM runtime_target_retirement_turn")).isEqualTo(legacyTurns);
        }
    }

    @Test
    void fullyIdentifiedLegacyFenceWithMissingTopologyFailsClosedOnlyForSameRelease() {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000", "sa", "");
        var f = new RuntimeRetirementTestFixture(source, true, true, java.util.List.of(), false, jdbc -> jdbc.update("""
            UPDATE runtime_target_retirement SET tenant_id = 'tenant-1', deployment_id = ?, target_profile_id = ?,
                release_id = ?, state = 'AUTHORITY_REVOKED', fence_epoch = 2, authority_revoked = TRUE
            """, TARGET.deploymentId(), TARGET.targetProfileId(), TARGET.releaseId()));
        assertThat(f.jdbc.queryForObject("SELECT state FROM runtime_release_retirement_gate WHERE release_id = ?",
            String.class, TARGET.releaseId())).isEqualTo("AUTHORITY_REVOKED");
        assertThatThrownBy(() -> f.service.status(TARGET)).hasMessageContaining("PERSISTED_RUNTIME_TOPOLOGY_MISMATCH");
        var replacement = f.replacement("release-2");
        assertThat(replacement.service().status(replacement.target()).state()).isEqualTo(State.ACTIVE);
        assertThat(f.jdbc.queryForObject("SELECT topology_fingerprint FROM runtime_target_retirement", String.class)).isNull();
    }

    @Test
    void obsoleteGenerationProtocolsReturnGoneWithoutReadingOrMutatingOldSingleton() {
        var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
        var legacy = f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement");
        assertThatThrownBy(() -> f.service.transition(null)).isInstanceOfSatisfying(ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode().value()).isEqualTo(410));
        assertThatThrownBy(() -> f.service.coldMaintenanceTransition(null)).hasMessageContaining("RUNTIME_GENERATION_TRANSITION_RETIRED");
        assertThat(f.jdbc.queryForMap("SELECT * FROM runtime_target_retirement")).isEqualTo(legacy);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_gate", Long.class)).isZero();
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
    @ResourceLock("java.util.TimeZone.default")
    void repeatedLondonHourPreservesDeadlineAdmissionsAndGateOrderingAcrossRestart() {
        TimeZone previous = TimeZone.getDefault();
        Instant before = Instant.parse("2026-10-25T00:58:00Z");
        Instant after = Instant.parse("2026-10-25T01:02:00Z");
        Instant deadline = after.plusSeconds(300);
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(before);
            assertThat(after.atZone(ZoneId.systemDefault()).toLocalTime())
                .isBefore(before.atZone(ZoneId.systemDefault()).toLocalTime());
            var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
            assertThat(f.service.status(TARGET).drainDeadline()).isNull();
            var first = f.service.beginIngress("first-hour", false);
            var command = new Command(f.binding(1), 0, CommandType.BEGIN_DRAIN, deadline, null);
            var draining = f.service.command(command).current();
            assertThat(draining.drainDeadline()).isEqualTo(deadline);
            assertThat(draining.updatedAt()).isEqualTo(before);
            assertThat(f.jdbc.queryForObject("SELECT drain_deadline FROM runtime_release_retirement_gate WHERE release_id = ?",
                OffsetDateTime.class, TARGET.releaseId()).toInstant()).isEqualTo(deadline);

            clock.when(Instant::now).thenReturn(after);
            var restarted = f.restart();
            assertThat(restarted.status(TARGET)).isEqualTo(draining);
            assertThat(restarted.command(command).replay()).isTrue();
            var second = restarted.beginIngress("second-hour", true);
            assertThat(f.jdbc.queryForObject("SELECT created_at FROM runtime_release_retirement_turn WHERE permit_id = ?",
                OffsetDateTime.class, first.permitId()).toInstant()).isEqualTo(before);
            assertThat(f.jdbc.queryForObject("SELECT created_at FROM runtime_release_retirement_turn WHERE permit_id = ?",
                OffsetDateTime.class, second.permitId()).toInstant()).isEqualTo(after).isAfter(before);
            var snapshot = restarted.snapshot(f.binding(1));
            assertThat(snapshot.inFlightRequests()).isEqualTo(2);
            assertThat(f.jdbc.queryForObject("SELECT observed_at FROM runtime_release_retirement_snapshot WHERE snapshot_id = ?",
                OffsetDateTime.class, snapshot.snapshotId()).toInstant()).isEqualTo(after);
            restarted.finishTurn(first);
            restarted.finishTurn(second);
            var fenced = restarted.command(new Command(f.binding(2), 1, CommandType.FENCE_AUTHORITY, null, null)).current();
            assertThat(fenced.state()).isEqualTo(State.AUTHORITY_REVOKED);
            assertThat(fenced.drainDeadline()).isEqualTo(deadline);
            assertThat(fenced.updatedAt()).isEqualTo(after).isAfter(draining.updatedAt());
            assertThat(f.jdbc.queryForList("SELECT created_at FROM runtime_release_retirement_command ORDER BY fence_epoch",
                OffsetDateTime.class)).extracting(OffsetDateTime::toInstant).containsExactly(before, after);
        } finally {
            TimeZone.setDefault(previous);
        }
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
        var replacement = f.replacement("release-2");
        assertThatThrownBy(() -> replacement.service().status(TARGET)).hasMessageContaining("RUNTIME_TARGET_SCOPE_MISMATCH");
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
        assertThatCode(() -> f.service.guard(late, null).verifyBeforeAction(null, null)).doesNotThrowAnyException();
        f.service.command(f.command(2, 1, CommandType.CANCEL_DRAIN));
        f.service.requireNewSessionAllowed();
        assertThatThrownBy(() -> f.service.verifyTurn(late)).hasMessageContaining("STALE_RUNTIME_TURN");
        assertThat(f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN)).current().state()).isEqualTo(State.ACTIVE);
        f.service.finishTurn(late);
    }

    @Test
    void expiredCommandsCannotStartDrainOrFenceButCancellationRemainsAvailable() {
        var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of());
        assertThatThrownBy(() -> f.service.command(new Command(f.binding(1), 0, CommandType.BEGIN_DRAIN, Instant.EPOCH, null)))
            .hasMessageContaining("RETIREMENT_DEADLINE_ELAPSED");
        assertThat(f.service.status(TARGET).state()).isEqualTo(State.ACTIVE);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.jdbc.update("UPDATE runtime_release_retirement_gate SET drain_deadline = ? WHERE release_id = 'release-1'",
            Instant.EPOCH.atOffset(java.time.ZoneOffset.UTC));
        assertThatThrownBy(() -> f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY)))
            .hasMessageContaining("RETIREMENT_DEADLINE_ELAPSED");
        assertThat(f.service.status(TARGET).fenceEpoch()).isEqualTo(1);
        assertThat(f.service.command(f.command(2, 1, CommandType.CANCEL_DRAIN)).current().state()).isEqualTo(State.ACTIVE);
    }

    @Test
    void aLaterDrainDoesNotReadmitPermitsInvalidatedByCancellation() {
        var f = new RuntimeRetirementTestFixture();
        f.session("owned", "alice", "ACTIVE", "{}");
        var old = f.service.beginTurn(f.caller("alice"), "owned", false);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.verifyTurn(old);
        f.service.command(f.command(2, 1, CommandType.CANCEL_DRAIN));
        var current = f.service.beginTurn(f.caller("alice"), "owned", false);
        f.service.command(f.command(3, 2, CommandType.BEGIN_DRAIN));
        assertThatThrownBy(() -> f.service.verifyTurn(old)).hasMessageContaining("STALE_RUNTIME_TURN");
        f.service.verifyTurn(current);
        f.service.finishTurn(old);
        f.service.finishTurn(current);
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

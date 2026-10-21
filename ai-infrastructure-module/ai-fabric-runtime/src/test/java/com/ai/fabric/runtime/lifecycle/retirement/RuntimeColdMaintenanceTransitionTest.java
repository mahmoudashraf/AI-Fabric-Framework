package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeRetirementTestFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.chat.storage.ChatSessionPendingActionStore;
import ai.fabric.chat.storage.DefaultDatabaseChatSessionStorage;
import ai.fabric.intent.action.PendingActionStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;

class RuntimeColdMaintenanceTransitionTest {
    private static final String EXECUTION_UNKNOWN = "DRAIN_PROVIDER_UNSUPPORTED:ai_specialist_execution";

    @Test
    void explicitlyAdoptsColdGenerationWithIncompleteFencingWithoutDeletingDataOrForgingDrainHistory() throws Exception {
        Case c = new Case();
        c.f.session("retained-history", "owner", "CLOSED", "{}");
        c.f.jdbc.execute("CREATE TABLE retained_wallet (id VARCHAR(40), consumed INT)");
        c.f.jdbc.update("INSERT INTO retained_wallet VALUES ('same-wallet', 12)");
        c.f.jdbc.update("INSERT INTO ai_specialist_execution VALUES ('SUCCEEDED')");
        c.f.jdbc.update("INSERT INTO ai_indexing_queue VALUES ('DEAD_LETTER')");
        Snapshot old = c.f.service.snapshot(c.f.binding(1));
        String oldJson = c.f.jdbc.queryForObject("SELECT snapshot_json FROM runtime_target_retirement_snapshot WHERE snapshot_id = ?", String.class, old.snapshotId());

        ColdMaintenanceTransitionResult result = c.service.coldMaintenanceTransition(c.command());

        assertThat(result.evidenceKind()).isEqualTo("PHYSICAL_STOP_COLD_MAINTENANCE_V1");
        assertThat(result.approvalReference()).isEqualTo("review:physical-stop:1");
        assertThat(result.inMemoryStateLossAcknowledged()).isTrue();
        assertThat(result.transition().previous().state()).isEqualTo(State.DRAINING);
        assertThat(result.transition().previous().authorityRevoked()).isFalse();
        assertThat(result.transition().current().target()).isEqualTo(c.next);
        assertThat(result.transition().current().state()).isEqualTo(State.ACTIVE);
        assertThat(result.transition().current().fenceEpoch()).isEqualTo(2);
        assertThat(result.observedInventory().inventoryComplete()).isFalse();
        assertThat(result.observedInventory().workerFencingComplete()).isFalse();
        assertThat(result.observedInventory().localDrainComplete()).isFalse();
        assertThat(result.observedInventory().backgroundWork().count()).isNull();
        assertThat(result.observedInventory().backgroundWork().observations())
            .contains(new Observation("ai_specialist_execution", 0L, false, List.of(EXECUTION_UNKNOWN)));
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_retirement_snapshot", Long.class)).isOne();
        assertThat(c.f.jdbc.queryForObject("SELECT snapshot_json FROM runtime_target_retirement_snapshot WHERE snapshot_id = ?", String.class, old.snapshotId())).isEqualTo(oldJson);
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions", Long.class)).isOne();
        assertThat(c.f.jdbc.queryForObject("SELECT consumed FROM retained_wallet WHERE id = 'same-wallet'", Integer.class)).isEqualTo(12);
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_indexing_queue", Long.class)).isOne();
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_specialist_execution", Long.class)).isOne();
        JsonNode receipt = c.f.mapper.readTree(c.f.jdbc.queryForObject("SELECT result_json FROM runtime_target_generation_transition", String.class));
        assertThat(receipt.path("evidenceKind").asText()).isEqualTo(result.evidenceKind());
        assertThat(receipt.path("transition").path("previous").path("authorityRevoked").asBoolean()).isFalse();
        assertThat(c.service.status(c.next)).isEqualTo(result.transition().current());
    }

    @Test
    void exactReplaySurvivesRestartButChangedApprovalOrNormalCommandCannotReuseTheJournal() {
        Case c = new Case();
        var command = c.command();
        var applied = c.service.coldMaintenanceTransition(command);
        var replay = c.restart().coldMaintenanceTransition(command);
        assertThat(replay.transition().replay()).isTrue();
        assertThat(replay.observedInventory()).isEqualTo(applied.observedInventory());
        assertThat(replay.transition().current()).isEqualTo(applied.transition().current());
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(new ColdMaintenanceTransition(command.transition(),
            command.previousOperationId(), command.previousRequestFingerprint(), "another-approval", true)))
            .hasMessageContaining("GENERATION_TRANSITION_COMMAND_REUSE");
        assertThatThrownBy(() -> c.service.transition(command.transition())).hasMessageContaining("GENERATION_TRANSITION_COMMAND_REUSE");
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_generation_transition", Long.class)).isOne();
    }

    @Test
    void concurrentExactCommandsSerializeIntoOneAdoptionAndOneReplay() throws Exception {
        Case c = new Case();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> c.service.coldMaintenanceTransition(c.command()));
            var second = executor.submit(() -> c.restart().coldMaintenanceTransition(c.command()));
            var a = first.get(5, TimeUnit.SECONDS);
            var b = second.get(5, TimeUnit.SECONDS);
            assertThat(a.transition().replay()).isNotEqualTo(b.transition().replay());
            assertThat(a.transition().current()).isEqualTo(b.transition().current());
        }
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_generation_transition", Long.class)).isOne();
        assertThat(c.service.status(c.next).fenceEpoch()).isEqualTo(2);
    }

    @Test
    void approvalAcknowledgementPreviousBindingAndProviderStopReferenceAreMandatory() {
        Case c = new Case();
        var command = c.command();
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(new ColdMaintenanceTransition(command.transition(),
            command.previousOperationId(), command.previousRequestFingerprint(), command.approvalReference(), false)))
            .hasMessageContaining("COLD_MAINTENANCE_APPROVAL_AND_PREVIOUS_BINDING_REQUIRED");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(new ColdMaintenanceTransition(command.transition(),
            command.previousOperationId(), command.previousRequestFingerprint(), " ", true)))
            .hasMessageContaining("COLD_MAINTENANCE_APPROVAL_AND_PREVIOUS_BINDING_REQUIRED");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(new ColdMaintenanceTransition(command.transition(),
            "different-operation", command.previousRequestFingerprint(), command.approvalReference(), true)))
            .hasMessageContaining("COLD_MAINTENANCE_PREVIOUS_BINDING_MISMATCH");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(new ColdMaintenanceTransition(command.transition(),
            command.previousOperationId(), "c".repeat(64), command.approvalReference(), true)))
            .hasMessageContaining("COLD_MAINTENANCE_PREVIOUS_BINDING_MISMATCH");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.withTransition(t -> new GenerationTransition(
            t.previousTarget(), t.nextTarget(), t.tenantId(), t.expectedFenceEpoch(), t.previousTopologyFingerprint(),
            t.nextTopologyFingerprint(), t.operationId(), t.requestFingerprint(), null))))
            .hasMessageContaining("GENERATION_TRANSITION_EVIDENCE_REQUIRED");
        c.assertUnchanged();
    }

    @Test
    void scopeTopologyAndStaleFenceCannotAdoptTheEnvironment() {
        Case c = new Case();
        assertThatThrownBy(() -> c.service.status(c.next)).hasMessageContaining("PERSISTED_RUNTIME_GENERATION_MISMATCH");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.withTransition(t -> new GenerationTransition(
            new Target("other", TARGET.targetProfileId(), TARGET.releaseId()), t.nextTarget(), t.tenantId(),
            t.expectedFenceEpoch(), t.previousTopologyFingerprint(), t.nextTopologyFingerprint(), t.operationId(),
            t.requestFingerprint(), t.providerStopEvidenceReference())))).hasMessageContaining("GENERATION_TRANSITION_SCOPE_INVALID");
        for (String drift : List.of("fence", "old-topology", "next-topology", "tenant", "old-release", "next-release")) {
            assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.withTransition(t -> new GenerationTransition(
                drift.equals("old-release") ? new Target(TARGET.deploymentId(), TARGET.targetProfileId(), "foreign") : t.previousTarget(),
                drift.equals("next-release") ? TARGET : t.nextTarget(), drift.equals("tenant") ? "other" : t.tenantId(),
                drift.equals("fence") ? 2 : t.expectedFenceEpoch(), drift.equals("old-topology") ? "c".repeat(64) : t.previousTopologyFingerprint(),
                drift.equals("next-topology") ? "d".repeat(64) : t.nextTopologyFingerprint(), t.operationId(),
                t.requestFingerprint(), t.providerStopEvidenceReference())))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        c.assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "AUTHORITY_REVOKED", "RECLAIM_STARTED"})
    void onlyUnrevokedUnreclaimedDrainingGenerationCanUseColdAdoption(String state) {
        Case c = new Case();
        c.f.jdbc.update("UPDATE runtime_target_retirement SET state = ?, authority_revoked = ?, reclaim_started = ?",
            state, !state.equals("ACTIVE"), state.equals("RECLAIM_STARTED"));
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.command()))
            .hasMessageContaining("COLD_MAINTENANCE_PREVIOUS_GENERATION_NOT_DRAINING");
        assertThat(c.f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_generation_transition", Long.class)).isZero();
    }

    @Test
    void outstandingTurnOrLiveChatOrBackgroundWorkRejectsWithoutJournalWrites() {
        Case c = new Case();
        c.f.jdbc.update("INSERT INTO runtime_target_retirement_turn VALUES ('turn',1,'conversation','owner',CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.command())).hasMessageContaining("IN_FLIGHT_WORK_REMAINS");
        c.f.jdbc.update("DELETE FROM runtime_target_retirement_turn");
        c.f.session("active", "owner", "ACTIVE", "{}");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.command())).hasMessageContaining("COLD_MAINTENANCE_WORK_REMAINS_OR_UNOBSERVED");
        c.f.jdbc.update("UPDATE chat_sessions SET status = 'CLOSED'");
        c.f.jdbc.update("INSERT INTO ai_specialist_execution VALUES ('RUNNING')");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.command())).hasMessageContaining("COLD_MAINTENANCE_WORK_REMAINS_OR_UNOBSERVED");
        c.f.jdbc.update("DELETE FROM ai_specialist_execution");
        c.f.jdbc.update("INSERT INTO ai_indexing_queue VALUES ('PROCESSING')");
        assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.command())).hasMessageContaining("COLD_MAINTENANCE_WORK_REMAINS_OR_UNOBSERVED");
        c.assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-unknown", "missing-count", "malformed-metadata", "other-fence"})
    void otherIncompleteUnknownOrMalformedInventoryIsNeverTreatedAsZero(String invalid) {
        Case c = new Case();
        if (invalid.equals("malformed-metadata")) {
            c.f.session("bad", "owner", "CLOSED", "{bad");
            assertThatThrownBy(() -> c.service.coldMaintenanceTransition(c.command())).hasMessageContaining("COLD_MAINTENANCE");
        } else {
            Snapshot actual = c.inventory.inspect(c.f.service.status(TARGET), 0);
            List<Observation> observations = new ArrayList<>(actual.backgroundWork().observations());
            List<String> blockers = new ArrayList<>(actual.blockers());
            if (invalid.equals("other-fence")) blockers.add("QUARTZ_TRIGGER_CREATION_TARGET_EPOCH_FENCE_UNSUPPORTED");
            else {
                observations.add(new Observation("custom-store", invalid.equals("missing-count") ? null : 0L,
                    false, List.of("DRAIN_PROVIDER_UNSUPPORTED:custom-store")));
                blockers.add("DRAIN_PROVIDER_UNSUPPORTED:custom-store");
            }
            Snapshot changed = new Snapshot(actual.snapshotId(), actual.evidenceReference(), actual.target(), actual.observedAt(),
                actual.openConversations(), actual.pendingConfirmations(), actual.humanWork(), new Metric(null, false, observations),
                0, false, false, false, false, actual.deadlineElapsed(), actual.topologyFingerprint(), actual.remoteObligations(), blockers);
            RuntimeTargetDrainInventory inventory = mock(RuntimeTargetDrainInventory.class);
            when(inventory.topologyFingerprint()).thenReturn(c.inventory.topologyFingerprint());
            when(inventory.inspect(any(), anyLong())).thenReturn(changed);
            assertThatThrownBy(() -> c.service(inventory).coldMaintenanceTransition(c.command())).hasMessageContaining("COLD_MAINTENANCE");
        }
        c.assertUnchanged();
    }

    @Test
    void normalTransitionStillRequiresTheOriginalFullDrainFence() {
        Case c = new Case();
        assertThatThrownBy(() -> c.service.transition(c.command().transition()))
            .hasMessageContaining("GENERATION_TRANSITION_PREVIOUS_GENERATION_NOT_FENCED");
        c.assertUnchanged();
    }

    static final class Case {
        final RuntimeRetirementTestFixture f;
        final Target next = new Target(TARGET.deploymentId(), TARGET.targetProfileId(), "release-2");
        final RuntimeTargetRetirementIdentity identity = new RuntimeTargetRetirementIdentity(true, next.deploymentId(),
            next.targetProfileId(), next.releaseId(), "tenant-1", true);
        final RuntimeTargetDrainInventory inventory;
        final RuntimeTargetRetirementService service;

        Case() {
            this(new RuntimeRetirementTestFixture(true, true, List.of()));
        }
        Case(RuntimeRetirementTestFixture fixture) {
            f = fixture;
            f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            f.jdbc.execute("CREATE TABLE ai_specialist_execution (status VARCHAR(40))");
            f.jdbc.execute("CREATE TABLE ai_indexing_queue (status VARCHAR(40))");
            var features = new EnumMap<RuntimeRetirementTopology.Feature, RuntimeRetirementTopology.Presence>(RuntimeRetirementTopology.Feature.class);
            for (var feature : RuntimeRetirementTopology.Feature.values()) features.put(feature, RuntimeRetirementTopology.Presence.ABSENT);
            features.put(RuntimeRetirementTopology.Feature.CHAT_SESSIONS, RuntimeRetirementTopology.Presence.PRESENT);
            features.put(RuntimeRetirementTopology.Feature.SPECIALIST_EXECUTION, RuntimeRetirementTopology.Presence.PRESENT);
            features.put(RuntimeRetirementTopology.Feature.INDEXING, RuntimeRetirementTopology.Presence.PRESENT);
            MockEnvironment env = new MockEnvironment();
            for (String property : List.of("loomai.conversation-handover.enabled", "ai.execution.reviews.enabled",
                "ai.execution.specialist-chains.enabled", "loomai.smart-brain.enabled", "loomai.documents.enabled")) env.withProperty(property, "false");
            try {
                String raw = f.mapper.writeValueAsString(new RuntimeRetirementTopology.Contract(RuntimeRetirementTopology.VERSION,
                    next, "tenant-1", features, List.of(RuntimeRetirementTopology.Dependency.CONNECTOR, RuntimeRetirementTopology.Dependency.HUMAN_INBOX),
                    new RuntimeRetirementTopology.VectorScope("qdrant", "COLLECTION_PREFIX", "reviewed-prefix", List.of("kb"))));
                String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                env.withProperty("loomai.runtime.target-retirement.topology-json", raw).withProperty("loomai.runtime.target-retirement.topology-sha256", sha);
            } catch (Exception e) { throw new IllegalStateException(e); }
            var topology = new RuntimeRetirementTopology(env, f.mapper, identity);
            var beans = new DefaultListableBeanFactory();
            var store = new DefaultDatabaseChatSessionStorage(null);
            beans.registerSingleton("sessions", new RuntimeRetirementStoreFencing.FencedSessions(store,
                beans.getBeanProvider(RuntimeTargetRetirementService.class)));
            beans.registerSingleton("pending", new ChatSessionPendingActionStore(store));
            var stores = mock(RuntimeRetirementStoreVerification.class);
            when(stores.supported(any())).thenReturn(true);
            when(stores.supported(RuntimeRetirementTopology.Feature.SPECIALIST_EXECUTION)).thenReturn(false);
            when(stores.actionDraftsSupported(anyBoolean())).thenReturn(true);
            inventory = new RuntimeTargetDrainInventory(f.jdbc, f.schema, identity, f.mapper,
                beans.getBeanProvider(ChatSessionStorageProvider.class), beans.getBeanProvider(PendingActionStore.class),
                topology, new RuntimeRetirementFencingCoverage(), stores);
            service = restart();
        }
        RuntimeTargetRetirementService service(RuntimeTargetDrainInventory actual) {
            return new RuntimeTargetRetirementService(identity, f.jdbc, f.schema, actual, f.mapper,
                new DataSourceTransactionManager(f.dataSource));
        }
        RuntimeTargetRetirementService restart() { return service(inventory); }
        ColdMaintenanceTransition command() {
            return new ColdMaintenanceTransition(new GenerationTransition(TARGET, next, "tenant-1", 1,
                f.inventory.topologyFingerprint(), inventory.topologyFingerprint(), "cold-transition-2", "b".repeat(64),
                "signed-backend-stop-proof:1"), "retirement-1", FINGERPRINT, "review:physical-stop:1", true);
        }
        ColdMaintenanceTransition withTransition(UnaryOperator<GenerationTransition> mutation) {
            var c = command();
            return new ColdMaintenanceTransition(mutation.apply(c.transition()), c.previousOperationId(),
                c.previousRequestFingerprint(), c.approvalReference(), c.acceptInMemoryStateLoss());
        }
        void assertUnchanged() {
            assertThat(f.service.status(TARGET).state()).isEqualTo(State.DRAINING);
            assertThat(f.service.status(TARGET).fenceEpoch()).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_generation_transition", Long.class)).isZero();
        }
    }
}

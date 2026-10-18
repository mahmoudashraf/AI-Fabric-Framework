package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;

import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.intent.action.PendingActionStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RuntimeTargetDrainInventoryTest {
    @Test
    void countsOpenConversationUnionAndEveryConfirmationIncludingClosedSessions() {
        var f = new RuntimeRetirementTestFixture(true, true,
            List.of(RuntimeRetirementTopology.Dependency.CONNECTOR, RuntimeRetirementTopology.Dependency.HUMAN_INBOX), true);
        f.session("owned", "alice", "ACTIVE", "{\"confirmationStack\":[{\"action\":\"one\"},{\"action\":\"two\"}]}");
        f.session("closed", "alice", "CLOSED", "{\"confirmationStack\":[{\"action\":\"three\"}]}");
        f.jdbc.update("INSERT INTO ai_conversation_control VALUES ('tenant-1','dep-123','owned','alice','AI_ACTIVE',NULL)");
        f.jdbc.update("INSERT INTO ai_conversation_control VALUES ('tenant-1','dep-123','human','bob','HUMAN_ACTIVE','handover-1')");
        f.jdbc.update("INSERT INTO ai_conversation_handover VALUES ('handover-1','ACTIVE')");
        f.jdbc.update("INSERT INTO ai_review_task VALUES ('WAITING_FOR_REVIEW')");
        f.jdbc.update("INSERT INTO loomai_document_work VALUES (FALSE),(TRUE)");
        f.jdbc.update("INSERT INTO loomai_document_manifest VALUES ('INDEX_PARTIAL'),('ACTIVE')");
        f.jdbc.update("INSERT INTO loomai_document_source VALUES ('DELETE_PENDING'),('ACTIVE')");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot snapshot = f.service.snapshot(f.binding(1));
        assertThat(observed(snapshot.openConversations(), "chat_sessions+ai_conversation_control")).isEqualTo(2);
        assertThat(snapshot.pendingConfirmations().count()).isEqualTo(3);
        assertThat(observed(snapshot.humanWork(), "ai_conversation_handover")).isEqualTo(1);
        assertThat(observed(snapshot.humanWork(), "ai_review_task")).isEqualTo(1);
        assertThat(observed(snapshot.backgroundWork(), "loomai_document_work")).isEqualTo(1);
        assertThat(observed(snapshot.backgroundWork(), "loomai_document_manifest")).isEqualTo(1);
        assertThat(observed(snapshot.backgroundWork(), "loomai_document_source")).isEqualTo(1);
        assertThat(snapshot.humanWork().count()).isNull();
        assertThat(snapshot.backgroundWork().count()).isNull();
        assertThat(snapshot.remoteObligations()).extracting(RemoteObligation::dependencyKind).contains("CONNECTOR", "HUMAN_INBOX");
        assertThat(snapshot.reclaimable()).isFalse();
    }

    @Test
    void malformedConfirmationMetadataIsIncompleteRatherThanSilentlyEmpty() {
        for (String metadata : List.of("broken-json", "[]", "{\"confirmationStack\":{}}", "{\"confirmationStack\":[{}]}")) {
            var f = new RuntimeRetirementTestFixture();
            f.session("owned", "alice", "ACTIVE", metadata);
            f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            Snapshot snapshot = f.service.snapshot(f.binding(1));
            assertThat(snapshot.pendingConfirmations().count()).isNull();
            assertThat(snapshot.blockers()).contains("MALFORMED_CONFIRMATION_METADATA");
        }
    }

    @Test
    void missingTablesMissingBeansAndUnattestedStoreNeverBecomeZeroEvidence() {
        var f = new RuntimeRetirementTestFixture();
        f.jdbc.execute("DROP TABLE chat_sessions");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot missingTable = f.service.snapshot(f.binding(1));
        assertThat(missingTable.openConversations().count()).isNull();
        assertThat(missingTable.pendingConfirmations().count()).isNull();
        assertThat(missingTable.blockers()).contains("DRAIN_STORE_SCHEMA_UNAVAILABLE:chat_sessions");

        var beans = new DefaultListableBeanFactory();
        var unsupported = new RuntimeTargetDrainInventory(f.jdbc, f.schema, f.identity, f.mapper,
            beans.getBeanProvider(ChatSessionStorageProvider.class), beans.getBeanProvider(PendingActionStore.class), f.topology, f.coverage, f.stores);
        assertThat(unsupported.inspect(f.service.status(RuntimeRetirementTestFixture.TARGET), 0).pendingConfirmations().complete()).isFalse();

        var shared = new RuntimeRetirementTestFixture(false);
        shared.service.command(shared.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot unscoped = shared.service.snapshot(shared.binding(1));
        assertThat(unscoped.openConversations().count()).isNull();
        assertThat(unscoped.blockers()).contains("TARGET_DEDICATED_STORE_NOT_ATTESTED");
    }

    @Test
    void expiredDeadlineDoesNotCloseDeleteExpireOrDropOutstandingWork() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of(), true);
        f.session("owned", "alice", "ACTIVE", "{\"confirmationStack\":[{\"action\":\"one\"}]}");
        f.service.command(new Command(f.binding(1), 0, CommandType.BEGIN_DRAIN, java.time.Instant.EPOCH, null));
        Snapshot snapshot = f.service.snapshot(f.binding(1));
        assertThat(snapshot.deadlineElapsed()).isTrue();
        assertThat(observed(snapshot.openConversations(), "chat_sessions+ai_conversation_control")).isEqualTo(1);
        assertThat(snapshot.pendingConfirmations().count()).isEqualTo(1);
        assertThat(snapshot.reclaimable()).isFalse();
    }

    private Long observed(Metric metric, String source) {
        return metric.observations().stream().filter(observation -> observation.source().equals(source)).findFirst().orElseThrow().observedCount();
    }

    @Test
    void certifiedConversationalTopologyCanProveLocalDrainWithoutFakingRemoteReadbacks() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of(RuntimeRetirementTopology.Dependency.CONNECTOR));
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot snapshot = f.service.snapshot(f.binding(1));
        assertThat(snapshot.inventoryComplete()).isTrue();
        assertThat(snapshot.workerFencingComplete()).isTrue();
        assertThat(snapshot.localDrainComplete()).isTrue();
        assertThat(snapshot.reclaimable()).isFalse();
        assertThat(snapshot.humanWork().count()).isZero();
        assertThat(snapshot.backgroundWork().count()).isZero();
        assertThat(snapshot.remoteObligations()).hasSize(1);
        assertThatThrownBy(() -> f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY)))
            .hasMessageContaining("AUTHORITATIVE_REMOTE_READBACK_REFERENCE_REQUIRED");
        var fence = new Command(f.binding(2), 1, CommandType.FENCE_AUTHORITY, null, "platform-r5-readback:verified-1");
        assertThat(f.service.command(fence).current().authorityRevoked()).isTrue();
        assertThatThrownBy(() -> f.service.command(f.command(3, 2, CommandType.CANCEL_DRAIN))).hasMessageContaining("CANNOT_RESTORE_INGRESS");
    }

    @Test
    void certifiedAbsentStoresDoNotHideResidualWorkAndNoRemoteTopologyCanBeLocallyReady() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        assertThat(f.service.snapshot(f.binding(1)).reclaimable()).isTrue();
        f.jdbc.update("INSERT INTO loomai_document_work VALUES (FALSE)");
        Snapshot residual = f.service.snapshot(f.binding(1));
        assertThat(residual.backgroundWork().count()).isNull();
        assertThat(residual.blockers()).contains("ATTESTED_ABSENT_CAPABILITY_HAS_PERSISTED_WORK:DOCUMENTS");
        assertThat(observed(residual.backgroundWork(), "loomai_document_work")).isEqualTo(1);
        assertThat(residual.reclaimable()).isFalse();
    }

    @Test
    void malformedExistingTableCannotBeCertifiedAsAnAbsentEmptyStore() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        f.jdbc.execute("DROP TABLE loomai_document_work");
        f.jdbc.execute("CREATE TABLE loomai_document_work (unsupported_state VARCHAR(40))");
        f.jdbc.update("INSERT INTO loomai_document_work VALUES ('UNKNOWN')");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot snapshot = f.service.snapshot(f.binding(1));
        assertThat(snapshot.backgroundWork().count()).isNull();
        assertThat(snapshot.blockers()).contains("DRAIN_STORE_SCHEMA_UNAVAILABLE:loomai_document_work");
        assertThat(snapshot.reclaimable()).isFalse();
    }

    @Test
    void foreignScopedRowsBlockEvenWhenTheyAreClosedAndCertificateClaimsAbsentCapability() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        f.jdbc.update("INSERT INTO ai_conversation_control VALUES ('other-tenant','other-deployment','foreign','alice','CLOSED',NULL)");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot snapshot = f.service.snapshot(f.binding(1));
        assertThat(snapshot.openConversations().count()).isNull();
        assertThat(snapshot.blockers()).contains("FOREIGN_OR_UNBOUND_RUNTIME_STORE_SCOPE:ai_conversation_control");
        assertThat(snapshot.localDrainComplete()).isFalse();
    }

    @Test
    void countsCanonicalConfirmationsAndDraftsWithoutLegacyShadowRows() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of(), true);
        f.canonicalState("owned", "alice", """
            {"confirmationStack":[{"action":"first"},{"action":"second"}],
             "actionDraft":{"action":"collect_contact","params":{"name":"Alice"}}}
            """);
        f.service.command(new Command(f.binding(1), 0, CommandType.BEGIN_DRAIN, java.time.Instant.EPOCH, null));

        Snapshot snapshot = f.service.snapshot(f.binding(1));

        assertThat(snapshot.pendingConfirmations().count()).isEqualTo(2);
        assertThat(observed(snapshot.backgroundWork(), "ai_conversation_orchestration_state.actionDraft")).isEqualTo(1);
        assertThat(snapshot.deadlineElapsed()).isTrue();
        assertThat(snapshot.inventoryComplete()).isTrue();
        assertThat(snapshot.blockers()).contains("DRAIN_WORK_REMAINS");
        assertThat(snapshot.localDrainComplete()).isFalse();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions", Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_orchestration_state", Long.class)).isOne();
        assertThat(f.restart().snapshot(f.binding(1)).pendingConfirmations().count()).isEqualTo(2);
    }

    @Test
    void canonicalEmptyStateIsCompleteButMalformedStateNeverBecomesEmptyEvidence() {
        var empty = new RuntimeRetirementTestFixture(true, true, List.of(), true);
        empty.service.command(empty.command(1, 0, CommandType.BEGIN_DRAIN));
        assertThat(empty.service.snapshot(empty.binding(1)).localDrainComplete()).isTrue();

        for (String state : List.of("broken-json", "[]", "{} {}", "{\"confirmationStack\":{}}",
            "{\"confirmationStack\":[{}]}", "{\"confirmationStack\":[{\"action\":\"a\"}],\"confirmationStack\":[]}",
            "{\"actionDraft\":null}", "{\"actionDraft\":{\"action\":\"a\",\"params\":[]}}")) {
            var f = new RuntimeRetirementTestFixture(true, true, List.of(), true);
            f.canonicalState("owned", "alice", state);
            f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            Snapshot snapshot = f.service.snapshot(f.binding(1));
            assertThat(snapshot.inventoryComplete()).as(state).isFalse();
            assertThat(snapshot.blockers()).as(state).contains("MALFORMED_CANONICAL_ORCHESTRATION_STATE");
            assertThat(snapshot.localDrainComplete()).isFalse();
        }
    }

    @Test
    void missingCanonicalSchemaAndUnsupportedProviderFailClosed() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of(), true);
        f.jdbc.execute("DROP TABLE ai_conversation_orchestration_state");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot missing = f.service.snapshot(f.binding(1));
        assertThat(missing.pendingConfirmations().count()).isNull();
        assertThat(missing.blockers()).contains("DRAIN_STORE_SCHEMA_UNAVAILABLE:ai_conversation_orchestration_state");

        var supported = new RuntimeRetirementTestFixture(true, true, List.of(), true);
        var beans = new DefaultListableBeanFactory();
        var inventory = new RuntimeTargetDrainInventory(supported.jdbc, supported.schema, supported.identity, supported.mapper,
            beans.getBeanProvider(ChatSessionStorageProvider.class), beans.getBeanProvider(PendingActionStore.class),
            supported.topology, supported.coverage, supported.stores);
        Snapshot unsupported = inventory.inspect(supported.service.status(RuntimeRetirementTestFixture.TARGET), 0);
        assertThat(unsupported.pendingConfirmations().count()).isNull();
        assertThat(unsupported.blockers()).contains("CANONICAL_PENDING_CONFIRMATION_STORE_ENUMERATION_UNSUPPORTED");
    }

    @Test
    void canonicalOwnerMismatchInvalidRevisionAndForeignScopeBlockDrain() {
        for (String update : List.of("owner_id = 'mallory'", "state_revision = 0")) {
            var f = new RuntimeRetirementTestFixture(true, true, List.of(), true);
            f.canonicalState("owned", "alice", "{}");
            f.jdbc.update("UPDATE ai_conversation_orchestration_state SET " + update);
            f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            assertThat(f.service.snapshot(f.binding(1)).blockers())
                .contains("CANONICAL_ORCHESTRATION_STATE_OWNERSHIP_OR_REVISION_INVALID");
        }
        var foreign = new RuntimeRetirementTestFixture(true, true, List.of(), true);
        foreign.jdbc.update("INSERT INTO ai_conversation_control VALUES ('other-tenant','other-deployment','foreign','alice','CLOSED',NULL)");
        foreign.jdbc.update("INSERT INTO ai_conversation_orchestration_state VALUES ('other-tenant','other-deployment','foreign','alice',1,'{}',CURRENT_TIMESTAMP)");
        foreign.service.command(foreign.command(1, 0, CommandType.BEGIN_DRAIN));
        assertThat(foreign.service.snapshot(foreign.binding(1)).blockers())
            .contains("FOREIGN_OR_UNBOUND_RUNTIME_STORE_SCOPE:ai_conversation_orchestration_state");

        var orphan = new RuntimeRetirementTestFixture(true, true, List.of(), true);
        orphan.jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        orphan.jdbc.update("INSERT INTO ai_conversation_orchestration_state VALUES ('tenant-1','dep-123','orphan','alice',1,'{}',CURRENT_TIMESTAMP)");
        orphan.service.command(orphan.command(1, 0, CommandType.BEGIN_DRAIN));
        assertThat(orphan.service.snapshot(orphan.binding(1)).blockers())
            .contains("CANONICAL_ORCHESTRATION_STATE_OWNERSHIP_OR_REVISION_INVALID");
    }

    @Test
    void canonicalResidualWorkCannotBeHiddenByAbsentTopologyAndLegacyDraftsAlsoBlock() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        f.canonicalState("owned", "alice", "{\"actionDraft\":{\"action\":\"draft\"}}");
        f.session("legacy", "alice", "CLOSED", "{\"actionDraft\":{\"action\":\"legacy_draft\"}}");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        Snapshot snapshot = f.service.snapshot(f.binding(1));
        assertThat(observed(snapshot.backgroundWork(), "chat_sessions.session_metadata.actionDraft")).isOne();
        assertThat(observed(snapshot.backgroundWork(), "ai_conversation_orchestration_state.actionDraft")).isOne();
        assertThat(snapshot.blockers()).contains("ATTESTED_ABSENT_CAPABILITY_HAS_PERSISTED_WORK:CONVERSATION_HANDOVER");
        assertThat(snapshot.localDrainComplete()).isFalse();
    }
}

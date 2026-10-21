package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeRetirementTopology.Feature.*;

import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.chat.storage.ChatSessionPendingActionStore;
import ai.fabric.chat.storage.ConversationAwarePendingActionStore;
import ai.fabric.chat.storage.DefaultDatabaseChatSessionStorage;
import ai.fabric.chat.util.ActionDraftMetadata;
import ai.fabric.chat.util.ConfirmationStack;
import ai.fabric.intent.action.PendingActionStore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RuntimeTargetDrainInventory {
    private final JdbcTemplate jdbc;
    private final RuntimeRetirementSchema schema;
    private final RuntimeTargetRetirementIdentity identity;
    private final ObjectMapper mapper;
    private final ObjectProvider<ChatSessionStorageProvider> sessions;
    private final ObjectProvider<PendingActionStore> pending;
    private final RuntimeRetirementTopology topology;
    private final RuntimeRetirementFencingCoverage coverage;
    private final RuntimeRetirementStoreVerification stores;

    public RuntimeTargetDrainInventory(JdbcTemplate jdbc, RuntimeRetirementSchema schema,
        RuntimeTargetRetirementIdentity identity, ObjectMapper mapper,
        ObjectProvider<ChatSessionStorageProvider> sessions, ObjectProvider<PendingActionStore> pending,
        RuntimeRetirementTopology topology, RuntimeRetirementFencingCoverage coverage, RuntimeRetirementStoreVerification stores) {
        this.jdbc = jdbc;
        this.schema = schema;
        this.identity = identity;
        this.mapper = mapper;
        this.sessions = sessions;
        this.pending = pending;
        this.topology = topology;
        this.coverage = coverage;
        this.stores = stores;
    }

    public String topologyFingerprint() { return topology.fingerprint(); }

    public Snapshot inspect(Readback target, long inFlightTurns) {
        List<Observation> conversations = new ArrayList<>();
        boolean databaseSessions = databaseSessions();
        boolean chatSchema = schema.supports("chat_sessions", "id", "owner_id", "status", "session_metadata");
        boolean controlSchema = schema.supports("ai_conversation_control", "conversation_id", "owner_id", "control_state");
        if (chatSchema && controlSchema) {
            conversations.add(observe("chat_sessions+ai_conversation_control", """
                SELECT COUNT(*) FROM (
                    SELECT id AS conversation_id, owner_id FROM chat_sessions WHERE status <> 'CLOSED' OR status IS NULL
                    UNION
                    SELECT conversation_id, owner_id FROM ai_conversation_control WHERE control_state <> 'CLOSED' OR control_state IS NULL
                ) open_conversations
                """, databaseSessions));
        } else {
            conversations.add(count(CHAT_SESSIONS, "chat_sessions", "status", "status <> 'CLOSED' OR status IS NULL"));
            conversations.add(count(CONVERSATION_HANDOVER, "ai_conversation_control", "control_state", "control_state <> 'CLOSED' OR control_state IS NULL"));
            if (!topology.absent(CHAT_SESSIONS) && !topology.absent(CONVERSATION_HANDOVER)) {
                conversations.add(unknown("conversation-union", "CONVERSATION_REFERENCE_ENUMERATION_INCOMPLETE"));
            }
        }
        if (!databaseSessions && !topology.absent(CHAT_SESSIONS)) conversations.add(unknown("session-provider", "SESSION_PROVIDER_GLOBAL_ENUMERATION_UNSUPPORTED"));
        if (!topology.absent(CONVERSATION_HANDOVER) && !stores.supported(CONVERSATION_HANDOVER)) {
            conversations.add(unknown("conversation-control-provider", "CONVERSATION_CONTROL_STORE_ENUMERATION_UNSUPPORTED"));
        }

        List<Observation> confirmations = new ArrayList<>();
        List<Observation> drafts = new ArrayList<>();
        PendingActionStore pendingStore = pending.getIfAvailable();
        boolean canonicalPendingStore = pendingStore instanceof ConversationAwarePendingActionStore;
        if (chatSchema && ((databaseSessions && (pendingStore instanceof ChatSessionPendingActionStore || canonicalPendingStore))
            || topology.absent(CHAT_SESSIONS))) {
            MetadataCounts legacy = metadataCounts("SELECT session_metadata FROM chat_sessions", false);
            confirmations.add(observation("chat_sessions.session_metadata.confirmationStack", legacy.confirmations,
                legacy.confirmationsValid, "MALFORMED_CONFIRMATION_METADATA"));
            drafts.add(observation("chat_sessions.session_metadata.actionDraft", legacy.drafts,
                legacy.draftsValid && (topology.absent(CHAT_SESSIONS) || stores.actionDraftsSupported(false)),
                legacy.draftsValid ? "ACTION_DRAFT_STORE_ENUMERATION_UNSUPPORTED" : "MALFORMED_ACTION_DRAFT_METADATA"));
        } else if (!chatSchema && !schema.supports("chat_sessions") && topology.absent(CHAT_SESSIONS)) {
            confirmations.add(absent(CHAT_SESSIONS, "pending-action-store"));
            drafts.add(absent(CHAT_SESSIONS, "action-draft-store"));
        } else {
            confirmations.add(unknown("pending-action-store", "PENDING_CONFIRMATION_STORE_ENUMERATION_UNSUPPORTED"));
            drafts.add(unknown("action-draft-store", "ACTION_DRAFT_STORE_ENUMERATION_UNSUPPORTED"));
        }
        canonicalState(confirmations, drafts, canonicalPendingStore);

        List<Observation> human = new ArrayList<>();
        // One handover work item per persisted handover; orphan control references remain blockers.
        human.add(count(CONVERSATION_HANDOVER, "ai_conversation_handover", "status", "status NOT IN ('CANCELLED','CLOSED','RETURNED_TO_AI') OR status IS NULL"));
        human.add(count(HUMAN_REVIEW, "ai_review_task", "status", "status NOT IN ('APPROVED','REJECTED','CORRECTED','ESCALATED','EXPIRED','FAILED') OR status IS NULL"));
        if (controlSchema && schema.supports("ai_conversation_handover", "handover_id")
            && schema.supports("ai_conversation_control", "active_handover_id")) {
            human.add(observe("orphan-human-control", """
                SELECT COUNT(*) FROM ai_conversation_control c
                WHERE c.control_state IN ('HUMAN_PENDING','HUMAN_ACTIVE','AI_RESUME_PENDING')
                    AND NOT EXISTS (SELECT 1 FROM ai_conversation_handover h WHERE h.handover_id = c.active_handover_id
                        AND h.status NOT IN ('CANCELLED','CLOSED','RETURNED_TO_AI'))
                """, true));
        } else if (topology.absent(CONVERSATION_HANDOVER) && !schema.supports("ai_conversation_control")) human.add(absent(CONVERSATION_HANDOVER, "human-control"));
        else human.add(unknown("human-control", "HUMAN_CONTROL_REFERENCES_INCOMPLETE"));

        List<Observation> background = new ArrayList<>();
        background.addAll(drafts);
        background.add(count(CONVERSATION_HANDOVER, "ai_conversation_ai_interaction", "status", "status NOT IN ('COMPLETED','FAILED') OR status IS NULL"));
        background.add(count(CONVERSATION_HANDOVER, "ai_conversation_handover_dispatch", "status", "status NOT IN ('DELIVERED','SKIPPED') OR status IS NULL"));
        background.add(count(HUMAN_REVIEW, "ai_review_dispatch", "status", "status <> 'ACCEPTED' OR status IS NULL"));
        background.add(count(SPECIALIST_EXECUTION, "ai_specialist_execution", "status", "status NOT IN ('SUCCEEDED','FAILED','CANCELLED','REJECTED','EXPIRED') OR status IS NULL"));
        background.add(count(SPECIALIST_CHAINS, "ai_specialist_chain_execution", "status", "status NOT IN ('COMPLETED','ASKED_USER','HANDED_OFF','INVALID','DENIED','FAILED','DEADLINE_EXCEEDED','CANCELLED') OR status IS NULL"));
        background.add(count(SMART_BRAIN, "loomai_smart_brain_operation", "status", "status NOT IN ('SUCCEEDED','FAILED','CANCELLED','EXPIRED') OR status IS NULL"));
        background.add(count(SMART_BRAIN, "loomai_smart_brain_delivery", "status", "status <> 'DELIVERED' OR status IS NULL"));
        background.add(count(ACTION_WEBHOOK, "runtime_action_webhook_delivery", "status", "status <> 'DELIVERED' OR status IS NULL"));
        background.add(count(INDEXING, "ai_indexing_queue", "status", "status NOT IN ('COMPLETED','SUPERSEDED','DEAD_LETTER') OR status IS NULL"));
        background.add(count(DOCUMENTS, "loomai_document_work", "terminal", "terminal = FALSE OR terminal IS NULL"));
        background.add(count(DOCUMENTS, "loomai_document_manifest", "lifecycle_state",
            "lifecycle_state NOT IN ('ACTIVE','SUPERSEDED','DELETED') OR lifecycle_state IS NULL"));
        background.add(count(DOCUMENTS, "loomai_document_source", "lifecycle_status",
            "lifecycle_status NOT IN ('ACTIVE','DELETED') OR lifecycle_status IS NULL"));
        background.add(count(SCHEDULER, "qrtz_triggers", "trigger_state", "trigger_state <> 'COMPLETE' OR trigger_state IS NULL"));
        background.add(count(SCHEDULER, "qrtz_fired_triggers", "entry_id", "1 = 1"));

        List<String> foreignScopes = foreignScopes();
        for (String foreignScope : foreignScopes) {
            for (List<Observation> group : List.of(conversations, confirmations, human, background)) {
                group.add(unknown("runtime-store-scope", foreignScope));
            }
        }
        Metric open = metric(conversations);
        Metric confirms = metric(confirmations);
        Metric humans = metric(human);
        Metric workers = metric(background);
        List<String> blockers = new ArrayList<>();
        for (Metric metric : List.of(open, confirms, humans, workers)) {
            metric.observations().forEach(observation -> blockers.addAll(observation.blockers()));
            if (metric.complete() && metric.count() > 0) blockers.add("DRAIN_WORK_REMAINS");
        }
        if (inFlightTurns > 0) blockers.add("IN_FLIGHT_OR_UNSETTLED_RUNTIME_TURNS");
        if (!target.ingressBlocked()) blockers.add("TARGET_INGRESS_NOT_DRAINED");
        if (!identity.dedicatedStore()) blockers.add("TARGET_DEDICATED_STORE_NOT_ATTESTED");
        boolean sessionsFenced = sessions.getIfAvailable() instanceof RuntimeRetirementStoreFencing.FencedSessions && databaseSessions;
        List<String> fencingBlockers = topology.fencingBlockers(sessionsFenced, coverage);
        blockers.addAll(fencingBlockers);
        boolean complete = open.complete() && confirms.complete() && humans.complete() && workers.complete();
        boolean fencingComplete = fencingBlockers.isEmpty();
        boolean localDrainComplete = complete && fencingComplete && inFlightTurns == 0 && target.ingressBlocked()
            && open.count() == 0 && confirms.count() == 0 && humans.count() == 0 && workers.count() == 0;
        List<RemoteObligation> remote = topology.remoteObligations(target.target());
        boolean reclaimable = localDrainComplete && remote.isEmpty();
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        return new Snapshot(id, "runtime-target-drain:" + id, target, now, open, confirms, humans, workers,
            inFlightTurns, complete, fencingComplete, localDrainComplete, reclaimable,
            target.drainDeadline() != null && !now.isBefore(target.drainDeadline()), topology.fingerprint(), remote,
            blockers.stream().distinct().toList());
    }

    private boolean databaseSessions() {
        ChatSessionStorageProvider provider = sessions.getIfAvailable();
        if (provider instanceof RuntimeRetirementStoreFencing.FencedSessions fenced) return fenced.databaseBacked();
        return provider != null && AopUtils.getTargetClass(provider) == DefaultDatabaseChatSessionStorage.class;
    }

    private List<String> foreignScopes() {
        List<String> result = new ArrayList<>();
        for (String table : List.of("ai_conversation_control", "ai_conversation_orchestration_state", "ai_conversation_handover", "ai_conversation_ai_interaction",
            "loomai_smart_brain_operation", "loomai_smart_brain_delivery", "loomai_document_source", "loomai_document_manifest")) {
            if (!schema.supports(table, "tenant_id", "deployment_id")) continue;
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE deployment_id <> ? OR tenant_id <> ? OR deployment_id IS NULL OR tenant_id IS NULL",
                Long.class, identity.target().deploymentId(), identity.tenantId());
            if (count == null || count > 0) result.add("FOREIGN_OR_UNBOUND_RUNTIME_STORE_SCOPE:" + table);
        }
        return result;
    }

    private void canonicalState(List<Observation> confirmations, List<Observation> drafts, boolean pendingSupported) {
        String table = "ai_conversation_orchestration_state";
        if (!schema.supports(table, "tenant_id", "deployment_id", "conversation_id", "owner_id", "state_revision", "state_json", "updated_at")) {
            Observation missing = topology.absent(CONVERSATION_HANDOVER) && !schema.supports(table)
                ? absent(CONVERSATION_HANDOVER, table) : unknown(table, "DRAIN_STORE_SCHEMA_UNAVAILABLE:" + table);
            confirmations.add(missing);
            drafts.add(missing);
            return;
        }
        if (!schema.supports("ai_conversation_control", "tenant_id", "deployment_id", "conversation_id", "owner_id")) {
            confirmations.add(unknown(table, "CANONICAL_ORCHESTRATION_STATE_OWNERSHIP_UNAVAILABLE"));
            drafts.add(unknown(table, "CANONICAL_ORCHESTRATION_STATE_OWNERSHIP_UNAVAILABLE"));
            return;
        }
        MetadataCounts canonical = metadataCounts("SELECT state_json FROM " + table, true);
        boolean providerSupported = topology.absent(CONVERSATION_HANDOVER) || stores.supported(CONVERSATION_HANDOVER);
        confirmations.add(observation(table + ".confirmationStack", canonical.confirmations,
            canonical.confirmationsValid && providerSupported && (topology.absent(CONVERSATION_HANDOVER) || pendingSupported),
            canonical.confirmationsValid ? "CANONICAL_PENDING_CONFIRMATION_STORE_ENUMERATION_UNSUPPORTED" : "MALFORMED_CANONICAL_ORCHESTRATION_STATE"));
        drafts.add(observation(table + ".actionDraft", canonical.drafts,
            canonical.draftsValid && providerSupported && (topology.absent(CONVERSATION_HANDOVER) || stores.actionDraftsSupported(true)),
            canonical.draftsValid ? "CANONICAL_ACTION_DRAFT_STORE_ENUMERATION_UNSUPPORTED" : "MALFORMED_CANONICAL_ORCHESTRATION_STATE"));
        Long invalid = jdbc.queryForObject("""
            SELECT COUNT(*) FROM ai_conversation_orchestration_state s
            LEFT JOIN ai_conversation_control c ON c.tenant_id = s.tenant_id
                AND c.deployment_id = s.deployment_id AND c.conversation_id = s.conversation_id
            WHERE c.conversation_id IS NULL OR s.owner_id IS NULL OR TRIM(s.owner_id) = ''
                OR c.owner_id IS NULL OR c.owner_id <> s.owner_id
                OR s.state_revision IS NULL OR s.state_revision < 1 OR s.updated_at IS NULL
            """, Long.class);
        if (invalid == null || invalid > 0) {
            confirmations.add(unknown(table, "CANONICAL_ORCHESTRATION_STATE_OWNERSHIP_OR_REVISION_INVALID"));
            drafts.add(unknown(table, "CANONICAL_ORCHESTRATION_STATE_OWNERSHIP_OR_REVISION_INVALID"));
        }
        if (topology.absent(CONVERSATION_HANDOVER) && (canonical.confirmations > 0 || canonical.drafts > 0)) {
            confirmations.add(unknown(table, "ATTESTED_ABSENT_CAPABILITY_HAS_PERSISTED_WORK:CONVERSATION_HANDOVER"));
            drafts.add(unknown(table, "ATTESTED_ABSENT_CAPABILITY_HAS_PERSISTED_WORK:CONVERSATION_HANDOVER"));
        }
    }

    private MetadataCounts metadataCounts(String sql, boolean canonical) {
        MetadataCounts counts = new MetadataCounts();
        jdbc.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            String raw = rs.getString(1);
            if (raw == null && !canonical) return;
            try {
                if (raw == null || (canonical && raw.length() > 262144)) throw new IllegalArgumentException("Invalid state payload");
                JsonNode metadata = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw);
                if (metadata == null || !metadata.isObject()) throw new IllegalArgumentException("State must be an object");
                JsonNode stack = metadata.get(ConfirmationStack.METADATA_KEY_STACK);
                if (stack != null) {
                    if (!stack.isArray()) counts.confirmationsValid = false;
                    else {
                        counts.confirmations += stack.size();
                        for (JsonNode action : stack) if (!validAction(action)) counts.confirmationsValid = false;
                    }
                }
                JsonNode draft = metadata.get(ActionDraftMetadata.METADATA_KEY_DRAFT);
                if (draft != null) {
                    counts.drafts++;
                    if (!validAction(draft)) counts.draftsValid = false;
                }
            } catch (Exception exception) {
                counts.confirmationsValid = false;
                counts.draftsValid = false;
            }
        });
        return counts;
    }

    private boolean validAction(JsonNode action) {
        return action.isObject() && action.path("action").isTextual() && !action.path("action").asText().isBlank()
            && (!action.has("params") || action.get("params").isObject());
    }

    private static final class MetadataCounts {
        private long confirmations;
        private long drafts;
        private boolean confirmationsValid = true;
        private boolean draftsValid = true;
    }

    private Observation count(RuntimeRetirementTopology.Feature feature, String table, String column, String predicate) {
        if (!schema.supports(table, column)) {
            return topology.absent(feature) && !schema.supports(table)
                ? absent(feature, table) : unknown(table, "DRAIN_STORE_SCHEMA_UNAVAILABLE:" + table);
        }
        Observation observed = observe(table, "SELECT COUNT(*) FROM " + table + " WHERE " + predicate,
            topology.absent(feature) || stores.supported(feature));
        if (topology.absent(feature) && observed.observedCount() > 0) {
            return new Observation(table, observed.observedCount(), false,
                List.of("ATTESTED_ABSENT_CAPABILITY_HAS_PERSISTED_WORK:" + feature));
        }
        return observed;
    }

    private Observation absent(RuntimeRetirementTopology.Feature feature, String source) {
        return observation(source + ":attested-absent:" + feature + ":" + topology.fingerprint(), 0, true, "");
    }

    private Observation observe(String source, String sql, boolean supported) {
        Long count = jdbc.queryForObject(sql, Long.class);
        if (count == null) return unknown(source, "DRAIN_COUNT_UNAVAILABLE:" + source);
        return observation(source, count, supported, "DRAIN_PROVIDER_UNSUPPORTED:" + source);
    }

    private Observation observation(String source, long count, boolean complete, String failure) {
        List<String> blockers = new ArrayList<>();
        if (!complete) blockers.add(failure);
        if (!identity.dedicatedStore()) blockers.add("TARGET_DEDICATED_STORE_NOT_ATTESTED");
        return new Observation(source, count, complete && identity.dedicatedStore(), List.copyOf(blockers));
    }
    private Observation unknown(String source, String code) { return new Observation(source, null, false, List.of(code)); }
    private Metric metric(List<Observation> observations) {
        boolean complete = observations.stream().allMatch(Observation::complete);
        Long total = complete ? observations.stream().mapToLong(Observation::observedCount).sum() : null;
        return new Metric(total, complete, List.copyOf(observations));
    }
}

package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;

import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RuntimeTargetRetirementService {
    private final RuntimeTargetRetirementIdentity identity;
    private final JdbcTemplate jdbc;
    private final RuntimeRetirementSchema schema;
    private final RuntimeTargetDrainInventory inventory;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate independentTransaction;

    public RuntimeTargetRetirementService(RuntimeTargetRetirementIdentity identity, JdbcTemplate jdbc,
        RuntimeRetirementSchema schema, RuntimeTargetDrainInventory inventory, ObjectMapper mapper,
        PlatformTransactionManager transactionManager) {
        this.identity = identity;
        this.jdbc = jdbc;
        this.schema = schema;
        this.inventory = inventory;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CommandResult command(Command command) {
        validateCommand(command);
        return transaction.execute(status -> {
            Readback current = locked();
            List<String[]> receipts = jdbc.query(
                "SELECT payload_digest, result_json FROM runtime_target_retirement_command WHERE fence_epoch = ?",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, command.binding().fenceEpoch());
            String digest = digest(json(command));
            if (!receipts.isEmpty()) {
                if (!receipts.getFirst()[0].equals(digest)) throw conflict("RETIREMENT_COMMAND_REUSE");
                return new CommandResult(read(receipts.getFirst()[1], Readback.class), current, true);
            }
            Binding binding = command.binding();
            if (command.expectedFenceEpoch() != current.fenceEpoch() || binding.fenceEpoch() <= current.fenceEpoch()) {
                throw conflict("STALE_RETIREMENT_FENCE");
            }
            if (current.state() != State.ACTIVE && (!Objects.equals(current.operationId(), binding.operationId())
                || !Objects.equals(current.requestFingerprint(), binding.requestFingerprint()))) {
                throw conflict("RETIREMENT_OPERATION_MISMATCH");
            }
            State next = current.state();
            boolean revoked = current.authorityRevoked();
            boolean reclaim = current.reclaimStarted();
            Instant deadline = current.drainDeadline();
            switch (command.type()) {
                case BEGIN_DRAIN -> {
                    if (revoked || reclaim || (next != State.ACTIVE && next != State.DRAINING)) {
                        throw conflict("RETIREMENT_IRREVERSIBLE");
                    }
                    if (next == State.DRAINING && !Objects.equals(deadline, command.drainDeadline())) {
                        throw conflict("DRAIN_POLICY_CHANGE_REQUIRES_NEW_PLAN");
                    }
                    next = State.DRAINING;
                    deadline = command.drainDeadline();
                }
                case CANCEL_DRAIN -> {
                    if (next != State.DRAINING || revoked || reclaim) throw conflict("RETIREMENT_CANNOT_RESTORE_INGRESS");
                    next = State.ACTIVE;
                }
                case FENCE_AUTHORITY -> {
                    Snapshot snapshot = capture(current);
                    if (next != State.DRAINING || !snapshot.localDrainComplete()) {
                        throw conflict("DRAIN_NOT_PROVED_COMPLETE");
                    }
                    if (!snapshot.remoteObligations().isEmpty() && (command.externalEvidenceReference() == null
                        || command.externalEvidenceReference().isBlank() || command.externalEvidenceReference().length() > 1000)) {
                        throw conflict("AUTHORITATIVE_REMOTE_READBACK_REFERENCE_REQUIRED");
                    }
                    revoked = true;
                    next = State.AUTHORITY_REVOKED;
                }
                case RECORD_AUTHORITY_REVOCATION -> {
                    if (next == State.ACTIVE) throw conflict("DRAIN_NOT_STARTED");
                    revoked = true;
                    if (!reclaim) next = State.AUTHORITY_REVOKED;
                }
                case RECORD_RECLAIM_STARTED -> {
                    if (next == State.ACTIVE) throw conflict("DRAIN_NOT_STARTED");
                    reclaim = true;
                    next = State.RECLAIM_STARTED;
                }
            }
            Instant now = Instant.now();
            jdbc.update("""
                UPDATE runtime_target_retirement SET state = ?, fence_epoch = ?, operation_id = ?,
                    request_fingerprint = ?, drain_deadline = ?, authority_revoked = ?, reclaim_started = ?, updated_at = ?
                WHERE singleton_id = 1
                """, next.name(), binding.fenceEpoch(), binding.operationId(), binding.requestFingerprint(),
                timestamp(deadline), revoked, reclaim, timestamp(now));
            Readback applied = locked();
            jdbc.update("""
                INSERT INTO runtime_target_retirement_command
                    (fence_epoch, operation_id, request_fingerprint, payload_digest, result_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, binding.fenceEpoch(), binding.operationId(), binding.requestFingerprint(), digest, json(applied), timestamp(now));
            return new CommandResult(applied, applied, false);
        });
    }

    /**
     * Adopts a replacement release only after the previous runtime generation has
     * completed its local drain, fenced its authority, and the control plane has
     * recorded an authoritative provider stop/readback. No startup environment
     * value can perform this transition implicitly.
     */
    public GenerationTransitionResult transition(GenerationTransition command) {
        validateTransition(command);
        return transaction.execute(status -> {
            if (!schema.supports("runtime_target_generation_transition", "operation_id", "payload_digest", "result_json")) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RUNTIME_GENERATION_TRANSITION_MIGRATION_REQUIRED");
            }
            String payloadDigest = digest(json(command));
            List<String[]> receipts = jdbc.query("""
                SELECT payload_digest, result_json FROM runtime_target_generation_transition
                WHERE operation_id = ?
                """, (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, command.operationId());
            if (!receipts.isEmpty()) {
                if (!receipts.getFirst()[0].equals(payloadDigest)) throw conflict("GENERATION_TRANSITION_COMMAND_REUSE");
                GenerationTransitionResult persisted = read(receipts.getFirst()[1], GenerationTransitionResult.class);
                return new GenerationTransitionResult(persisted.previous(), persisted.current(),
                    persisted.providerStopEvidenceReference(), persisted.transitionedAt(), true);
            }

            Readback previous = persistedLocked();
            String persistedTopology = jdbc.queryForObject(
                "SELECT topology_fingerprint FROM runtime_target_retirement WHERE singleton_id = 1", String.class);
            if (!command.previousTarget().equals(previous.target()) || !command.tenantId().equals(previous.tenantId())) {
                throw conflict("GENERATION_TRANSITION_PREVIOUS_SCOPE_MISMATCH");
            }
            if (previous.fenceEpoch() != command.expectedFenceEpoch()) {
                throw conflict("GENERATION_TRANSITION_STALE_FENCE");
            }
            if (previous.state() != State.AUTHORITY_REVOKED || !previous.authorityRevoked() || previous.reclaimStarted()) {
                throw conflict("GENERATION_TRANSITION_PREVIOUS_GENERATION_NOT_FENCED");
            }
            if (!command.previousTopologyFingerprint().equals(persistedTopology)) {
                throw conflict("GENERATION_TRANSITION_PREVIOUS_TOPOLOGY_MISMATCH");
            }
            String actualNextTopology = inventory.topologyFingerprint();
            if (!command.nextTopologyFingerprint().equals(actualNextTopology)) {
                throw conflict("GENERATION_TRANSITION_NEXT_TOPOLOGY_MISMATCH");
            }
            Long turns = jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_retirement_turn", Long.class);
            if (turns == null || turns != 0) throw conflict("GENERATION_TRANSITION_IN_FLIGHT_WORK_REMAINS");
            Snapshot drainEvidence = latestSnapshot(previous);
            if (!drainEvidence.localDrainComplete() || !drainEvidence.inventoryComplete()
                || !drainEvidence.workerFencingComplete()) {
                throw conflict("GENERATION_TRANSITION_DRAIN_EVIDENCE_INCOMPLETE");
            }

            long nextFence = Math.addExact(previous.fenceEpoch(), 1L);
            Instant now = Instant.now();
            jdbc.update("""
                UPDATE runtime_target_retirement SET deployment_id = ?, target_profile_id = ?, release_id = ?,
                    tenant_id = ?, topology_fingerprint = ?, state = ?, fence_epoch = ?, operation_id = NULL,
                    request_fingerprint = NULL, drain_deadline = NULL, authority_revoked = FALSE,
                    reclaim_started = FALSE, updated_at = ? WHERE singleton_id = 1
                """, command.nextTarget().deploymentId(), command.nextTarget().targetProfileId(),
                command.nextTarget().releaseId(), command.tenantId(), command.nextTopologyFingerprint(),
                State.ACTIVE.name(), nextFence, timestamp(now));
            Readback current = persistedLocked();
            GenerationTransitionResult result = new GenerationTransitionResult(previous, current,
                command.providerStopEvidenceReference(), now, false);
            jdbc.update("""
                INSERT INTO runtime_target_generation_transition
                    (operation_id, request_fingerprint, payload_digest, previous_deployment_id,
                     previous_target_profile_id, previous_release_id, next_release_id,
                     previous_fence_epoch, next_fence_epoch, previous_topology_fingerprint,
                     next_topology_fingerprint, provider_stop_evidence_reference, result_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, command.operationId(), command.requestFingerprint(), payloadDigest,
                command.previousTarget().deploymentId(), command.previousTarget().targetProfileId(),
                command.previousTarget().releaseId(), command.nextTarget().releaseId(),
                previous.fenceEpoch(), nextFence, command.previousTopologyFingerprint(),
                command.nextTopologyFingerprint(), command.providerStopEvidenceReference(), json(result), timestamp(now));
            return result;
        });
    }

    public Readback status(Target target) {
        identity.requireTarget(target);
        return transaction.execute(status -> locked());
    }

    public Snapshot snapshot(Binding binding) {
        validateBinding(binding);
        return transaction.execute(status -> {
            Readback current = locked();
            requireCurrent(binding, current);
            return capture(current);
        });
    }

    public Snapshot readSnapshot(String snapshotId, Binding binding) {
        validateBinding(binding);
        return transaction.execute(status -> {
            requireCurrent(binding, locked());
            List<String> saved = jdbc.query("""
                SELECT snapshot_json FROM runtime_target_retirement_snapshot
                WHERE snapshot_id = ? AND fence_epoch = ? AND operation_id = ? AND request_fingerprint = ?
                """, (rs, row) -> rs.getString(1), snapshotId, binding.fenceEpoch(), binding.operationId(), binding.requestFingerprint());
            if (saved.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DRAIN_SNAPSHOT_NOT_FOUND");
            return read(saved.getFirst(), Snapshot.class);
        });
    }

    public void requireNewSessionAllowed() {
        if (!identity.enabled()) return;
        transaction.executeWithoutResult(status -> {
            if (locked().state() != State.ACTIVE) throw conflict("TARGET_NEW_SESSIONS_BLOCKED");
        });
    }

    public TurnPermit beginTurn(RuntimeResolvedIdentity caller, String conversationId, boolean transientTurn) {
        if (!identity.enabled()) return null;
        requireCaller(caller);
        return independentTransaction.execute(status -> {
            Readback current = locked();
            requireAuthority(current);
            if (current.state() == State.DRAINING && (transientTurn || !ownedOpenConversation(conversationId, caller.ownerId()))) {
                throw conflict("TARGET_NEW_CONVERSATIONS_BLOCKED");
            }
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                INSERT INTO runtime_target_retirement_turn (permit_id, fence_epoch, conversation_id, owner_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, current.fenceEpoch(), conversationId, caller.ownerId(), timestamp(Instant.now()));
            return new TurnPermit(id, current.fenceEpoch());
        });
    }

    public TurnPermit beginIngress(String route, boolean declaredContinuation) {
        if (!identity.enabled()) return null;
        return independentTransaction.execute(status -> {
            Readback current = locked();
            requireAuthority(current);
            if (current.state() == State.DRAINING && !declaredContinuation) throw conflict("TARGET_NEW_WORK_BLOCKED");
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                INSERT INTO runtime_target_retirement_turn (permit_id, fence_epoch, conversation_id, owner_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, current.fenceEpoch(), route.substring(0, Math.min(route.length(), 160)), "runtime-ingress", timestamp(Instant.now()));
            return new TurnPermit(id, current.fenceEpoch());
        });
    }

    public void finishTurn(TurnPermit permit) {
        if (permit == null) return;
        independentTransaction.executeWithoutResult(status -> jdbc.update(
            "DELETE FROM runtime_target_retirement_turn WHERE permit_id = ? AND fence_epoch = ?",
            permit.permitId(), permit.fenceEpoch()));
    }

    public OrchestrationTurnGuard guard(TurnPermit permit, OrchestrationTurnGuard delegate) {
        if (permit == null) return delegate;
        return new OrchestrationTurnGuard() {
            public void verifyBeforeOrchestration() {
                verifyTurn(permit);
                if (delegate != null) delegate.verifyBeforeOrchestration();
            }
            public void verifyBeforeAction(AIActionMetaData metadata, ActionContext context) {
                verifyTurn(permit);
                if (delegate != null) delegate.verifyBeforeAction(metadata, context);
            }
            public void verifyBeforeResultCommit() {
                verifyTurn(permit);
                if (delegate != null) delegate.verifyBeforeResultCommit();
            }
            public void verifyBeforeResultCommit(OrchestrationResult result) {
                verifyTurn(permit);
                if (delegate != null) delegate.verifyBeforeResultCommit(result);
            }
        };
    }

    public void verifyTurn(TurnPermit permit) {
        independentTransaction.executeWithoutResult(status -> {
            Readback current = locked();
            requireAuthority(current);
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_retirement_turn WHERE permit_id = ? AND fence_epoch = ?",
                Long.class, permit.permitId(), permit.fenceEpoch());
            if (current.fenceEpoch() != permit.fenceEpoch() || count == null || count != 1) throw conflict("STALE_RUNTIME_TURN");
        });
    }

    public <T> T withConversationWrite(String conversationId, String ownerId, Supplier<T> action) {
        return withConversationAccess(conversationId, ownerId, true, action);
    }

    public <T> T withExistingConversation(String conversationId, String ownerId, Supplier<T> action) {
        return withConversationAccess(conversationId, ownerId, false, action);
    }

    private <T> T withConversationAccess(String conversationId, String ownerId, boolean openOnly, Supplier<T> action) {
        if (!identity.enabled()) return action.get();
        return transaction.execute(status -> {
            Readback current = locked();
            requireAuthority(current);
            if (current.state() == State.DRAINING && !ownedConversation(conversationId, ownerId, openOnly)) {
                throw conflict("TARGET_NEW_CONVERSATIONS_BLOCKED");
            }
            return action.get();
        });
    }

    public <T> T withWorkerBoundary(boolean continuation, Supplier<T> action) {
        if (!identity.enabled()) return action.get();
        return transaction.execute(status -> {
            Readback current = locked();
            // The lock spans the actual effect. Authority fencing cannot overtake a late worker.
            if (current.authorityRevoked() || current.reclaimStarted()
                || (current.state() == State.DRAINING && !continuation)) return null;
            return action.get();
        });
    }

    public void requireConversationScope(String deploymentId, String tenantId) {
        if (identity.enabled() && (!identity.target().deploymentId().equals(deploymentId) || !identity.tenantId().equals(tenantId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RUNTIME_CONVERSATION_SCOPE_MISMATCH");
        }
    }

    public boolean authorityFenced() {
        if (!identity.enabled()) return false;
        return transaction.execute(status -> {
            Readback current = locked();
            return current.authorityRevoked() || current.reclaimStarted();
        });
    }

    private void requireCaller(RuntimeResolvedIdentity caller) {
        if (caller == null || caller.getAuthContext() == null || caller.ownerId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RUNTIME_TURN_IDENTITY_REQUIRED");
        }
        requireConversationScope(caller.getAuthContext().getDeploymentId(), caller.getAuthContext().getTenantId());
    }

    private boolean ownedOpenConversation(String conversationId, String ownerId) {
        return ownedConversation(conversationId, ownerId, true);
    }

    private boolean ownedConversation(String conversationId, String ownerId, boolean openOnly) {
        if (schema.supports("ai_conversation_control", "conversation_id", "owner_id", "control_state", "deployment_id", "tenant_id")) {
            Long controls = jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_control WHERE conversation_id = ? AND owner_id = ?"
                + " AND deployment_id = ? AND tenant_id = ?" + (openOnly ? " AND control_state <> 'CLOSED'" : ""),
                Long.class, conversationId, ownerId, identity.target().deploymentId(), identity.tenantId());
            if (controls != null && controls > 0) return true;
        }
        if (!identity.dedicatedStore() || !schema.supports("chat_sessions", "id", "owner_id", "status")) return false;
        Long sessions = jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE id = ? AND owner_id = ?" + (openOnly ? " AND status = 'ACTIVE'" : ""),
            Long.class, conversationId, ownerId);
        return sessions != null && sessions > 0;
    }

    private Snapshot capture(Readback current) {
        Long turns = jdbc.queryForObject("SELECT COUNT(*) FROM runtime_target_retirement_turn", Long.class);
        if (turns == null) throw new IllegalStateException("Runtime turn count is unavailable.");
        Snapshot snapshot = inventory.inspect(current, turns);
        jdbc.update("""
            INSERT INTO runtime_target_retirement_snapshot
                (snapshot_id, fence_epoch, operation_id, request_fingerprint, snapshot_json, observed_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, snapshot.snapshotId(), current.fenceEpoch(), current.operationId(), current.requestFingerprint(),
            json(snapshot), timestamp(snapshot.observedAt()));
        return snapshot;
    }

    private Readback locked() {
        if (!schema.supports("runtime_target_retirement", "singleton_id", "release_id", "fence_epoch")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RUNTIME_RETIREMENT_MIGRATION_REQUIRED");
        }
        Readback current = persistedLocked();
        if (current == null) throw new IllegalStateException("Runtime retirement singleton is missing.");
        if (current.target().deploymentId() == null) {
            jdbc.update("""
                UPDATE runtime_target_retirement SET deployment_id = ?, target_profile_id = ?, release_id = ?, tenant_id = ?, topology_fingerprint = ?
                WHERE singleton_id = 1
                """, identity.target().deploymentId(), identity.target().targetProfileId(), identity.target().releaseId(), identity.tenantId(), inventory.topologyFingerprint());
            return jdbc.queryForObject("SELECT * FROM runtime_target_retirement WHERE singleton_id = 1", this::map);
        }
        if (!identity.target().equals(current.target()) || !identity.tenantId().equals(current.tenantId())) {
            throw conflict("PERSISTED_RUNTIME_GENERATION_MISMATCH");
        }
        String pinned = jdbc.queryForObject("SELECT topology_fingerprint FROM runtime_target_retirement WHERE singleton_id = 1", String.class);
        if (pinned == null) {
            jdbc.update("UPDATE runtime_target_retirement SET topology_fingerprint = ? WHERE singleton_id = 1", inventory.topologyFingerprint());
        } else if (!pinned.equals(inventory.topologyFingerprint())) {
            throw conflict("PERSISTED_RUNTIME_TOPOLOGY_MISMATCH");
        }
        return current;
    }

    private Readback persistedLocked() {
        Readback current = jdbc.queryForObject(
            "SELECT * FROM runtime_target_retirement WHERE singleton_id = 1 FOR UPDATE", this::map);
        if (current == null) throw new IllegalStateException("Runtime retirement singleton is missing.");
        return current;
    }

    private Snapshot latestSnapshot(Readback previous) {
        List<String> snapshots = jdbc.query("""
            SELECT snapshot_json FROM runtime_target_retirement_snapshot
            WHERE fence_epoch <= ? AND operation_id = ? AND request_fingerprint = ?
            ORDER BY observed_at DESC
            """, (rs, row) -> rs.getString(1), previous.fenceEpoch(), previous.operationId(),
            previous.requestFingerprint());
        if (snapshots.isEmpty()) throw conflict("GENERATION_TRANSITION_DRAIN_EVIDENCE_REQUIRED");
        return read(snapshots.getFirst(), Snapshot.class);
    }

    private Readback map(ResultSet rs, int row) throws SQLException {
        return new Readback(new Target(rs.getString("deployment_id"), rs.getString("target_profile_id"), rs.getString("release_id")),
            rs.getString("tenant_id"), State.valueOf(rs.getString("state")), rs.getLong("fence_epoch"),
            rs.getString("operation_id"), rs.getString("request_fingerprint"), instant(rs, "drain_deadline"),
            rs.getBoolean("authority_revoked"), rs.getBoolean("reclaim_started"), instant(rs, "updated_at"));
    }

    private void validateCommand(Command command) {
        if (command == null || command.type() == null) throw badRequest("RETIREMENT_COMMAND_REQUIRED");
        validateBinding(command.binding());
        if (command.expectedFenceEpoch() < 0) throw badRequest("EXPECTED_FENCE_REQUIRED");
        if (command.type() == CommandType.BEGIN_DRAIN && command.drainDeadline() == null) throw badRequest("DRAIN_DEADLINE_REQUIRED");
        if ((command.type() == CommandType.RECORD_AUTHORITY_REVOCATION || command.type() == CommandType.RECORD_RECLAIM_STARTED)
            && (command.externalEvidenceReference() == null || command.externalEvidenceReference().isBlank()
            || command.externalEvidenceReference().length() > 1000)) throw badRequest("EXTERNAL_EVIDENCE_REQUIRED");
    }

    private void validateBinding(Binding binding) {
        if (binding == null) throw badRequest("RETIREMENT_BINDING_REQUIRED");
        identity.requireTarget(binding.target());
        if (binding.fenceEpoch() < 1 || binding.operationId() == null || !binding.operationId().matches("[A-Za-z0-9._:-]{1,160}")
            || binding.requestFingerprint() == null || !binding.requestFingerprint().matches("[a-fA-F0-9]{64}")) {
            throw badRequest("RETIREMENT_FENCE_OPERATION_FINGERPRINT_REQUIRED");
        }
    }

    private void validateTransition(GenerationTransition command) {
        if (command == null) throw badRequest("GENERATION_TRANSITION_REQUIRED");
        identity.requireTarget(command.nextTarget());
        if (command.previousTarget() == null
            || !Objects.equals(command.previousTarget().deploymentId(), command.nextTarget().deploymentId())
            || !Objects.equals(command.previousTarget().targetProfileId(), command.nextTarget().targetProfileId())
            || Objects.equals(command.previousTarget().releaseId(), command.nextTarget().releaseId())
            || !identity.tenantId().equals(command.tenantId())) {
            throw badRequest("GENERATION_TRANSITION_SCOPE_INVALID");
        }
        if (command.expectedFenceEpoch() < 1
            || !sha256(command.previousTopologyFingerprint()) || !sha256(command.nextTopologyFingerprint())
            || command.operationId() == null || !command.operationId().matches("[A-Za-z0-9._:-]{1,160}")
            || !sha256(command.requestFingerprint())
            || command.providerStopEvidenceReference() == null
            || command.providerStopEvidenceReference().isBlank()
            || command.providerStopEvidenceReference().length() > 1000) {
            throw badRequest("GENERATION_TRANSITION_EVIDENCE_REQUIRED");
        }
    }

    private boolean sha256(String value) {
        return value != null && value.matches("[a-fA-F0-9]{64}");
    }

    private void requireCurrent(Binding binding, Readback current) {
        if (binding.fenceEpoch() != current.fenceEpoch() || !binding.operationId().equals(current.operationId())
            || !binding.requestFingerprint().equals(current.requestFingerprint())) throw conflict("STALE_RETIREMENT_READBACK");
    }
    private void requireAuthority(Readback current) {
        if (current.authorityRevoked() || current.reclaimStarted()) throw conflict("RUNTIME_TARGET_AUTHORITY_FENCED");
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Retirement evidence serialization failed.", exception); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("Persisted retirement evidence is invalid.", exception); }
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private ResponseStatusException conflict(String code) { return new ResponseStatusException(HttpStatus.CONFLICT, code); }
    private ResponseStatusException badRequest(String code) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, code); }
}

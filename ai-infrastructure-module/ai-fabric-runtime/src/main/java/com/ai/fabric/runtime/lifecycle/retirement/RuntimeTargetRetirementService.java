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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Release-local optional gates. Core's confirmed native stop owns cross-release single-writer replacement. */
@Service
public class RuntimeTargetRetirementService {
    private static final String SCOPE = "tenant_id = :tenant AND deployment_id = :deployment AND target_profile_id = :profile AND release_id = :release";
    private final RuntimeTargetRetirementIdentity identity;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate scoped;
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
        this.scoped = new NamedParameterJdbcTemplate(jdbc);
        this.schema = schema;
        this.inventory = inventory;
        this.mapper = mapper;
        transaction = new TransactionTemplate(transactionManager);
        independentTransaction = new TransactionTemplate(transactionManager);
        independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CommandResult command(Command command) {
        validateCommand(command);
        return transaction.execute(status -> {
            Readback current = locked();
            var parameters = scope().addValue("epoch", command.binding().fenceEpoch());
            List<String[]> receipts = scoped.query("SELECT payload_digest, result_json FROM runtime_release_retirement_command WHERE "
                + SCOPE + " AND fence_epoch = :epoch", parameters, (rs, row) -> new String[]{rs.getString(1), rs.getString(2)});
            String payloadDigest = digest(json(command));
            if (!receipts.isEmpty()) {
                if (!receipts.getFirst()[0].equals(payloadDigest)) throw conflict("RETIREMENT_COMMAND_REUSE");
                return new CommandResult(read(receipts.getFirst()[1], Readback.class), current, true);
            }
            Binding binding = command.binding();
            if (command.expectedFenceEpoch() != current.fenceEpoch() || binding.fenceEpoch() <= current.fenceEpoch()) {
                throw conflict("STALE_RETIREMENT_FENCE");
            }
            if (current.state() != State.ACTIVE && (!Objects.equals(current.operationId(), binding.operationId())
                || !Objects.equals(current.requestFingerprint(), binding.requestFingerprint()))) throw conflict("RETIREMENT_OPERATION_MISMATCH");
            State next = current.state();
            boolean revoked = current.authorityRevoked();
            boolean reclaim = current.reclaimStarted();
            Instant deadline = current.drainDeadline();
            switch (command.type()) {
                case BEGIN_DRAIN -> {
                    if (revoked || reclaim || next != State.ACTIVE && next != State.DRAINING) throw conflict("RETIREMENT_IRREVERSIBLE");
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
                    if (next != State.DRAINING) throw conflict("DRAIN_NOT_PROVED_COMPLETE");
                    Snapshot snapshot = capture(current);
                    if (!snapshot.localDrainComplete()) throw conflict("DRAIN_NOT_PROVED_COMPLETE");
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
            if (command.type() != CommandType.CANCEL_DRAIN && (deadline == null || !now.isBefore(deadline))) {
                throw conflict("RETIREMENT_DEADLINE_ELAPSED");
            }
            if (command.type() == CommandType.BEGIN_DRAIN && current.state() == State.ACTIVE) {
                scoped.update("UPDATE runtime_release_retirement_gate SET drain_admission_epoch = :floor WHERE " + SCOPE,
                    scope().addValue("floor", current.fenceEpoch()));
            }
            parameters.addValue("state", next.name()).addValue("operation", binding.operationId())
                .addValue("fingerprint", binding.requestFingerprint()).addValue("deadline", timestamp(deadline))
                .addValue("revoked", revoked).addValue("reclaim", reclaim).addValue("now", timestamp(now));
            scoped.update("""
                UPDATE runtime_release_retirement_gate SET state = :state, fence_epoch = :epoch, operation_id = :operation,
                    request_fingerprint = :fingerprint, drain_deadline = :deadline, authority_revoked = :revoked,
                    reclaim_started = :reclaim, updated_at = :now WHERE
                """ + SCOPE, parameters);
            Readback applied = locked();
            scoped.update("""
                INSERT INTO runtime_release_retirement_command
                    (tenant_id, deployment_id, target_profile_id, release_id, fence_epoch, operation_id,
                     request_fingerprint, payload_digest, result_json, created_at)
                VALUES (:tenant, :deployment, :profile, :release, :epoch, :operation, :fingerprint, :digest, :result, :now)
                """, parameters.addValue("digest", payloadDigest).addValue("result", json(applied)));
            return new CommandResult(applied, applied, false);
        });
    }

    public GenerationTransitionResult transition(GenerationTransition command) {
        throw retiredGenerationProtocol();
    }

    public ColdMaintenanceTransitionResult coldMaintenanceTransition(ColdMaintenanceTransition command) {
        throw retiredGenerationProtocol();
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
            List<String> saved = scoped.query("SELECT snapshot_json FROM runtime_release_retirement_snapshot WHERE " + SCOPE
                + " AND snapshot_id = :snapshot AND fence_epoch = :epoch AND operation_id = :operation AND request_fingerprint = :fingerprint",
                scope().addValue("snapshot", snapshotId).addValue("epoch", binding.fenceEpoch())
                    .addValue("operation", binding.operationId()).addValue("fingerprint", binding.requestFingerprint()), (rs, row) -> rs.getString(1));
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
            if (current.state() == State.DRAINING && (transientTurn || !ownedConversation(conversationId, caller.ownerId(), true))) {
                throw conflict("TARGET_NEW_CONVERSATIONS_BLOCKED");
            }
            return permit(current, conversationId, caller.ownerId());
        });
    }

    public TurnPermit beginIngress(String route, boolean declaredContinuation) {
        if (!identity.enabled()) return null;
        return independentTransaction.execute(status -> {
            Readback current = locked();
            requireAuthority(current);
            if (current.state() == State.DRAINING && !declaredContinuation) throw conflict("TARGET_NEW_WORK_BLOCKED");
            return permit(current, route.substring(0, Math.min(route.length(), 160)), "runtime-ingress");
        });
    }

    private TurnPermit permit(Readback current, String conversation, String owner) {
        String id = UUID.randomUUID().toString();
        scoped.update("""
            INSERT INTO runtime_release_retirement_turn
                (permit_id, tenant_id, deployment_id, target_profile_id, release_id, fence_epoch, conversation_id, owner_id, created_at)
            VALUES (:permit, :tenant, :deployment, :profile, :release, :epoch, :conversation, :owner, :now)
            """, scope().addValue("permit", id).addValue("epoch", current.fenceEpoch()).addValue("conversation", conversation)
                .addValue("owner", owner).addValue("now", timestamp(Instant.now())));
        return new TurnPermit(id, current.fenceEpoch());
    }

    public void finishTurn(TurnPermit permit) {
        if (permit == null) return;
        independentTransaction.executeWithoutResult(status -> scoped.update("DELETE FROM runtime_release_retirement_turn WHERE " + SCOPE
            + " AND permit_id = :permit AND fence_epoch = :epoch", scope().addValue("permit", permit.permitId()).addValue("epoch", permit.fenceEpoch())));
    }

    public OrchestrationTurnGuard guard(TurnPermit permit, OrchestrationTurnGuard delegate) {
        if (permit == null) return delegate;
        return new OrchestrationTurnGuard() {
            public void verifyBeforeOrchestration() { verifyTurn(permit); if (delegate != null) delegate.verifyBeforeOrchestration(); }
            public void verifyBeforeAction(AIActionMetaData metadata, ActionContext context) {
                verifyTurn(permit); if (delegate != null) delegate.verifyBeforeAction(metadata, context);
            }
            public void verifyBeforeResultCommit() { verifyTurn(permit); if (delegate != null) delegate.verifyBeforeResultCommit(); }
            public void verifyBeforeResultCommit(OrchestrationResult result) {
                verifyTurn(permit); if (delegate != null) delegate.verifyBeforeResultCommit(result);
            }
        };
    }

    public void verifyTurn(TurnPermit permit) {
        independentTransaction.executeWithoutResult(status -> {
            Readback current = locked();
            requireAuthority(current);
            Long count = scoped.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_turn WHERE " + SCOPE
                + " AND permit_id = :permit AND fence_epoch = :epoch", scope().addValue("permit", permit.permitId()).addValue("epoch", permit.fenceEpoch()), Long.class);
            Long floor = current.state() == State.DRAINING ? scoped.queryForObject(
                "SELECT drain_admission_epoch FROM runtime_release_retirement_gate WHERE " + SCOPE, scope(), Long.class) : null;
            boolean continuation = floor != null && permit.fenceEpoch() >= floor && permit.fenceEpoch() <= current.fenceEpoch();
            if ((!continuation && current.fenceEpoch() != permit.fenceEpoch()) || count == null || count != 1) throw conflict("STALE_RUNTIME_TURN");
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
            if (current.state() == State.DRAINING && !ownedConversation(conversationId, ownerId, openOnly)) throw conflict("TARGET_NEW_CONVERSATIONS_BLOCKED");
            return action.get();
        });
    }

    public <T> T withWorkerBoundary(boolean continuation, Supplier<T> action) {
        if (!identity.enabled()) return action.get();
        return transaction.execute(status -> {
            Readback current = locked();
            if (current.authorityRevoked() || current.reclaimStarted() || current.state() == State.DRAINING && !continuation) return null;
            return action.get();
        });
    }

    /** Holds an attempt, not a database lock, until actual execution exits, including cancelled futures. */
    public <T> T withObservedWorkerBoundary(String boundary, boolean continuation, Supplier<T> action) {
        if (!identity.enabled()) return action.get();
        TurnPermit permit = beginIngress("worker:" + boundary, continuation);
        try { return action.get(); }
        finally { finishTurn(permit); }
    }

    public void requireConversationScope(String deploymentId, String tenantId) {
        if (identity.enabled() && (!identity.target().deploymentId().equals(deploymentId) || !identity.tenantId().equals(tenantId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RUNTIME_CONVERSATION_SCOPE_MISMATCH");
        }
    }
    public boolean authorityFenced() {
        if (!identity.enabled()) return false;
        return transaction.execute(status -> { Readback current = locked(); return current.authorityRevoked() || current.reclaimStarted(); });
    }
    private void requireCaller(RuntimeResolvedIdentity caller) {
        if (caller == null || caller.getAuthContext() == null || caller.ownerId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RUNTIME_TURN_IDENTITY_REQUIRED");
        }
        requireConversationScope(caller.getAuthContext().getDeploymentId(), caller.getAuthContext().getTenantId());
    }
    private boolean ownedConversation(String conversationId, String ownerId, boolean openOnly) {
        if (schema.supports("ai_conversation_control", "conversation_id", "owner_id", "control_state", "deployment_id", "tenant_id")) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_control WHERE conversation_id = ? AND owner_id = ?"
                + " AND deployment_id = ? AND tenant_id = ?" + (openOnly ? " AND control_state <> 'CLOSED'" : ""),
                Long.class, conversationId, ownerId, identity.target().deploymentId(), identity.tenantId());
            if (count != null && count > 0) return true;
        }
        if (!identity.dedicatedStore() || !schema.supports("chat_sessions", "id", "owner_id", "status")) return false;
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE id = ? AND owner_id = ?" + (openOnly ? " AND status = 'ACTIVE'" : ""),
            Long.class, conversationId, ownerId);
        return count != null && count > 0;
    }

    private Snapshot capture(Readback current) {
        Long turns = scoped.queryForObject("SELECT COUNT(*) FROM runtime_release_retirement_turn WHERE " + SCOPE, scope(), Long.class);
        if (turns == null) throw new IllegalStateException("Runtime turn count is unavailable.");
        Snapshot snapshot = inventory.inspect(current, turns);
        scoped.update("""
            INSERT INTO runtime_release_retirement_snapshot
                (snapshot_id, tenant_id, deployment_id, target_profile_id, release_id, fence_epoch, operation_id,
                 request_fingerprint, snapshot_json, observed_at)
            VALUES (:snapshot, :tenant, :deployment, :profile, :release, :epoch, :operation, :fingerprint, :json, :now)
            """, scope().addValue("snapshot", snapshot.snapshotId()).addValue("epoch", current.fenceEpoch())
                .addValue("operation", current.operationId()).addValue("fingerprint", current.requestFingerprint())
                .addValue("json", json(snapshot)).addValue("now", timestamp(snapshot.observedAt())));
        return snapshot;
    }

    private Readback locked() {
        if (!schema.supports("runtime_release_retirement_gate", "tenant_id", "deployment_id", "target_profile_id", "release_id", "fence_epoch", "drain_admission_epoch")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RUNTIME_RELEASE_RETIREMENT_MIGRATION_REQUIRED");
        }
        scoped.update("""
            INSERT INTO runtime_release_retirement_gate
                (tenant_id, deployment_id, target_profile_id, release_id, topology_fingerprint, state, fence_epoch,
                 authority_revoked, reclaim_started, updated_at)
            VALUES (:tenant, :deployment, :profile, :release, :topology, 'ACTIVE', 0, FALSE, FALSE, :now)
            ON CONFLICT DO NOTHING
            """, scope().addValue("topology", inventory.topologyFingerprint()).addValue("now", timestamp(Instant.now())));
        Readback current = scoped.queryForObject("SELECT * FROM runtime_release_retirement_gate WHERE " + SCOPE + " FOR UPDATE", scope(), this::map);
        String topology = scoped.queryForObject("SELECT topology_fingerprint FROM runtime_release_retirement_gate WHERE " + SCOPE, scope(), String.class);
        if (!inventory.topologyFingerprint().equals(topology)) throw conflict("PERSISTED_RUNTIME_TOPOLOGY_MISMATCH");
        return current;
    }
    private MapSqlParameterSource scope() {
        return new MapSqlParameterSource().addValue("tenant", identity.tenantId()).addValue("deployment", identity.target().deploymentId())
            .addValue("profile", identity.target().targetProfileId()).addValue("release", identity.target().releaseId());
    }
    private Readback map(ResultSet rs, int row) throws SQLException {
        return new Readback(new Target(rs.getString("deployment_id"), rs.getString("target_profile_id"), rs.getString("release_id")),
            rs.getString("tenant_id"), State.valueOf(rs.getString("state")), rs.getLong("fence_epoch"), rs.getString("operation_id"),
            rs.getString("request_fingerprint"), instant(rs, "drain_deadline"), rs.getBoolean("authority_revoked"),
            rs.getBoolean("reclaim_started"), instant(rs, "updated_at"));
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
    private void requireCurrent(Binding binding, Readback current) {
        if (binding.fenceEpoch() != current.fenceEpoch() || !binding.operationId().equals(current.operationId())
            || !binding.requestFingerprint().equals(current.requestFingerprint())) throw conflict("STALE_RETIREMENT_READBACK");
    }
    private void requireAuthority(Readback current) {
        if (current.authorityRevoked() || current.reclaimStarted()) throw conflict("RUNTIME_TARGET_AUTHORITY_FENCED");
    }
    private ResponseStatusException retiredGenerationProtocol() {
        return new ResponseStatusException(HttpStatus.GONE, "RUNTIME_GENERATION_TRANSITION_RETIRED_RELEASE_SCOPED_GATES");
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Retirement evidence serialization failed.", failure); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception failure) { throw new IllegalStateException("Persisted retirement evidence is invalid.", failure); }
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
    private OffsetDateTime timestamp(Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
    private ResponseStatusException conflict(String code) { return new ResponseStatusException(HttpStatus.CONFLICT, code); }
    private ResponseStatusException badRequest(String code) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, code); }
}

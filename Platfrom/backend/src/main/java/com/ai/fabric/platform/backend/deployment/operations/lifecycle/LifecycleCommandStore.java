package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;
import static org.springframework.http.HttpStatus.*;

/** Native-command journal. No policy scheduler, capacity or remote I/O belongs in these transactions. */
@Repository
public class LifecycleCommandStore {
    public record Scope(JobRequest job, String versionId, String versionConfigHash, String authorityFingerprint,
        JsonNode nativeSnapshot, boolean reclaimOwnedData) { }
    public record Grant(String id, Scope scope, String state, boolean claimed) { }
    public record Work(Command command, Grant grant, String token) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public LifecycleCommandStore(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.mapper = mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.tx = new TransactionTemplate(manager); this.tx.setTimeout(5);
    }

    public Grant create(Scope scope, String digest, String actor) {
        return transaction(() -> {
            lockDeployment(scope.job().deploymentId());
            var existing = jdbc.query("SELECT * FROM deployment_lifecycle_grants WHERE operation_id=?",
                this::grant, scope.job().operationId());
            if (!existing.isEmpty()) {
                String recorded = jdbc.queryForObject("SELECT request_digest FROM deployment_lifecycle_grants WHERE grant_id=?",
                    String.class, existing.getFirst().id());
                if (!digest.equals(recorded)) throw conflict("LIFECYCLE_OPERATION_CONFLICT");
                return existing.getFirst();
            }
            Instant now = Instant.now();
            jdbc.update("INSERT INTO deployment_lifecycle_grants(grant_id,operation_id,deployment_id,target_profile_id,release_id,"
                + "scope_hash,request_digest,request_json,snapshot_json,actor_id,state,claimed,created_at,updated_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,FALSE,?,?)", scope.job().grantId(), scope.job().operationId(),
                scope.job().deploymentId(), scope.job().targetProfileId(), scope.job().releaseId(), scope.job().scopeHash(),
                digest, json(scope.job()), json(scope), actor, "OPEN", timestamp(now), timestamp(now));
            return new Grant(scope.job().grantId(), scope, "OPEN", false);
        });
    }

    public Grant require(String id) {
        return jdbc.query("SELECT * FROM deployment_lifecycle_grants WHERE grant_id=?", this::grant, id).stream()
            .findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "LIFECYCLE_GRANT_NOT_FOUND"));
    }

    public Grant byOperation(String operationId) {
        return jdbc.query("SELECT * FROM deployment_lifecycle_grants WHERE operation_id=?", this::grant, operationId)
            .stream().findFirst().orElse(null);
    }

    public Receipt accept(Command command) {
        Grant known = require(command.grantId());
        return transaction(() -> {
            lockDeployment(known.scope().job().deploymentId());
            Grant grant = lockedGrant(command.grantId());
            JobRequest job = grant.scope().job();
            if (!job.operationId().equals(command.operationId()) || !job.scopeHash().equals(command.scopeHash()))
                throw conflict("LIFECYCLE_SCOPE_MISMATCH");
            var prior = jdbc.query("SELECT request_digest,receipt_json FROM deployment_lifecycle_commands WHERE command_id=?",
                (rs, row) -> new String[] {rs.getString(1), rs.getString(2)}, command.commandId());
            boolean retry = false;
            if (!prior.isEmpty()) {
                if (!digest(command).equals(prior.getFirst()[0])) throw conflict("LIFECYCLE_COMMAND_CONFLICT");
                Receipt recorded = parse(prior.getFirst()[1], Receipt.class);
                retry = recorded.status() == Status.BLOCKED && "OPEN".equals(grant.state());
                if (!retry) return recorded;
            }
            Integer other = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands WHERE grant_id=? AND phase=?",
                Integer.class, grant.id(), command.phase().name());
            if (!retry && other != null && other > 0) throw conflict("LIFECYCLE_PHASE_COMMAND_EXISTS");
            if (!"OPEN".equals(grant.state())) throw conflict("LIFECYCLE_GRANT_CLOSED");
            if (command.phase() == Phase.CLAIM) {
                if (Instant.now().isBefore(job.dueAt())) throw conflict("LIFECYCLE_NOT_DUE");
                if (!Instant.now().isBefore(job.deadlineAt())) throw conflict("LIFECYCLE_DEADLINE_EXCEEDED");
                Integer busy = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_grants WHERE deployment_id=? "
                    + "AND target_profile_id=? AND claimed=TRUE AND grant_id<>?", Integer.class,
                    job.deploymentId(), job.targetProfileId(), grant.id());
                if (busy != null && busy > 0) throw conflict("TARGET_NATIVE_OPERATION_IN_PROGRESS");
                Integer applies = jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployment_releases WHERE deployment_id=? "
                    + "AND target_profile_id=? AND status IN ('APPLY_REQUESTED','PRE_APPLY_VERIFYING','PROVISIONING','VERIFYING')",
                    Integer.class, job.deploymentId(), job.targetProfileId());
                if (applies != null && applies > 0) throw conflict("RELEASE_APPLY_IN_PROGRESS");
                var latest = jdbc.query("SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND target_profile_id=? "
                    + "ORDER BY created_at DESC,id DESC LIMIT 1", (rs, row) -> rs.getString(1), job.deploymentId(), job.targetProfileId());
                if (latest.isEmpty() || !job.releaseId().equals(latest.getFirst())) throw conflict("LIFECYCLE_RELEASE_SUPERSEDED");
                jdbc.update("UPDATE deployment_lifecycle_grants SET claimed=TRUE,updated_at=? WHERE grant_id=?",
                    timestamp(Instant.now()), grant.id());
            } else if (command.phase() != Phase.CANCEL && !grant.claimed()) {
                throw conflict("LIFECYCLE_CLAIM_REQUIRED");
            }
            requirePredecessors(grant, command.phase());
            Receipt receipt = receipt(command, Status.PENDING, retry ? "EXPLICIT_REOBSERVATION_ACCEPTED" : "COMMAND_ACCEPTED", List.of());
            if (retry) jdbc.update("UPDATE deployment_lifecycle_commands SET receipt_json=?,status='PENDING',updated_at=? WHERE command_id=?",
                json(receipt), timestamp(receipt.observedAt()), command.commandId());
            else jdbc.update("INSERT INTO deployment_lifecycle_commands(command_id,grant_id,operation_id,phase,request_digest,request_json,"
                + "receipt_json,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)", command.commandId(), grant.id(),
                command.operationId(), command.phase().name(), digest(command), json(command), json(receipt),
                receipt.status().name(), timestamp(receipt.observedAt()), timestamp(receipt.observedAt()));
            return receipt;
        });
    }

    /** Known pre-dispatch rejections get a durable receipt, not an ambiguous transport error. */
    public Receipt rejection(Command command, String code) {
        if (!java.util.Set.of("LIFECYCLE_NOT_DUE", "LIFECYCLE_DEADLINE_EXCEEDED", "TARGET_NATIVE_OPERATION_IN_PROGRESS",
            "RELEASE_APPLY_IN_PROGRESS", "LIFECYCLE_RELEASE_SUPERSEDED", "LIFECYCLE_GRANT_CLOSED", "LIFECYCLE_CLAIM_REQUIRED",
            "LIFECYCLE_CLAIM_NOT_VERIFIED", "LIFECYCLE_PHASE_NOT_AUTHORIZED", "DRAIN_NOT_STARTED", "DRAIN_NOT_VERIFIED",
            "DRAIN_NOT_FENCED", "STOP_NOT_VERIFIED", "LIFECYCLE_EFFECT_NOT_VERIFIED", "LIFECYCLE_VERIFICATION_REQUIRED").contains(code))
            throw conflict(code);
        Grant known = require(command.grantId());
        return transaction(() -> {
            lockDeployment(known.scope().job().deploymentId());
            var grant = lockedGrant(command.grantId());
            if (!grant.scope().job().operationId().equals(command.operationId())
                || !grant.scope().job().scopeHash().equals(command.scopeHash())) throw conflict("LIFECYCLE_SCOPE_MISMATCH");
            var old = jdbc.query("SELECT request_digest,receipt_json FROM deployment_lifecycle_commands WHERE command_id=?",
                (rs, row) -> new String[] {rs.getString(1), rs.getString(2)}, command.commandId());
            if (!old.isEmpty()) {
                if (!digest(command).equals(old.getFirst()[0])) throw conflict("LIFECYCLE_COMMAND_CONFLICT");
                Receipt prior = parse(old.getFirst()[1], Receipt.class);
                if (prior.status() != Status.BLOCKED) return prior;
                Receipt denied = receipt(command, Status.BLOCKED, code, List.of(code));
                jdbc.update("UPDATE deployment_lifecycle_commands SET receipt_json=?,updated_at=? WHERE command_id=?",
                    json(denied), timestamp(denied.observedAt()), command.commandId());
                return denied;
            }
            Integer other = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands WHERE grant_id=? AND phase=?",
                Integer.class, grant.id(), command.phase().name());
            if (other != null && other > 0) throw conflict("LIFECYCLE_PHASE_COMMAND_EXISTS");
            Receipt denied = receipt(command, Status.BLOCKED, code, List.of(code));
            jdbc.update("INSERT INTO deployment_lifecycle_commands(command_id,grant_id,operation_id,phase,request_digest,request_json,"
                + "receipt_json,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)", command.commandId(), grant.id(),
                command.operationId(), command.phase().name(), digest(command), json(command), json(denied), Status.BLOCKED.name(),
                timestamp(denied.observedAt()), timestamp(denied.observedAt()));
            return denied;
        });
    }

    public Receipt receipt(String id) {
        return jdbc.query("SELECT receipt_json FROM deployment_lifecycle_commands WHERE command_id=?",
            (rs, row) -> parse(rs.getString(1), Receipt.class), id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "LIFECYCLE_COMMAND_NOT_FOUND"));
    }

    public Work claim(String id) {
        return transaction(() -> {
            var rows = jdbc.query("SELECT * FROM deployment_lifecycle_commands WHERE command_id=? FOR UPDATE", (rs, row) -> {
                OffsetDateTime until = rs.getObject("lease_until", OffsetDateTime.class);
                Instant lease = until == null ? null : until.toInstant();
                return new Object[] {parse(rs.getString("request_json"), Command.class),
                    parse(rs.getString("receipt_json"), Receipt.class), lease};
            }, id);
            if (rows.isEmpty()) return null;
            Object[] row = rows.getFirst(); Receipt receipt = (Receipt) row[1]; Instant lease = (Instant) row[2];
            if (receipt.status() == Status.SUCCEEDED || receipt.status() == Status.FAILED || receipt.status() == Status.BLOCKED
                || lease != null && lease.isAfter(Instant.now())) return null;
            Command command = (Command) row[0]; String token = UUID.randomUUID().toString();
            jdbc.update("UPDATE deployment_lifecycle_commands SET worker_token=?,lease_until=? WHERE command_id=?", token,
                timestamp(Instant.now().plus(Duration.ofMinutes(5))), id);
            return new Work(command, require(command.grantId()), token);
        });
    }

    public void guard(Work work) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands c JOIN deployment_lifecycle_grants g "
            + "ON g.grant_id=c.grant_id WHERE c.command_id=? AND c.worker_token=? AND c.lease_until>? AND g.state='OPEN' "
            + "AND (g.claimed=TRUE OR c.phase='CANCEL')", Integer.class, work.command().commandId(), work.token(), timestamp(Instant.now()));
        if (count == null || count != 1) throw conflict("LIFECYCLE_WORKER_FENCED");
    }

    public boolean beforeEffect(Work work, String effect) {
        return transaction(() -> {
            jdbc.queryForObject("SELECT command_id FROM deployment_lifecycle_commands WHERE command_id=? FOR UPDATE",
                String.class, work.command().commandId());
            guard(work);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id=?",
                Integer.class, work.command().commandId(), effect);
            if (count != null && count > 0) return false;
            if (work.command().phase() != Phase.CANCEL && !Instant.now().isBefore(work.grant().scope().job().deadlineAt()))
                throw conflict("LIFECYCLE_DEADLINE_EXCEEDED");
            jdbc.update("INSERT INTO deployment_lifecycle_effects(command_id,effect_id,state,created_at,updated_at) VALUES(?,?,'INTENT',?,?)",
                work.command().commandId(), effect, timestamp(Instant.now()), timestamp(Instant.now()));
            return true;
        });
    }

    public boolean effectComplete(Work work, String effect) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM deployment_lifecycle_effects "
            + "WHERE command_id=? AND effect_id=? AND state='COMPLETE'", Boolean.class, work.command().commandId(), effect));
    }

    public void completed(Work work, String effect) {
        transaction(() -> {
            guard(work);
            if (jdbc.update("UPDATE deployment_lifecycle_effects SET state='COMPLETE',updated_at=? WHERE command_id=? AND effect_id=?",
                timestamp(Instant.now()), work.command().commandId(), effect) != 1) throw conflict("LIFECYCLE_EFFECT_INTENT_REQUIRED");
            return null;
        });
    }

    public String providerOperation(Work work, String effect) {
        return jdbc.query("SELECT provider_operation_id FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id=?",
            (rs, row) -> rs.getString(1), work.command().commandId(), effect).stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    public void dispatched(Work work, String effect, String providerOperation) {
        if (providerOperation == null || !providerOperation.matches("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,254}"))
            throw conflict("NATIVE_OPERATION_ID_INVALID");
        transaction(() -> {
            guard(work);
            String recorded = providerOperation(work, effect);
            if (recorded != null && !recorded.equals(providerOperation)) throw conflict("NATIVE_OPERATION_ID_CONFLICT");
            if (jdbc.update("UPDATE deployment_lifecycle_effects SET provider_operation_id=?,updated_at=? WHERE command_id=? AND effect_id=?",
                providerOperation, timestamp(Instant.now()), work.command().commandId(), effect) != 1)
                throw conflict("LIFECYCLE_EFFECT_INTENT_REQUIRED");
            return null;
        });
    }

    public void finish(Work work, Status status, String code, List<String> blockers) {
        transaction(() -> {
            boolean closing = status == Status.SUCCEEDED && (work.command().phase() == Phase.RELEASE || work.command().phase() == Phase.CANCEL);
            Status finalStatus = status; String finalCode = code; List<String> finalBlockers = blockers;
            if (closing) {
                // Serialize final release with acceptance of any later native command on this grant.
                lockDeployment(work.grant().scope().job().deploymentId());
                lockedGrant(work.grant().id());
                if (work.command().phase() == Phase.CANCEL && anyEffect(work.grant().id())) {
                    finalStatus = Status.BLOCKED; finalCode = "NATIVE_EFFECT_ALREADY_STARTED"; finalBlockers = List.of(finalCode);
                } else if (unsettledOtherCommand(work.grant().id(), work.command().commandId())) {
                    finalStatus = Status.PENDING; finalCode = "NATIVE_COMMAND_RECONCILIATION_REQUIRED"; finalBlockers = List.of(finalCode);
                }
            }
            Receipt receipt = receipt(work.command(), finalStatus, finalCode, finalBlockers);
            int changed = jdbc.update("UPDATE deployment_lifecycle_commands SET receipt_json=?,status=?,worker_token=NULL,lease_until=NULL,"
                + "updated_at=? WHERE command_id=? AND worker_token=?", json(receipt), finalStatus.name(), timestamp(receipt.observedAt()),
                work.command().commandId(), work.token());
            if (changed != 1) throw conflict("LIFECYCLE_WORKER_FENCED");
            if (closing && finalStatus == Status.SUCCEEDED) {
                jdbc.update("UPDATE deployment_lifecycle_grants SET claimed=FALSE,state=?,updated_at=? WHERE grant_id=?",
                    work.command().phase() == Phase.CANCEL ? "CANCELLED" : "CLOSED", timestamp(Instant.now()), work.grant().id());
            }
            return null;
        });
    }

    public List<String> pending() {
        return jdbc.query("SELECT command_id FROM deployment_lifecycle_commands WHERE status IN ('PENDING','UNKNOWN') "
            + "AND (lease_until IS NULL OR lease_until<?) ORDER BY created_at LIMIT 20", (rs, row) -> rs.getString(1), timestamp(Instant.now()));
    }

    public List<Receipt> receipts(String grantId) {
        return jdbc.query("SELECT receipt_json FROM deployment_lifecycle_commands WHERE grant_id=? ORDER BY created_at LIMIT 32",
            (rs, row) -> parse(rs.getString(1), Receipt.class), grantId);
    }

    public boolean succeeded(String grantId, Phase phase) {
        return jdbc.query("SELECT receipt_json FROM deployment_lifecycle_commands WHERE grant_id=? AND phase=?",
            (rs, row) -> parse(rs.getString(1), Receipt.class), grantId, phase.name()).stream()
            .anyMatch(value -> value.status() == Status.SUCCEEDED);
    }

    public boolean anyEffect(String grantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects e JOIN deployment_lifecycle_commands c "
            + "ON c.command_id=e.command_id WHERE c.grant_id=? AND c.phase IN ('STOP','DELETE','RECOVER','FENCE')", Integer.class, grantId);
        return count != null && count > 0;
    }

    public boolean unsettledOtherCommand(String grantId, String commandId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands WHERE grant_id=? "
            + "AND command_id<>? AND phase IN ('BEGIN_DRAIN','FENCE','STOP','DELETE','RECOVER') "
            + "AND status IN ('PENDING','UNKNOWN')", Integer.class, grantId, commandId);
        return count != null && count > 0;
    }

    private void requirePredecessors(Grant grant, Phase phase) {
        String id = grant.id(); JobRequest job = grant.scope().job();
        if (phase == Phase.CLAIM || phase == Phase.CANCEL) return;
        if (!succeeded(id, Phase.CLAIM)) throw conflict("LIFECYCLE_CLAIM_NOT_VERIFIED");
        switch (phase) {
            case BEGIN_DRAIN, OBSERVE_DRAIN, FENCE -> {
                if (job.mode() != Mode.GRACEFUL || !(job.goal() == Goal.STOP || job.goal() == Goal.RETIRE))
                    throw conflict("LIFECYCLE_PHASE_NOT_AUTHORIZED");
                if (phase != Phase.BEGIN_DRAIN && !succeeded(id, Phase.BEGIN_DRAIN)) throw conflict("DRAIN_NOT_STARTED");
                if (phase == Phase.FENCE && !succeeded(id, Phase.OBSERVE_DRAIN)) throw conflict("DRAIN_NOT_VERIFIED");
            }
            case STOP -> {
                if (!(job.goal() == Goal.STOP || job.goal() == Goal.RETIRE)) throw conflict("LIFECYCLE_PHASE_NOT_AUTHORIZED");
                if (job.mode() == Mode.GRACEFUL && !succeeded(id, Phase.FENCE)) throw conflict("DRAIN_NOT_FENCED");
            }
            case DELETE -> {
                if (job.goal() != Goal.RETIRE || !succeeded(id, Phase.STOP)) throw conflict("STOP_NOT_VERIFIED");
            }
            case RECOVER -> { if (job.goal() != Goal.RECOVER) throw conflict("LIFECYCLE_PHASE_NOT_AUTHORIZED"); }
            case OBSERVE -> { if (job.goal() != Goal.RECONCILE) throw conflict("LIFECYCLE_PHASE_NOT_AUTHORIZED"); }
            case VERIFY -> {
                Phase required = switch (job.goal()) { case STOP -> Phase.STOP; case RETIRE -> Phase.DELETE;
                    case RECOVER -> Phase.RECOVER; case RECONCILE -> Phase.OBSERVE; };
                if (!succeeded(id, required)) throw conflict("LIFECYCLE_EFFECT_NOT_VERIFIED");
            }
            case RELEASE -> {
                if (!succeeded(id, job.goal() == Goal.RECONCILE ? Phase.OBSERVE : Phase.VERIFY))
                    throw conflict("LIFECYCLE_VERIFICATION_REQUIRED");
            }
            default -> { }
        }
    }

    private Grant lockedGrant(String id) {
        return jdbc.query("SELECT * FROM deployment_lifecycle_grants WHERE grant_id=? FOR UPDATE", this::grant, id)
            .stream().findFirst().orElseThrow(() -> conflict("LIFECYCLE_GRANT_NOT_FOUND"));
    }
    private Grant grant(ResultSet rs, int row) throws SQLException {
        return new Grant(rs.getString("grant_id"), parse(rs.getString("snapshot_json"), Scope.class),
            rs.getString("state"), rs.getBoolean("claimed"));
    }
    private void lockDeployment(String id) {
        jdbc.queryForObject("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE", String.class, id);
    }
    public <T> T transaction(Supplier<T> body) { return tx.execute(status -> body.get()); }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("LIFECYCLE_SERIALIZATION_FAILED"); }
    }
    public <T> T parse(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("LIFECYCLE_STORED_CONTRACT_INVALID"); }
    }
    public String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("LIFECYCLE_HASH_FAILED"); }
    }
    private Receipt receipt(Command command, Status status, String code, List<String> blockers) {
        return new Receipt("1", command.commandId(), command.operationId(), command.grantId(), command.phase(), status,
            code, Instant.now(), List.copyOf(blockers));
    }
    private static OffsetDateTime timestamp(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static ResponseStatusException conflict(String code) { return new ResponseStatusException(CONFLICT, code); }
}

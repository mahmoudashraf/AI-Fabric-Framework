package com.loomai.operations.lifecycle;

import static com.loomai.operations.lifecycle.LifecycleContracts.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.operations.persistence.OperationsRepository;
import com.loomai.operations.service.OperationsException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LifecycleStore {
    public enum JobState { SCHEDULED, RUNNING, BLOCKED, UNKNOWN, FAILED, SUCCEEDED, CANCEL_PENDING, CANCELLED }
    public record JobView(JobRequest request, JobState state, Phase phase, boolean irreversible,
                          boolean cancelRequested, String code, Command command, Receipt receipt, Instant createdAt, Instant updatedAt) { }
    public record JobPage(List<JobView> jobs, Long nextOffset) { }
    public record Event(long sequence, String eventId, String operationId, String type, JobState state,
                        Phase phase, String code, Instant occurredAt) { }
    public record EventPage(List<Event> events, Long nextCursor) { }
    record Job(JobRequest request, JobState state, int step, boolean irreversible, boolean cancelRequested,
               String code, String leaseToken, Instant leaseUntil, Instant createdAt, Instant updatedAt) { }
    record Work(Command command, String delivery, boolean observationPending, Receipt receipt) { }
    record Lease(Command command, JobRequest request, boolean submit, String token) { }
    record Delivery(Event event, String token) { }

    private final JdbcTemplate jdbc;
    private final OperationsRepository transactions;
    private final ObjectMapper mapper;
    private final LifecycleProperties properties;
    private final Clock clock;

    public LifecycleStore(JdbcTemplate jdbc, OperationsRepository transactions, ObjectMapper mapper,
                          LifecycleProperties properties, Clock clock) {
        this.jdbc = jdbc; this.transactions = transactions; this.mapper = mapper;
        this.properties = properties; this.clock = clock;
    }

    public JobView register(JobRequest request, Runnable validateNew) {
        return transactions.locked(() -> {
            var existing = find(request.operationId());
            if (existing.isPresent()) {
                String digest = jdbc.queryForObject("SELECT request_digest FROM deployment_lifecycle_job WHERE operation_id=?",
                    String.class, request.operationId());
                if (!transactions.digest(request).equals(digest)) throw conflict("LIFECYCLE_JOB_CONFLICT");
                return view(existing.get());
            }
            validateNew.run();
            if (!jdbc.queryForList("SELECT operation_id FROM deployment_lifecycle_job WHERE grant_id=?", String.class, request.grantId()).isEmpty()) {
                throw conflict("LIFECYCLE_GRANT_REUSE");
            }
            var targets = jdbc.queryForList("SELECT * FROM deployment_lifecycle_target WHERE deployment_id=? AND target_profile_id=?",
                request.deploymentId(), request.targetProfileId());
            if (!targets.isEmpty()) {
                var target = targets.getFirst();
                if (!request.customerId().equals(target.get("customer_id")) || !request.tenantId().equals(target.get("tenant_id"))) {
                    throw conflict("LIFECYCLE_TARGET_OWNER_CONFLICT");
                }
                if (target.get("operation_id") != null) throw conflict("LIFECYCLE_TARGET_BUSY");
            }
            Instant now = clock.instant();
            jdbc.update("""
                INSERT INTO deployment_lifecycle_job
                (operation_id,grant_id,request_digest,request_json,customer_id,tenant_id,deployment_id,target_profile_id,
                 state,step_index,code,due_at,deadline_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,'SCHEDULED',0,'JOB_REGISTERED',?,?,?,?)
                """, request.operationId(), request.grantId(), transactions.digest(request), write(request), request.customerId(), request.tenantId(),
                request.deploymentId(), request.targetProfileId(), time(request.dueAt()), time(request.deadlineAt()), time(now), time(now));
            if (targets.isEmpty()) jdbc.update("INSERT INTO deployment_lifecycle_target VALUES (?,?,?,?,?)",
                request.deploymentId(), request.targetProfileId(), request.customerId(), request.tenantId(), request.operationId());
            else jdbc.update("UPDATE deployment_lifecycle_target SET operation_id=? WHERE deployment_id=? AND target_profile_id=?",
                request.operationId(), request.deploymentId(), request.targetProfileId());
            Job job = require(request.operationId());
            event(job, "JOB_REGISTERED", Phase.CLAIM, "JOB_REGISTERED");
            return view(job);
        });
    }

    public JobView status(String operationId) { return view(require(operationId)); }

    public JobPage list(long offset, int limit) {
        bounds(offset); int size = size(limit);
        var rows = jdbc.query("SELECT * FROM deployment_lifecycle_job ORDER BY created_at,operation_id LIMIT ? OFFSET ?",
            this::mapJob, size + 1, offset);
        return new JobPage(rows.stream().limit(size).map(this::view).toList(), rows.size() > size ? offset + size : null);
    }

    public EventPage events(String operationId, long after, int limit) {
        require(operationId);
        if (after < 0) throw invalid("LIFECYCLE_CURSOR_INVALID");
        int size = size(limit);
        var rows = jdbc.query("SELECT * FROM deployment_lifecycle_event WHERE operation_id=? AND sequence_id>? ORDER BY sequence_id LIMIT ?",
            this::mapEvent, operationId, after, size + 1);
        List<Event> page = rows.stream().limit(size).toList();
        return new EventPage(page, rows.size() > size ? page.getLast().sequence() : null);
    }

    public void requestCancel(String operationId) {
        transactions.locked(() -> {
            Job job = require(operationId);
            if (job.state() == JobState.CANCELLED) return null;
            if (terminal(job) || job.irreversible()) throw conflict("LIFECYCLE_CANNOT_CANCEL");
            if (!job.cancelRequested()) {
                jdbc.update("UPDATE deployment_lifecycle_job SET cancel_requested=TRUE,state='CANCEL_PENDING',code='CANCEL_REQUESTED',updated_at=? WHERE operation_id=?",
                    time(clock.instant()), operationId);
                event(require(operationId), "CANCEL_REQUESTED", Phase.CANCEL, "CANCEL_REQUESTED");
            }
            return null;
        });
    }

    Optional<Lease> acquire(String operationId, boolean retry) {
        return transactions.locked(() -> {
            Job job = require(operationId); Instant now = clock.instant();
            if (terminal(job) || job.leaseUntil() != null && job.leaseUntil().isAfter(now)) return Optional.empty();
            if (!job.cancelRequested() && job.request().dueAt().isAfter(now)) return Optional.empty();
            if (!retry && (job.state() == JobState.BLOCKED || job.state() == JobState.FAILED)
                && (!job.cancelRequested() || work(operationId, Phase.CANCEL).isPresent())) return Optional.empty();
            emitDue(job, now);
            Phase phase = phase(job);
            Work work = work(operationId, phase).orElse(null);
            boolean unresolved = work != null && "SENT".equals(work.delivery()) && (work.observationPending() || work.receipt() == null
                || work.receipt().status() == Status.PENDING || work.receipt().status() == Status.UNKNOWN);
            // Local/read-only work cannot hold cancellation; uncertain native mutations must settle first.
            if (job.cancelRequested() && (!unresolved || List.of(Phase.CLAIM, Phase.OBSERVE_DRAIN, Phase.OBSERVE).contains(phase))) {
                phase = Phase.CANCEL; work = work(operationId, phase).orElse(null);
            }
            if (work == null || "READY".equals(work.delivery())) {
                if (!now.isBefore(job.request().deadlineAt()) && phase != Phase.CANCEL) {
                    update(job, JobState.BLOCKED, job.step(), "DEADLINE_EXCEEDED");
                    if (!"DEADLINE_EXCEEDED".equals(job.code())) event(require(operationId), "DEADLINE_BLOCKED", phase, "DEADLINE_EXCEEDED");
                    return Optional.empty();
                }
                if (work == null) {
                    var command = new Command("1", "lc-" + transactions.digest(List.of(operationId, job.request().grantId(),
                        job.request().scopeHash(), phase.name())), operationId, job.request().grantId(), job.request().scopeHash(), phase);
                    jdbc.update("INSERT INTO deployment_lifecycle_command (command_id,operation_id,phase,command_json,delivery,receipt_json) VALUES (?,?,?,?, 'READY',NULL)",
                        command.commandId(), operationId, phase.name(), write(command));
                    work = new Work(command, "READY", false, null);
                    event(job, "COMMAND_PREPARED", phase, "COMMAND_PREPARED");
                }
            }
            String token = UUID.randomUUID().toString();
            jdbc.update("UPDATE deployment_lifecycle_job SET lease_token=?,lease_until=?,state=?,updated_at=? WHERE operation_id=?",
                token, time(now.plus(properties.workerLease())), job.cancelRequested() ? "CANCEL_PENDING" : "RUNNING", time(now), operationId);
            boolean blockedReplay = retry && job.state() == JobState.BLOCKED && !work.observationPending() && work.receipt() != null
                && work.receipt().status() == Status.BLOCKED && (phase == Phase.CANCEL || now.isBefore(job.request().deadlineAt()))
                && (!job.cancelRequested() || phase == Phase.CANCEL);
            return Optional.of(new Lease(work.command(), job.request(), "READY".equals(work.delivery()) || blockedReplay, token));
        });
    }

    boolean markSent(Lease lease) {
        return transactions.locked(() -> {
            Job job = owned(lease); if (job == null) return false;
            if (job.cancelRequested() && lease.command().phase() != Phase.CANCEL) {
                releaseLease(job); return false;
            }
            boolean irreversible = job.irreversible() || List.of(Phase.FENCE, Phase.STOP, Phase.DELETE, Phase.RECOVER).contains(lease.command().phase());
            jdbc.update("UPDATE deployment_lifecycle_command SET delivery='SENT',observation_pending=TRUE WHERE command_id=?", lease.command().commandId());
            jdbc.update("UPDATE deployment_lifecycle_job SET irreversible=?,updated_at=? WHERE operation_id=?",
                irreversible, time(clock.instant()), job.request().operationId());
            event(require(job.request().operationId()), "COMMAND_DISPATCHED", lease.command().phase(), "COMMAND_DISPATCHED");
            return true;
        });
    }

    void received(Lease lease, Receipt receipt) {
        transactions.locked(() -> {
            Job job = owned(lease); if (job == null) return null;
            var previous = work(job.request().operationId(), lease.command().phase()).orElseThrow().receipt();
            if (previous != null && receipt.observedAt().isBefore(previous.observedAt())) {
                throw conflict("LIFECYCLE_RECEIPT_REGRESSION");
            }
            jdbc.update("UPDATE deployment_lifecycle_command SET receipt_json=?,observation_pending=FALSE WHERE command_id=?", write(receipt), receipt.commandId());
            JobState state = switch (receipt.status()) {
                case PENDING -> job.cancelRequested() ? JobState.CANCEL_PENDING : JobState.RUNNING;
                case BLOCKED -> JobState.BLOCKED;
                case UNKNOWN -> JobState.UNKNOWN;
                case FAILED -> JobState.FAILED;
                case SUCCEEDED -> receipt.phase() == Phase.CANCEL ? JobState.CANCELLED
                    : receipt.phase() == Phase.RELEASE ? JobState.SUCCEEDED : JobState.RUNNING;
            };
            int step = receipt.status() == Status.SUCCEEDED && receipt.phase() != Phase.CANCEL ? job.step() + 1 : job.step();
            update(job, state, step, receipt.code());
            if (!receipt.equals(previous)) event(require(job.request().operationId()), "CORE_" + receipt.status(), receipt.phase(), receipt.code());
            if (state == JobState.SUCCEEDED || state == JobState.CANCELLED) {
                jdbc.update("UPDATE deployment_lifecycle_target SET operation_id=NULL WHERE operation_id=?", job.request().operationId());
            }
            return null;
        });
    }

    void unknown(Lease lease) {
        transactions.locked(() -> {
            Job job = owned(lease); if (job == null) return null;
            update(job, JobState.UNKNOWN, job.step(), "CORE_OUTCOME_UNKNOWN");
            if (!"CORE_OUTCOME_UNKNOWN".equals(job.code())) event(require(job.request().operationId()), "CORE_UNKNOWN", lease.command().phase(), "CORE_OUTCOME_UNKNOWN");
            return null;
        });
    }

    List<String> eligible() {
        return jdbc.queryForList("""
            SELECT operation_id FROM deployment_lifecycle_job
             WHERE state IN ('SCHEDULED','RUNNING','UNKNOWN','CANCEL_PENDING')
               AND (due_at<=? OR cancel_requested=TRUE)
             ORDER BY updated_at,operation_id LIMIT 10
            """, String.class, time(clock.instant()));
    }

    void notices() {
        transactions.locked(() -> {
            Instant now = clock.instant();
            var jobs = jdbc.query("SELECT * FROM deployment_lifecycle_job WHERE state='SCHEDULED' AND due_at<=? AND (notice_emitted=FALSE OR due_emitted=FALSE) ORDER BY due_at LIMIT 100",
                this::mapJob, time(now.plus(properties.noticeLead())));
            for (Job job : jobs) {
                int emitted = jdbc.update("UPDATE deployment_lifecycle_job SET notice_emitted=TRUE WHERE operation_id=? AND notice_emitted=FALSE", job.request().operationId());
                if (emitted == 1) event(job, "NOTICE_DUE", phase(job), "SCHEDULED_EXPIRY_NOTICE");
                emitDue(job, now);
            }
            return null;
        });
    }

    Optional<Delivery> claimDelivery() {
        return transactions.locked(() -> {
            Instant now = clock.instant();
            var events = jdbc.query("""
                SELECT * FROM deployment_lifecycle_event WHERE delivery='PENDING' AND next_attempt_at<=?
                 AND (lease_until IS NULL OR lease_until<=?) ORDER BY sequence_id LIMIT 1
                """, this::mapEvent, time(now), time(now));
            if (events.isEmpty()) return Optional.empty();
            Event event = events.getFirst();
            int attempts = jdbc.queryForObject("SELECT attempts FROM deployment_lifecycle_event WHERE event_id=?", Integer.class, event.eventId());
            if (attempts >= 8) {
                jdbc.update("UPDATE deployment_lifecycle_event SET delivery='EXHAUSTED' WHERE event_id=?", event.eventId());
                return Optional.empty();
            }
            String token = UUID.randomUUID().toString();
            jdbc.update("UPDATE deployment_lifecycle_event SET attempts=attempts+1,lease_token=?,lease_until=? WHERE event_id=?",
                token, time(now.plus(properties.workerLease())), event.eventId());
            return Optional.of(new Delivery(event, token));
        });
    }

    void delivered(Delivery delivery, boolean success) {
        transactions.locked(() -> {
            var attempts = jdbc.queryForList("SELECT attempts FROM deployment_lifecycle_event WHERE event_id=? AND lease_token=? AND lease_until>?",
                Integer.class, delivery.event().eventId(), delivery.token(), time(clock.instant()));
            if (attempts.isEmpty()) return null;
            int attempt = attempts.getFirst();
            jdbc.update("UPDATE deployment_lifecycle_event SET delivery=?,lease_token=NULL,lease_until=NULL,next_attempt_at=? WHERE event_id=?",
                success ? "DELIVERED" : attempt >= 8 ? "EXHAUSTED" : "PENDING",
                time(clock.instant().plusSeconds(Math.min(900, 5L << attempt))), delivery.event().eventId());
            return null;
        });
    }

    private void emitDue(Job job, Instant now) {
        if (!job.request().dueAt().isAfter(now)) {
            int updated = jdbc.update("UPDATE deployment_lifecycle_job SET due_emitted=TRUE WHERE operation_id=? AND due_emitted=FALSE", job.request().operationId());
            if (updated == 1) event(job, "JOB_DUE", phase(job), "JOB_DUE");
        }
    }

    private Job owned(Lease lease) {
        Job job = require(lease.request().operationId());
        return lease.token().equals(job.leaseToken()) && job.leaseUntil() != null && job.leaseUntil().isAfter(clock.instant()) ? job : null;
    }

    private void update(Job job, JobState state, int step, String code) {
        jdbc.update("UPDATE deployment_lifecycle_job SET state=?,step_index=?,code=?,lease_token=NULL,lease_until=NULL,updated_at=? WHERE operation_id=?",
            state.name(), step, code, time(clock.instant()), job.request().operationId());
    }

    private void releaseLease(Job job) {
        jdbc.update("UPDATE deployment_lifecycle_job SET lease_token=NULL,lease_until=NULL WHERE operation_id=?", job.request().operationId());
    }

    private Optional<Job> find(String id) {
        return jdbc.query("SELECT * FROM deployment_lifecycle_job WHERE operation_id=?", this::mapJob, id).stream().findFirst();
    }

    private Job require(String id) {
        if (id == null || !id.matches(ID)) throw invalid("LIFECYCLE_OPERATION_ID_INVALID");
        return find(id).orElseThrow(() -> new OperationsException(404, "LIFECYCLE_JOB_NOT_FOUND", "Lifecycle job was not found."));
    }

    private Optional<Work> work(String id, Phase phase) {
        return jdbc.query("SELECT command_json,delivery,receipt_json,observation_pending FROM deployment_lifecycle_command WHERE operation_id=? AND phase=?",
            (rs, row) -> new Work(read(rs.getString(1), Command.class), rs.getString(2), rs.getBoolean(4),
                rs.getString(3) == null ? null : read(rs.getString(3), Receipt.class)), id, phase.name()).stream().findFirst();
    }

    private Phase phase(Job job) {
        if (work(job.request().operationId(), Phase.CANCEL).isPresent()) return Phase.CANCEL;
        var phases = phases(job.request());
        return phases.get(Math.min(job.step(), phases.size() - 1));
    }

    static List<Phase> phases(JobRequest request) {
        var phases = new ArrayList<Phase>(); phases.add(Phase.CLAIM);
        switch (request.goal()) {
            case STOP, RETIRE -> {
                if (request.mode() == Mode.GRACEFUL) phases.addAll(List.of(Phase.BEGIN_DRAIN, Phase.OBSERVE_DRAIN, Phase.FENCE));
                phases.add(Phase.STOP);
                if (request.goal() == Goal.RETIRE) phases.add(Phase.DELETE);
                phases.add(Phase.VERIFY);
            }
            case RECOVER -> phases.addAll(List.of(Phase.RECOVER, Phase.VERIFY));
            case RECONCILE -> phases.add(Phase.OBSERVE);
        }
        phases.add(Phase.RELEASE); return List.copyOf(phases);
    }

    private JobView view(Job job) {
        Phase phase = phase(job);
        Work work = work(job.request().operationId(), phase).orElse(null);
        return new JobView(job.request(), job.state(), phase, job.irreversible(), job.cancelRequested(), job.code(),
            work == null ? null : work.command(), work == null ? null : work.receipt(), job.createdAt(), job.updatedAt());
    }

    private Job mapJob(ResultSet rs, int row) throws SQLException {
        Timestamp until = rs.getTimestamp("lease_until");
        return new Job(read(rs.getString("request_json"), JobRequest.class), JobState.valueOf(rs.getString("state")),
            rs.getInt("step_index"), rs.getBoolean("irreversible"), rs.getBoolean("cancel_requested"), rs.getString("code"),
            rs.getString("lease_token"), until == null ? null : until.toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private Event mapEvent(ResultSet rs, int row) throws SQLException {
        String phase = rs.getString("phase");
        return new Event(rs.getLong("sequence_id"), rs.getString("event_id"), rs.getString("operation_id"), rs.getString("event_type"),
            JobState.valueOf(rs.getString("state")), phase == null ? null : Phase.valueOf(phase), rs.getString("code"), rs.getTimestamp("occurred_at").toInstant());
    }

    private void event(Job job, String type, Phase phase, String code) {
        Instant now = clock.instant();
        jdbc.update("INSERT INTO deployment_lifecycle_event (event_id,operation_id,event_type,state,phase,code,occurred_at,delivery,next_attempt_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "evt-" + UUID.randomUUID(), job.request().operationId(), type, job.state().name(), phase.name(), code, time(now),
            properties.webhookUrl().isBlank() ? "DISABLED" : "PENDING", time(now));
    }

    private boolean terminal(Job job) { return job.state() == JobState.SUCCEEDED || job.state() == JobState.CANCELLED; }
    private int size(int limit) { return Math.max(1, Math.min(limit, 100)); }
    private void bounds(long offset) { if (offset < 0 || offset > 10_000) throw invalid("LIFECYCLE_OFFSET_INVALID"); }
    private Timestamp time(Instant value) { return Timestamp.from(value); }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Lifecycle record serialization failed."); }
    }
    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (Exception exception) { throw new IllegalStateException("Lifecycle stored record is invalid."); }
    }
    private OperationsException conflict(String code) { return new OperationsException(409, code, "Lifecycle operation conflicts with durable state."); }
    private OperationsException invalid(String code) { return new OperationsException(400, code, "Lifecycle request bounds are invalid."); }
}

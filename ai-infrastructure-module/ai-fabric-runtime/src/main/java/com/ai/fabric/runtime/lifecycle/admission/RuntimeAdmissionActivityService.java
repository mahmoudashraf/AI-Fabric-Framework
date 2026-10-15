package com.ai.fabric.runtime.lifecycle.admission;

import com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.Target;
import com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Fixed-size per-release watermarks. Neither user content nor browser-provided activity is accepted. */
@Service
public class RuntimeAdmissionActivityService {
    public enum Kind { ACCEPTED_SESSION, ACCEPTED_ACTION, HUMAN_WORK }
    public record Signal(Kind kind, long sequence, Instant acceptedAt) { }
    public record Readback(String schemaVersion, String deploymentId, String tenantId, String targetProfileId,
                           String releaseId, Instant observedAt, List<Signal> signals) { }
    private final JdbcTemplate jdbc;
    private final RuntimeTargetRetirementIdentity identity;
    private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate transaction;

    @org.springframework.beans.factory.annotation.Autowired
    public RuntimeAdmissionActivityService(JdbcTemplate jdbc, RuntimeTargetRetirementIdentity identity,
                                           org.springframework.transaction.PlatformTransactionManager transactions) {
        this(jdbc, identity, transactions, Clock.systemUTC());
    }

    RuntimeAdmissionActivityService(JdbcTemplate jdbc, RuntimeTargetRetirementIdentity identity,
        org.springframework.transaction.PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc; this.identity = identity; this.clock = clock;
        this.transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
    }

    public void accepted(Kind kind) {
        if (!identity.enabled()) return;
        if (kind == null) throw new IllegalArgumentException("Accepted activity kind is required.");
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM runtime_admission_activity_mutex WHERE id=1 FOR UPDATE", Integer.class);
            Target target = identity.target();
            Timestamp now = Timestamp.from(clock.instant());
            if (increment(target, kind, now) != 0) return;
            jdbc.update("""
                INSERT INTO runtime_admission_activity
                 (deployment_id,tenant_id,target_profile_id,release_id,activity_kind,sequence,accepted_at)
                VALUES (?,?,?,?,?,1,?)
                """, target.deploymentId(), identity.tenantId(), target.targetProfileId(), target.releaseId(), kind.name(), now);
        });
    }

    private int increment(Target target, Kind kind, Timestamp now) {
        return jdbc.update("""
            UPDATE runtime_admission_activity SET sequence=sequence+1,accepted_at=GREATEST(accepted_at,?)
             WHERE deployment_id=? AND tenant_id=? AND target_profile_id=? AND release_id=? AND activity_kind=?
            """, now, target.deploymentId(), identity.tenantId(), target.targetProfileId(), target.releaseId(), kind.name());
    }

    public Readback read(Target target) {
        identity.requireTarget(target);
        var signals = jdbc.query("""
            SELECT activity_kind,sequence,accepted_at FROM runtime_admission_activity
             WHERE deployment_id=? AND tenant_id=? AND target_profile_id=? AND release_id=? ORDER BY activity_kind
            """, (rs, row) -> new Signal(Kind.valueOf(rs.getString(1)), rs.getLong(2), rs.getTimestamp(3).toInstant()),
            target.deploymentId(), identity.tenantId(), target.targetProfileId(), target.releaseId());
        return new Readback("loomai-runtime-admission-activity-v1", target.deploymentId(), identity.tenantId(),
            target.targetProfileId(), target.releaseId(), clock.instant(), signals);
    }
}

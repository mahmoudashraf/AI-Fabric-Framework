package com.loomai.operations.persistence;

import static com.loomai.operations.model.OperationsContracts.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OperationsRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public OperationsRepository(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(5);
    }

    public <T> T locked(Supplier<T> operation) {
        return transactions.execute(transaction -> {
            // One short database transaction serializes decisions across service replicas.
            jdbc.queryForObject("SELECT id FROM deployment_operations_lock WHERE id = 1 FOR UPDATE", Integer.class);
            return operation.get();
        });
    }

    public Optional<StoredAssessment> assessment(String commandId) {
        return jdbc.query("""
            SELECT request_json, request_digest, response_json, last_outcome_json
              FROM deployment_operations_assessment WHERE command_id = ?
            """, (rs, row) -> new StoredAssessment(
                read(rs.getString("request_json"), AssessmentRequest.class), rs.getString("request_digest"),
                read(rs.getString("response_json"), AssessmentResponse.class),
                rs.getString("last_outcome_json") == null ? null : read(rs.getString("last_outcome_json"), OutcomeRequest.class)
            ), commandId).stream().findFirst();
    }

    public void saveAssessment(AssessmentRequest request, String digest, AssessmentResponse response) {
        jdbc.update("""
            INSERT INTO deployment_operations_assessment
                (command_id, request_fingerprint, request_digest, assessment_id, customer_id, tenant_id,
                 deployment_id, target_profile_id, mode, decision, reason, policy_revision,
                 request_json, response_json, observed_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            request.commandId(), request.requestFingerprint(), digest, response.assessmentId(), request.customerId(),
            request.tenantId(), request.deploymentId(), request.targetProfileId(), request.mode().name(),
            response.decision().name(), response.reason(), response.policyRevision(), write(request), write(response),
            Timestamp.from(response.observedAt()), Timestamp.from(response.expiresAt()));
    }

    public Optional<Allocation> allocation(String deploymentId, String targetProfileId) {
        return jdbc.query("""
            SELECT * FROM deployment_operations_allocation WHERE deployment_id = ? AND target_profile_id = ?
            """, this::mapAllocation, deploymentId, targetProfileId).stream().findFirst();
    }

    public List<Allocation> allocations(String targetProfileId) {
        return jdbc.query("""
            SELECT * FROM deployment_operations_allocation WHERE target_profile_id = ? AND state <> 'STOPPED'
            """, this::mapAllocation, targetProfileId);
    }

    public List<Allocation> deploymentAllocations(String deploymentId) {
        return jdbc.query("SELECT * FROM deployment_operations_allocation WHERE deployment_id = ?",
            this::mapAllocation, deploymentId);
    }

    public void expireHolds(Instant now) {
        jdbc.update("""
            UPDATE deployment_operations_allocation SET state = 'UNCONFIRMED', updated_at = ?
             WHERE state = 'HOLD' AND hold_expires_at <= ?
            """, Timestamp.from(now), Timestamp.from(now));
    }

    public void saveAllocation(Allocation allocation) {
        int updated = jdbc.update("""
            UPDATE deployment_operations_allocation
               SET command_id = ?, resource_demand_json = ?, state = ?, hold_expires_at = ?, updated_at = ?
             WHERE deployment_id = ? AND target_profile_id = ? AND customer_id = ? AND tenant_id = ?
            """, allocation.commandId(), write(allocation.demand()), allocation.state().name(),
            Timestamp.from(allocation.holdExpiresAt()), Timestamp.from(allocation.updatedAt()),
            allocation.deploymentId(), allocation.targetProfileId(), allocation.customerId(), allocation.tenantId());
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO deployment_operations_allocation
                    (deployment_id, target_profile_id, customer_id, tenant_id, command_id,
                     resource_demand_json, state, hold_expires_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, allocation.deploymentId(), allocation.targetProfileId(), allocation.customerId(), allocation.tenantId(),
                allocation.commandId(), write(allocation.demand()), allocation.state().name(),
                Timestamp.from(allocation.holdExpiresAt()), Timestamp.from(allocation.updatedAt()));
        }
    }

    public boolean hasOutcome(String digest) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT COUNT(*) > 0 FROM deployment_operations_outcome WHERE payload_sha256 = ?
            """, Boolean.class, digest));
    }

    public void saveOutcome(OutcomeRequest outcome, String digest, String outcomeId) {
        jdbc.update("""
            INSERT INTO deployment_operations_outcome
                (outcome_id, command_id, payload_sha256, status, observed_at, outcome_json)
            VALUES (?, ?, ?, ?, ?, ?)
            """, outcomeId, outcome.commandId(), digest, outcome.status().name(),
            Timestamp.from(outcome.observedAt()), write(outcome));
        jdbc.update("UPDATE deployment_operations_assessment SET last_outcome_json = ? WHERE command_id = ?",
            write(outcome), outcome.commandId());
    }

    public String digest(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(write(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required.", exception);
        }
    }

    private Allocation mapAllocation(ResultSet rs, int row) throws SQLException {
        return new Allocation(rs.getString("customer_id"), rs.getString("tenant_id"), rs.getString("deployment_id"),
            rs.getString("target_profile_id"), rs.getString("command_id"),
            read(rs.getString("resource_demand_json"), ResourceDemand.class),
            AllocationState.valueOf(rs.getString("state")), rs.getTimestamp("hold_expires_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Deployment operations record serialization failed.");
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Deployment operations stored record is invalid.");
        }
    }
}

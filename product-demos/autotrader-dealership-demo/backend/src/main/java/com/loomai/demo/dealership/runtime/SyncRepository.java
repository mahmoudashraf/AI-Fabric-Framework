package com.loomai.demo.dealership.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SyncRepository {

    private final JdbcTemplate jdbc;

    public SyncRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createRun(String id, String requestId, int total) {
        jdbc.update("""
            INSERT INTO dealership_sync_run
                (id, request_id, status, total_count, succeeded_count, failed_count, started_at)
            VALUES (?, ?, 'SUBMITTING', ?, 0, 0, ?)
            """, id, requestId, total, Timestamp.from(Instant.now()));
    }

    public void addItem(String id, String runId, String vehicleId, String workId, String status,
                        String failureCode, String failureMessage) {
        jdbc.update("""
            INSERT INTO dealership_sync_item
                (id, run_id, vehicle_id, indexing_work_id, status, failure_code, failure_message, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, runId, vehicleId, workId, status, failureCode, truncate(failureMessage), Timestamp.from(Instant.now()));
    }

    public void finishSubmission(String runId, String status, int succeeded, int failed,
                                 String providerRequestId, String failureCode, String failureMessage) {
        jdbc.update("""
            UPDATE dealership_sync_run
               SET status = ?, succeeded_count = ?, failed_count = ?, provider_request_id = ?,
                   failure_code = ?, failure_message = ?, completed_at = ?
             WHERE id = ?
            """, status, succeeded, failed, providerRequestId, failureCode, truncate(failureMessage),
            isTerminal(status) ? Timestamp.from(Instant.now()) : null, runId);
    }

    public List<PendingWork> pendingWork(int limit) {
        return jdbc.query("""
            SELECT id, run_id, vehicle_id, indexing_work_id
              FROM dealership_sync_item
             WHERE indexing_work_id IS NOT NULL AND status IN ('QUEUED', 'PROCESSING', 'RETRYING')
             ORDER BY updated_at
             LIMIT ?
            """, (rs, rowNum) -> new PendingWork(
                rs.getString("id"), rs.getString("run_id"), rs.getString("vehicle_id"), rs.getString("indexing_work_id")
            ), limit);
    }

    public void updateWork(String itemId, String status, String failureCode, String failureMessage) {
        jdbc.update("""
            UPDATE dealership_sync_item
               SET status = ?, failure_code = ?, failure_message = ?, updated_at = ?
             WHERE id = ?
            """, status, failureCode, truncate(failureMessage), Timestamp.from(Instant.now()), itemId);
    }

    public void refreshRun(String runId) {
        RunCounts counts = jdbc.queryForObject("""
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS succeeded,
                   SUM(CASE WHEN status IN ('FAILED', 'DEAD_LETTER') THEN 1 ELSE 0 END) AS failed,
                   SUM(CASE WHEN status IN ('QUEUED', 'PROCESSING', 'RETRYING') THEN 1 ELSE 0 END) AS pending
              FROM dealership_sync_item WHERE run_id = ?
            """, (rs, rowNum) -> new RunCounts(
                rs.getInt("total"), rs.getInt("succeeded"), rs.getInt("failed"), rs.getInt("pending")
            ), runId);
        if (counts == null) {
            return;
        }
        String status = counts.pending() > 0 ? "INDEXING"
            : counts.failed() == 0 ? "COMPLETED"
            : counts.succeeded() == 0 ? "FAILED" : "PARTIAL";
        jdbc.update("""
            UPDATE dealership_sync_run
               SET status = ?, succeeded_count = ?, failed_count = ?, completed_at = ?
             WHERE id = ?
            """, status, counts.succeeded(), counts.failed(), counts.pending() == 0 ? Timestamp.from(Instant.now()) : null, runId);
    }

    public Optional<RunSummary> latestRun() {
        List<RunSummary> rows = jdbc.query("""
            SELECT id, request_id, status, total_count, succeeded_count, failed_count,
                   provider_request_id, failure_code, failure_message, started_at, completed_at
              FROM dealership_sync_run ORDER BY started_at DESC LIMIT 1
            """, (rs, rowNum) -> new RunSummary(
                rs.getString("id"), rs.getString("request_id"), rs.getString("status"),
                rs.getInt("total_count"), rs.getInt("succeeded_count"), rs.getInt("failed_count"),
                rs.getString("provider_request_id"), rs.getString("failure_code"), rs.getString("failure_message"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()
            ));
        return rows.stream().findFirst();
    }

    private boolean isTerminal(String status) {
        return List.of("COMPLETED", "FAILED", "PARTIAL").contains(status);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    public record PendingWork(String id, String runId, String vehicleId, String workId) { }
    public record RunCounts(int total, int succeeded, int failed, int pending) { }
    public record RunSummary(String id, String requestId, String status, int total, int succeeded, int failed,
                             String providerRequestId, String failureCode, String failureMessage,
                             Instant startedAt, Instant completedAt) { }
}

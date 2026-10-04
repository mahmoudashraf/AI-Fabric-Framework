package com.ai.infrastructure.connector.rest.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class JdbcIntegrationStateRepository implements IntegrationStateRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public JdbcIntegrationStateRepository(JdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper());
    }

    public JdbcIntegrationStateRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(
            Objects.requireNonNull(jdbc.getDataSource(), "JdbcTemplate DataSource is required")
        ));
    }

    @Override
    public void startSync(String sourceId, String runId, String sourceVersion) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
            UPDATE integration_sync_state
               SET status = 'RUNNING', source_version = ?, last_started_at = ?, error_class = NULL,
                   error_message = NULL, updated_at = ?
             WHERE source_id = ?
            """, sourceVersion, Timestamp.from(now), Timestamp.from(now), sourceId);
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO integration_sync_state
                    (source_id, status, source_count, normalized_count, indexed_count,
                     deleted_count, accepted_work_count, completed_work_count, failed_work_count,
                     source_version, last_started_at, updated_at)
                VALUES (?, 'RUNNING', 0, 0, 0, 0, 0, 0, 0, ?, ?, ?)
                """, sourceId, sourceVersion, Timestamp.from(now), Timestamp.from(now));
        }
    }

    @Override
    public void completeSync(String sourceId, String runId, String cursor, SyncCounts counts) {
        Instant now = Instant.now();
        jdbc.update("""
            UPDATE integration_sync_state
               SET status = ?, cursor_value = ?, source_count = ?, normalized_count = ?,
                   indexed_count = ?, deleted_count = ?, accepted_work_count = ?,
                   completed_work_count = ?, failed_work_count = ?, last_success_at = ?,
                   error_class = ?, error_message = NULL, updated_at = ?
             WHERE source_id = ?
            """,
            counts.failedWorkCount() > 0 ? "PARTIAL" : "COMPLETED",
            cursor,
            counts.sourceCount(),
            counts.normalizedCount(),
            counts.indexedCount(),
            counts.deletedCount(),
            counts.acceptedWorkCount(),
            counts.completedWorkCount(),
            counts.failedWorkCount(),
            Timestamp.from(now),
            counts.failedWorkCount() > 0 ? "INDEXING_WORK_FAILED" : null,
            Timestamp.from(now),
            sourceId
        );
    }

    @Override
    public void failSync(String sourceId, String errorClass, String message, SyncCounts counts) {
        Instant now = Instant.now();
        if (counts != null) {
            jdbc.update("""
                UPDATE integration_sync_state
                   SET status = 'FAILED', source_count = ?, normalized_count = ?, indexed_count = ?,
                       deleted_count = ?, accepted_work_count = ?, completed_work_count = ?,
                       failed_work_count = ?, last_error_at = ?, error_class = ?, error_message = ?, updated_at = ?
                 WHERE source_id = ?
                """,
                counts.sourceCount(), counts.normalizedCount(), counts.indexedCount(), counts.deletedCount(),
                counts.acceptedWorkCount(), counts.completedWorkCount(), counts.failedWorkCount(),
                Timestamp.from(now), errorClass, bounded(message, 1000), Timestamp.from(now), sourceId
            );
            return;
        }
        jdbc.update("""
            UPDATE integration_sync_state
               SET status = 'FAILED', last_error_at = ?, error_class = ?, error_message = ?, updated_at = ?
             WHERE source_id = ?
            """, Timestamp.from(now), errorClass, bounded(message, 1000), Timestamp.from(now), sourceId);
    }

    @Override
    public void recordProviderCorrelation(String sourceId, String headerName, String headerValue) {
        jdbc.update("""
            UPDATE integration_sync_state
               SET provider_correlation_header = ?, provider_correlation_value = ?, updated_at = ?
             WHERE source_id = ?
            """, bounded(headerName, 160), bounded(headerValue, 256), Timestamp.from(Instant.now()), sourceId);
    }

    @Override
    public Optional<SyncState> syncState(String sourceId) {
        List<SyncState> rows = jdbc.query(
            "SELECT * FROM integration_sync_state WHERE source_id = ?",
            (rs, rowNum) -> mapSyncState(rs),
            sourceId
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<SyncState> syncStates() {
        return jdbc.query("SELECT * FROM integration_sync_state ORDER BY source_id", (rs, rowNum) -> mapSyncState(rs));
    }

    @Override
    public Set<String> activeRecordIds(String sourceId) {
        return new LinkedHashSet<>(jdbc.query(
            "SELECT record_id FROM integration_source_record WHERE source_id = ? AND active = TRUE ORDER BY record_id",
            (rs, rowNum) -> rs.getString(1),
            sourceId
        ));
    }

    @Override
    public void markRecordSeen(String sourceId, String recordId, String fingerprint, String runId) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
            UPDATE integration_source_record
               SET fingerprint = ?, last_seen_run = ?, active = TRUE, updated_at = ?
             WHERE source_id = ? AND record_id = ?
            """, fingerprint, runId, Timestamp.from(now), sourceId, recordId);
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO integration_source_record
                    (source_id, record_id, fingerprint, last_seen_run, active, updated_at)
                VALUES (?, ?, ?, ?, TRUE, ?)
                """, sourceId, recordId, fingerprint, runId, Timestamp.from(now));
        }
    }

    @Override
    public void markRecordDeleted(String sourceId, String recordId) {
        jdbc.update(
            "UPDATE integration_source_record SET active = FALSE, updated_at = ? WHERE source_id = ? AND record_id = ?",
            Timestamp.from(Instant.now()), sourceId, recordId
        );
    }

    @Override
    public void applyProjectionChanges(
        String sourceId,
        String runId,
        List<SourceProjectionRecord> upserts,
        Set<String> deletes
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            if (upserts != null) {
                for (SourceProjectionRecord record : upserts) {
                    if (record == null) {
                        continue;
                    }
                    Instant updatedAt = record.updatedAt() != null ? record.updatedAt() : Instant.now();
                    jdbc.update("""
                        INSERT INTO integration_source_record
                            (source_id, record_id, fingerprint, last_seen_run, active, content_text,
                             entity_data, metadata_data, updated_at)
                        VALUES (?, ?, ?, ?, TRUE, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?)
                        ON CONFLICT (source_id, record_id) DO UPDATE
                           SET fingerprint = EXCLUDED.fingerprint,
                               last_seen_run = EXCLUDED.last_seen_run,
                               active = TRUE,
                               content_text = EXCLUDED.content_text,
                               entity_data = EXCLUDED.entity_data,
                               metadata_data = EXCLUDED.metadata_data,
                               updated_at = EXCLUDED.updated_at
                        """,
                        sourceId,
                        record.recordId(),
                        record.fingerprint(),
                        runId,
                        record.content() != null ? record.content() : "",
                        writeJson(record.entity()),
                        writeJson(record.metadata()),
                        Timestamp.from(updatedAt)
                    );
                }
            }
            if (deletes != null) {
                Instant now = Instant.now();
                for (String recordId : deletes) {
                    jdbc.update(
                        "UPDATE integration_source_record SET active = FALSE, updated_at = ? WHERE source_id = ? AND record_id = ?",
                        Timestamp.from(now), sourceId, recordId
                    );
                }
            }
        });
    }

    @Override
    public ProjectionQueryResult queryProjection(String sourceId, ProjectionQuery query) {
        ProjectionQuery safeQuery = query != null ? query : new ProjectionQuery(List.of(), 1);
        StringBuilder sql = new StringBuilder("""
            SELECT record_id, fingerprint, content_text, entity_data::text AS entity_json,
                   metadata_data::text AS metadata_json, updated_at,
                   COUNT(*) OVER() AS total_matches
              FROM integration_source_record
             WHERE source_id = ? AND active = TRUE
            """);
        List<Object> args = new ArrayList<>();
        args.add(sourceId);
        if (safeQuery.criteria() != null) {
            for (ProjectionCriterion criterion : safeQuery.criteria()) {
                appendCriterion(sql, args, criterion);
            }
        }
        sql.append(" ORDER BY record_id LIMIT ?");
        args.add(Math.max(1, safeQuery.limit()));

        List<ProjectionRow> rows = jdbc.query(
            sql.toString(),
            (rs, rowNum) -> new ProjectionRow(mapProjection(rs), rs.getLong("total_matches")),
            args.toArray()
        );
        long total = rows.isEmpty() ? 0L : rows.getFirst().totalMatches();
        return new ProjectionQueryResult(rows.stream().map(ProjectionRow::record).toList(), total);
    }

    @Override
    public void recordWork(String workId, String sourceId, String recordId, String operation, String status) {
        Instant now = Instant.now();
        jdbc.update("""
            INSERT INTO integration_index_work
                (work_id, source_id, record_id, operation, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (work_id) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
            """, workId, sourceId, recordId, operation, status, Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void updateWork(String workId, String status, String errorCode) {
        jdbc.update(
            "UPDATE integration_index_work SET status = ?, error_code = ?, updated_at = ? WHERE work_id = ?",
            status, errorCode, Timestamp.from(Instant.now()), workId
        );
    }

    @Override
    public List<IndexWorkState> workStates(String sourceId) {
        return jdbc.query("""
            SELECT * FROM integration_index_work WHERE source_id = ? ORDER BY created_at DESC
            """, (rs, rowNum) -> mapWorkState(rs), sourceId);
    }

    @Override
    public boolean registerEvent(WebhookEvent event) {
        try {
            return jdbc.update("""
                INSERT INTO integration_webhook_event
                    (source_id, event_id, event_type, record_key, resource_fingerprint, payload_sha256,
                     status, attempt_count, duplicate_count, replay_count, error_class, received_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, event_id) DO NOTHING
                """,
                event.sourceId(), event.eventId(), event.eventType(), event.recordKey(), event.resourceFingerprint(),
                event.payloadSha256(), event.status(), event.attemptCount(), event.duplicateCount(),
                event.replayCount(), event.errorClass(),
                Timestamp.from(event.receivedAt()), Timestamp.from(event.updatedAt())
            ) == 1;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    @Override
    public void markEventDuplicate(String sourceId, String eventId) {
        jdbc.update("""
            UPDATE integration_webhook_event
               SET duplicate_count = duplicate_count + 1, updated_at = ?
             WHERE source_id = ? AND event_id = ?
            """, Timestamp.from(Instant.now()), sourceId, eventId);
    }

    @Override
    public void markEventReplay(String sourceId, String eventId) {
        jdbc.update("""
            UPDATE integration_webhook_event
               SET replay_count = replay_count + 1, updated_at = ?
             WHERE source_id = ? AND event_id = ?
            """, Timestamp.from(Instant.now()), sourceId, eventId);
    }

    @Override
    public void beginEventAttempt(String sourceId, String eventId, String status) {
        jdbc.update("""
            UPDATE integration_webhook_event
               SET status = ?, error_class = NULL, attempt_count = attempt_count + 1, updated_at = ?
             WHERE source_id = ? AND event_id = ?
            """, status, Timestamp.from(Instant.now()), sourceId, eventId);
    }

    @Override
    public void updateEvent(String sourceId, String eventId, String status, String errorClass) {
        jdbc.update("""
            UPDATE integration_webhook_event
               SET status = ?, error_class = ?, updated_at = ?
             WHERE source_id = ? AND event_id = ?
            """, status, errorClass, Timestamp.from(Instant.now()), sourceId, eventId);
    }

    @Override
    public Optional<WebhookEvent> event(String sourceId, String eventId) {
        return jdbc.query(
            "SELECT * FROM integration_webhook_event WHERE source_id = ? AND event_id = ?",
            (rs, rowNum) -> mapEvent(rs), sourceId, eventId
        ).stream().findFirst();
    }

    @Override
    public List<WebhookEvent> recentEvents(String sourceId, int limit) {
        return jdbc.query(
            "SELECT * FROM integration_webhook_event WHERE source_id = ? ORDER BY received_at DESC LIMIT ?",
            (rs, rowNum) -> mapEvent(rs), sourceId, Math.max(1, Math.min(500, limit))
        );
    }

    @Override
    public void recordEventRejection(String sourceId, String errorClass) {
        jdbc.update("""
            INSERT INTO integration_webhook_rejection
                (source_id, error_class, rejection_count, last_rejected_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT (source_id, error_class) DO UPDATE
                SET rejection_count = integration_webhook_rejection.rejection_count + 1,
                    last_rejected_at = EXCLUDED.last_rejected_at
            """, sourceId, errorClass, Timestamp.from(Instant.now()));
    }

    @Override
    public long eventRejectionCount(String sourceId) {
        Long count = jdbc.queryForObject(
            "SELECT COALESCE(SUM(rejection_count), 0) FROM integration_webhook_rejection WHERE source_id = ?",
            Long.class,
            sourceId
        );
        return count != null ? count : 0L;
    }

    private void appendCriterion(StringBuilder sql, List<Object> args, ProjectionCriterion criterion) {
        if (criterion == null || criterion.operator() == null || criterion.fields() == null
            || criterion.fields().isEmpty() || criterion.values() == null || criterion.values().isEmpty()) {
            throw new IllegalArgumentException("Projection query criterion is incomplete.");
        }
        List<String> alternatives = new ArrayList<>();
        switch (criterion.operator()) {
            case EQUALS_IGNORE_CASE, ANY_TOKEN_EQUALS_IGNORE_CASE -> {
                for (String field : criterion.fields()) {
                    for (String value : criterion.values()) {
                        alternatives.add("LOWER(COALESCE(entity_data ->> ?, '')) = LOWER(?)");
                        args.add(field);
                        args.add(value);
                    }
                }
            }
            case NUMBER_LESS_THAN_OR_EQUAL -> {
                if (criterion.values().size() != 1) {
                    throw new IllegalArgumentException("Numeric projection criteria require exactly one value.");
                }
                for (String field : criterion.fields()) {
                    alternatives.add("(jsonb_typeof(entity_data -> ?) = 'number' AND (entity_data ->> ?)::numeric <= CAST(? AS numeric))");
                    args.add(field);
                    args.add(field);
                    args.add(criterion.values().getFirst());
                }
            }
        }
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("Projection query criterion produced no predicates.");
        }
        sql.append(" AND (").append(String.join(" OR ", alternatives)).append(')');
    }

    private SourceProjectionRecord mapProjection(ResultSet rs) throws SQLException {
        return new SourceProjectionRecord(
            rs.getString("record_id"),
            rs.getString("fingerprint"),
            rs.getString("content_text"),
            readJsonMap(rs.getString("entity_json")),
            readJsonMap(rs.getString("metadata_json")),
            instant(rs, "updated_at")
        );
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : Map.of());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize integration source projection.", exception);
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(value, new TypeReference<>() { });
            return parsed != null && !parsed.isEmpty() ? Map.copyOf(parsed) : Map.of();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize integration source projection.", exception);
        }
    }

    private SyncState mapSyncState(ResultSet rs) throws SQLException {
        return new SyncState(
            rs.getString("source_id"),
            rs.getString("status"),
            rs.getString("cursor_value"),
            rs.getString("source_version"),
            rs.getString("provider_correlation_header"),
            rs.getString("provider_correlation_value"),
            new SyncCounts(
                rs.getInt("source_count"),
                rs.getInt("normalized_count"),
                rs.getInt("indexed_count"),
                rs.getInt("deleted_count"),
                rs.getInt("accepted_work_count"),
                rs.getInt("completed_work_count"),
                rs.getInt("failed_work_count")
            ),
            instant(rs, "last_started_at"),
            instant(rs, "last_success_at"),
            instant(rs, "last_error_at"),
            rs.getString("error_class"),
            rs.getString("error_message"),
            instant(rs, "updated_at")
        );
    }

    private IndexWorkState mapWorkState(ResultSet rs) throws SQLException {
        return new IndexWorkState(
            rs.getString("work_id"), rs.getString("source_id"), rs.getString("record_id"),
            rs.getString("operation"), rs.getString("status"), rs.getString("error_code"),
            instant(rs, "created_at"), instant(rs, "updated_at")
        );
    }

    private WebhookEvent mapEvent(ResultSet rs) throws SQLException {
        return new WebhookEvent(
            rs.getString("source_id"), rs.getString("event_id"), rs.getString("event_type"),
            rs.getString("record_key"), rs.getString("resource_fingerprint"), rs.getString("payload_sha256"), rs.getString("status"),
            rs.getInt("attempt_count"), rs.getInt("duplicate_count"), rs.getInt("replay_count"),
            rs.getString("error_class"), instant(rs, "received_at"),
            instant(rs, "updated_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private String bounded(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record ProjectionRow(SourceProjectionRecord record, long totalMatches) {
    }
}

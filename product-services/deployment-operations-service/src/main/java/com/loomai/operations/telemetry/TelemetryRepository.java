package com.loomai.operations.telemetry;

import static com.loomai.operations.telemetry.TelemetryContracts.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class TelemetryRepository {
    record StoredSample(SampleBatch sample, Instant receivedAt, String digest) {}
    record Boot(Instant firstObservedAt, Instant lastObservedAt) {}
    private static final TypeReference<Map<String, TelemetryMetrics.Statistic>> STATS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final TransactionTemplate reads;

    public TelemetryRepository(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        transactions = new TransactionTemplate(manager);
        transactions.setTimeout(5);
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        reads.setTimeout(5);
    }

    <T> T locked(Supplier<T> operation) {
        return transactions.execute(transaction -> {
            // Telemetry never locks admission or lifecycle records.
            jdbc.queryForObject("SELECT id FROM operations_telemetry_lock WHERE id = 1 FOR UPDATE", Integer.class);
            return operation.get();
        });
    }

    Optional<StoredSample> latest(String serverId) {
        return jdbc.query("SELECT payload_json, received_at, payload_sha256 FROM operations_telemetry_latest WHERE server_id = ?",
            this::stored, serverId).stream().findFirst();
    }

    Optional<StoredSample> minute(String serverId, long minute) {
        return jdbc.query("SELECT payload_json, received_at, payload_sha256 FROM operations_host_samples WHERE server_id = ? AND sample_minute = ?",
            this::stored, serverId, minute).stream().findFirst();
    }

    Optional<Boot> boot(String serverId, String bootId) {
        return jdbc.query("SELECT first_observed_at, last_observed_at FROM operations_telemetry_boots WHERE server_id = ? AND boot_id = ?",
            (rs, row) -> new Boot(instant(rs, "first_observed_at"), instant(rs, "last_observed_at")), serverId, bootId)
            .stream().findFirst();
    }

    void saveBoot(SampleBatch batch, Optional<Boot> previous) {
        if (previous.isEmpty()) {
            jdbc.update("INSERT INTO operations_telemetry_boots (server_id, boot_id, first_observed_at, last_observed_at) VALUES (?, ?, ?, ?)",
                batch.serverId(), batch.bootId(), time(batch.observedAt()), time(batch.observedAt()));
        } else {
            jdbc.update("UPDATE operations_telemetry_boots SET first_observed_at = ?, last_observed_at = ? WHERE server_id = ? AND boot_id = ?",
                time(batch.observedAt().isBefore(previous.get().firstObservedAt()) ? batch.observedAt() : previous.get().firstObservedAt()),
                time(batch.observedAt().isAfter(previous.get().lastObservedAt()) ? batch.observedAt() : previous.get().lastObservedAt()),
                batch.serverId(), batch.bootId());
        }
    }

    void saveRaw(SampleBatch batch, Instant receivedAt, String digest) {
        jdbc.update("""
            INSERT INTO operations_host_samples
                (server_id, sample_minute, boot_id, observed_key, observed_at, received_at, payload_sha256, payload_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, batch.serverId(), Math.floorDiv(batch.observedAt().getEpochSecond(), 60), batch.bootId(),
            batch.observedAt().toString(), time(batch.observedAt()), time(receivedAt), digest, write(batch));
    }

    void saveLatest(SampleBatch batch, Instant receivedAt, String digest, boolean exists) {
        if (exists) {
            jdbc.update("UPDATE operations_telemetry_latest SET observed_at = ?, received_at = ?, payload_sha256 = ?, payload_json = ? WHERE server_id = ?",
                time(batch.observedAt()), time(receivedAt), digest, write(batch), batch.serverId());
        } else {
            jdbc.update("INSERT INTO operations_telemetry_latest (server_id, observed_at, received_at, payload_sha256, payload_json) VALUES (?, ?, ?, ?, ?)",
                batch.serverId(), time(batch.observedAt()), time(receivedAt), digest, write(batch));
        }
    }

    void raw(String serverId, Instant from, Instant to, Consumer<SampleBatch> consume) {
        // Stream batches rather than materializing up to 24 hours of container payloads.
        reads.executeWithoutResult(transaction -> jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                SELECT payload_json FROM operations_host_samples
                 WHERE server_id = ? AND observed_at >= ? AND observed_at <= ?
                 ORDER BY observed_at LIMIT 1441
                """);
            statement.setString(1, serverId);
            statement.setObject(2, time(from));
            statement.setObject(3, time(to));
            // PostgreSQL cursors require both a transaction and a positive fetch size.
            statement.setFetchSize(8);
            return statement;
        }, (RowCallbackHandler) rs -> consume.accept(read(rs.getString(1), SampleBatch.class))));
    }

    void replaceAggregates(String serverId, Instant bucket, Map<String, Map<String, TelemetryMetrics.Statistic>> resources) {
        jdbc.update("DELETE FROM operations_telemetry_aggregates WHERE server_id = ? AND bucket_start = ?", serverId, time(bucket));
        List<Object[]> rows = new ArrayList<>();
        resources.forEach((resource, stats) -> rows.add(new Object[] {serverId, resource, time(bucket), write(stats)}));
        jdbc.batchUpdate("INSERT INTO operations_telemetry_aggregates (server_id, resource_id, bucket_start, stats_json) VALUES (?, ?, ?, ?)", rows);
    }

    Map<String, Map<String, TelemetryMetrics.Statistic>> bucketStatistics(String serverId, Instant bucket) {
        Map<String, Map<String, TelemetryMetrics.Statistic>> statistics = new HashMap<>();
        jdbc.query("SELECT resource_id, stats_json FROM operations_telemetry_aggregates WHERE server_id = ? AND bucket_start = ? LIMIT 1001",
            (RowCallbackHandler) rs -> {
                try {
                    statistics.put(rs.getString("resource_id"), mapper.readValue(rs.getString("stats_json"), STATS));
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Stored telemetry statistics are invalid.");
                }
            }, serverId, time(bucket));
        return statistics;
    }

    void aggregates(String serverId, String resourceId, Instant from, Instant to,
                    BiConsumer<Instant, Map<String, TelemetryMetrics.Statistic>> consume) {
        jdbc.query("""
            SELECT bucket_start, stats_json FROM operations_telemetry_aggregates
             WHERE server_id = ? AND resource_id = ? AND bucket_start >= ? AND bucket_start <= ?
             ORDER BY bucket_start LIMIT 2017
            """, (RowCallbackHandler) rs -> {
                try {
                    consume.accept(instant(rs, "bucket_start"), mapper.readValue(rs.getString("stats_json"), STATS));
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Stored telemetry statistics are invalid.");
                }
            }, serverId, resourceId == null ? "" : resourceId, time(from), time(to));
    }

    public void purge(Instant now, List<String> configuredServers, boolean registryEnabled) {
        // Fixed batches keep an overdue retention pass from monopolizing the shared database.
        jdbc.update("""
            DELETE FROM operations_host_samples WHERE (server_id, sample_minute) IN
                (SELECT server_id, sample_minute FROM operations_host_samples WHERE observed_at < ? ORDER BY observed_at LIMIT 2000)
            """, time(now.minusSeconds(86400)));
        jdbc.update("""
            DELETE FROM operations_telemetry_aggregates WHERE (server_id, resource_id, bucket_start) IN
                (SELECT server_id, resource_id, bucket_start FROM operations_telemetry_aggregates
                 WHERE bucket_start < ? ORDER BY bucket_start LIMIT 8000)
            """, time(now.minusSeconds(7 * 86400)));
        jdbc.update("""
            DELETE FROM operations_telemetry_boots WHERE (server_id, boot_id) IN
                (SELECT server_id, boot_id FROM operations_telemetry_boots WHERE last_observed_at < ?
                 ORDER BY last_observed_at LIMIT 2000)
            """, time(now.minusSeconds(7 * 86400)));
        if (registryEnabled && !configuredServers.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(configuredServers.size(), "?"));
            jdbc.update("DELETE FROM operations_telemetry_latest WHERE server_id IN (SELECT server_id FROM operations_telemetry_latest "
                + "WHERE server_id NOT IN (" + placeholders + ") ORDER BY server_id LIMIT 2000)",
                configuredServers.toArray());
        }
    }

    String digest(SampleBatch batch) {
        return HexFormat.of().formatHex(TelemetryRegistry.sha256(write(batch)));
    }

    private StoredSample stored(ResultSet rs, int row) throws SQLException {
        return new StoredSample(read(rs.getString("payload_json"), SampleBatch.class), instant(rs, "received_at"), rs.getString("payload_sha256"));
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Telemetry serialization failed.");
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored telemetry is invalid.");
        }
    }

    private static OffsetDateTime time(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, OffsetDateTime.class).toInstant();
    }
}

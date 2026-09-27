package com.ai.infrastructure.connector.rest.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIntegrationStateRepository implements IntegrationStateRepository {

    private final Map<String, SyncState> syncStates = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> records = new ConcurrentHashMap<>();
    private final Map<String, IndexWorkState> work = new ConcurrentHashMap<>();
    private final Map<String, WebhookEvent> events = new ConcurrentHashMap<>();
    private final Map<String, Long> eventRejections = new ConcurrentHashMap<>();

    @Override
    public void startSync(String sourceId, String runId, String sourceVersion) {
        Instant now = Instant.now();
        SyncState previous = syncStates.get(sourceId);
        syncStates.put(sourceId, new SyncState(
            sourceId,
            "RUNNING",
            previous != null ? previous.cursor() : null,
            sourceVersion,
            previous != null ? previous.providerCorrelationHeader() : null,
            previous != null ? previous.providerCorrelationValue() : null,
            previous != null ? previous.counts() : emptyCounts(),
            now,
            previous != null ? previous.lastSuccessAt() : null,
            previous != null ? previous.lastErrorAt() : null,
            null,
            null,
            now
        ));
    }

    @Override
    public void completeSync(String sourceId, String runId, String cursor, SyncCounts counts) {
        Instant now = Instant.now();
        SyncState previous = syncStates.get(sourceId);
        syncStates.put(sourceId, new SyncState(
            sourceId,
            counts.failedWorkCount() > 0 ? "PARTIAL" : "COMPLETED",
            cursor,
            previous != null ? previous.sourceVersion() : null,
            previous != null ? previous.providerCorrelationHeader() : null,
            previous != null ? previous.providerCorrelationValue() : null,
            counts,
            previous != null ? previous.lastStartedAt() : now,
            now,
            previous != null ? previous.lastErrorAt() : null,
            counts.failedWorkCount() > 0 ? "INDEXING_WORK_FAILED" : null,
            null,
            now
        ));
    }

    @Override
    public void failSync(String sourceId, String errorClass, String message, SyncCounts counts) {
        Instant now = Instant.now();
        SyncState previous = syncStates.get(sourceId);
        syncStates.put(sourceId, new SyncState(
            sourceId,
            "FAILED",
            previous != null ? previous.cursor() : null,
            previous != null ? previous.sourceVersion() : null,
            previous != null ? previous.providerCorrelationHeader() : null,
            previous != null ? previous.providerCorrelationValue() : null,
            counts != null ? counts : previous != null ? previous.counts() : emptyCounts(),
            previous != null ? previous.lastStartedAt() : now,
            previous != null ? previous.lastSuccessAt() : null,
            now,
            errorClass,
            message,
            now
        ));
    }

    @Override
    public void recordProviderCorrelation(String sourceId, String headerName, String headerValue) {
        syncStates.computeIfPresent(sourceId, (ignored, previous) -> new SyncState(
            previous.sourceId(), previous.status(), previous.cursor(), previous.sourceVersion(),
            headerName, headerValue, previous.counts(), previous.lastStartedAt(), previous.lastSuccessAt(),
            previous.lastErrorAt(), previous.errorClass(), previous.errorMessage(), Instant.now()
        ));
    }

    @Override
    public Optional<SyncState> syncState(String sourceId) {
        return Optional.ofNullable(syncStates.get(sourceId));
    }

    @Override
    public List<SyncState> syncStates() {
        return syncStates.values().stream().sorted(Comparator.comparing(SyncState::sourceId)).toList();
    }

    @Override
    public Set<String> activeRecordIds(String sourceId) {
        return Set.copyOf(records.getOrDefault(sourceId, Map.of()).keySet());
    }

    @Override
    public void markRecordSeen(String sourceId, String recordId, String fingerprint, String runId) {
        records.computeIfAbsent(sourceId, ignored -> new ConcurrentHashMap<>()).put(recordId, fingerprint);
    }

    @Override
    public void markRecordDeleted(String sourceId, String recordId) {
        Map<String, String> sourceRecords = records.get(sourceId);
        if (sourceRecords != null) {
            sourceRecords.remove(recordId);
        }
    }

    @Override
    public void recordWork(String workId, String sourceId, String recordId, String operation, String status) {
        Instant now = Instant.now();
        work.put(workId, new IndexWorkState(workId, sourceId, recordId, operation, status, null, now, now));
    }

    @Override
    public void updateWork(String workId, String status, String errorCode) {
        work.computeIfPresent(workId, (ignored, previous) -> new IndexWorkState(
            previous.workId(), previous.sourceId(), previous.recordId(), previous.operation(), status,
            errorCode, previous.createdAt(), Instant.now()
        ));
    }

    @Override
    public List<IndexWorkState> workStates(String sourceId) {
        return work.values().stream()
            .filter(item -> item.sourceId().equals(sourceId))
            .sorted(Comparator.comparing(IndexWorkState::createdAt).reversed())
            .toList();
    }

    @Override
    public boolean registerEvent(WebhookEvent event) {
        return events.putIfAbsent(eventKey(event.sourceId(), event.eventId()), event) == null;
    }

    @Override
    public void markEventDuplicate(String sourceId, String eventId) {
        events.computeIfPresent(eventKey(sourceId, eventId), (ignored, previous) -> previous.withDuplicate());
    }

    @Override
    public void markEventReplay(String sourceId, String eventId) {
        events.computeIfPresent(eventKey(sourceId, eventId), (ignored, previous) -> previous.withReplay());
    }

    @Override
    public void beginEventAttempt(String sourceId, String eventId, String status) {
        events.computeIfPresent(eventKey(sourceId, eventId), (ignored, previous) -> previous.withAttempt(status));
    }

    @Override
    public void updateEvent(String sourceId, String eventId, String status, String errorClass) {
        events.computeIfPresent(eventKey(sourceId, eventId), (ignored, previous) -> previous.withStatus(status, errorClass));
    }

    @Override
    public Optional<WebhookEvent> event(String sourceId, String eventId) {
        return Optional.ofNullable(events.get(eventKey(sourceId, eventId)));
    }

    @Override
    public List<WebhookEvent> recentEvents(String sourceId, int limit) {
        List<WebhookEvent> out = new ArrayList<>(events.values().stream()
            .filter(item -> item.sourceId().equals(sourceId))
            .sorted(Comparator.comparing(WebhookEvent::receivedAt).reversed())
            .limit(Math.max(1, limit))
            .toList());
        return List.copyOf(out);
    }

    @Override
    public void recordEventRejection(String sourceId, String errorClass) {
        eventRejections.merge(eventKey(sourceId, errorClass), 1L, Long::sum);
    }

    @Override
    public long eventRejectionCount(String sourceId) {
        String prefix = sourceId + "\u0000";
        return eventRejections.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .mapToLong(Map.Entry::getValue)
            .sum();
    }

    private String eventKey(String sourceId, String eventId) {
        return sourceId + "\u0000" + eventId;
    }

    private SyncCounts emptyCounts() {
        return new SyncCounts(0, 0, 0, 0, 0, 0, 0);
    }
}

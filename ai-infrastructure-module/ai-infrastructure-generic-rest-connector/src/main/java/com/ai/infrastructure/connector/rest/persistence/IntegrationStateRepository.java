package com.ai.infrastructure.connector.rest.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface IntegrationStateRepository {

    void startSync(String sourceId, String runId, String sourceVersion);

    void completeSync(String sourceId, String runId, String cursor, SyncCounts counts);

    void failSync(String sourceId, String errorClass, String message, SyncCounts counts);

    void recordProviderCorrelation(String sourceId, String headerName, String headerValue);

    Optional<SyncState> syncState(String sourceId);

    List<SyncState> syncStates();

    Set<String> activeRecordIds(String sourceId);

    void markRecordSeen(String sourceId, String recordId, String fingerprint, String runId);

    void markRecordDeleted(String sourceId, String recordId);

    void recordWork(String workId, String sourceId, String recordId, String operation, String status);

    void updateWork(String workId, String status, String errorCode);

    List<IndexWorkState> workStates(String sourceId);

    boolean registerEvent(WebhookEvent event);

    void markEventDuplicate(String sourceId, String eventId);

    void markEventReplay(String sourceId, String eventId);

    void beginEventAttempt(String sourceId, String eventId, String status);

    void updateEvent(String sourceId, String eventId, String status, String errorClass);

    Optional<WebhookEvent> event(String sourceId, String eventId);

    List<WebhookEvent> recentEvents(String sourceId, int limit);

    void recordEventRejection(String sourceId, String errorClass);

    long eventRejectionCount(String sourceId);

    record SyncCounts(
        int sourceCount,
        int normalizedCount,
        int indexedCount,
        int deletedCount,
        int acceptedWorkCount,
        int completedWorkCount,
        int failedWorkCount
    ) {
    }

    record SyncState(
        String sourceId,
        String status,
        String cursor,
        String sourceVersion,
        String providerCorrelationHeader,
        String providerCorrelationValue,
        SyncCounts counts,
        Instant lastStartedAt,
        Instant lastSuccessAt,
        Instant lastErrorAt,
        String errorClass,
        String errorMessage,
        Instant updatedAt
    ) {
    }

    record IndexWorkState(
        String workId,
        String sourceId,
        String recordId,
        String operation,
        String status,
        String errorCode,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    record WebhookEvent(
        String sourceId,
        String eventId,
        String eventType,
        String recordKey,
        String resourceFingerprint,
        String payloadSha256,
        String status,
        int attemptCount,
        int duplicateCount,
        int replayCount,
        String errorClass,
        Instant receivedAt,
        Instant updatedAt
    ) {
        public WebhookEvent withStatus(String nextStatus, String nextErrorClass) {
            return new WebhookEvent(
                sourceId,
                eventId,
                eventType,
                recordKey,
                resourceFingerprint,
                payloadSha256,
                nextStatus,
                attemptCount,
                duplicateCount,
                replayCount,
                nextErrorClass,
                receivedAt,
                Instant.now()
            );
        }

        public WebhookEvent withAttempt(String nextStatus) {
            return new WebhookEvent(
                sourceId,
                eventId,
                eventType,
                recordKey,
                resourceFingerprint,
                payloadSha256,
                nextStatus,
                attemptCount + 1,
                duplicateCount,
                replayCount,
                null,
                receivedAt,
                Instant.now()
            );
        }

        public WebhookEvent withDuplicate() {
            return new WebhookEvent(
                sourceId, eventId, eventType, recordKey, resourceFingerprint, payloadSha256, status,
                attemptCount, duplicateCount + 1, replayCount, errorClass, receivedAt, Instant.now()
            );
        }

        public WebhookEvent withReplay() {
            return new WebhookEvent(
                sourceId, eventId, eventType, recordKey, resourceFingerprint, payloadSha256, status,
                attemptCount, duplicateCount, replayCount + 1, errorClass, receivedAt, Instant.now()
            );
        }
    }
}

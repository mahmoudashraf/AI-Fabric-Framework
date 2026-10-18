package com.ai.infrastructure.connector.rest.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import com.ai.infrastructure.connector.rest.service.ProviderCallException;
import com.ai.infrastructure.connector.rest.service.ProviderErrorClass;

public interface IntegrationStateRepository {

    default <T> T executeAdminCommand(String idempotencyKey, String operation, String resource,
                                      Class<T> resultType, Supplier<T> mutation) {
        throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0,
            "DURABLE_INTEGRATION_COMMAND_STORE_REQUIRED");
    }

    static String requireAdminCommandKey(String value) {
        if (value == null || value.isEmpty() || value.length() > 128
            || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0,
                "Idempotency-Key is required and must contain 1 to 128 visible ASCII characters.");
        }
        return value;
    }

    void startSync(String sourceId, String runId, String sourceVersion);

    void completeSync(String sourceId, String runId, String cursor, SyncCounts counts);

    void failSync(String sourceId, String errorClass, String message, SyncCounts counts);

    void recordProviderCorrelation(String sourceId, String headerName, String headerValue);

    Optional<SyncState> syncState(String sourceId);

    List<SyncState> syncStates();

    Set<String> activeRecordIds(String sourceId);

    void markRecordSeen(String sourceId, String recordId, String fingerprint, String runId);

    void markRecordDeleted(String sourceId, String recordId);

    /**
     * Atomically applies the queryable, safe projection produced by one successful source reconciliation.
     * The projection contains only fields explicitly allowlisted by the data-source mapping.
     */
    void applyProjectionChanges(
        String sourceId,
        String runId,
        List<SourceProjectionRecord> upserts,
        Set<String> deletes
    );

    ProjectionQueryResult queryProjection(String sourceId, ProjectionQuery query);

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

    record SourceProjectionRecord(
        String recordId,
        String fingerprint,
        String content,
        Map<String, Object> entity,
        Map<String, Object> metadata,
        Instant updatedAt
    ) {
    }

    record ProjectionQuery(
        List<ProjectionCriterion> criteria,
        int limit
    ) {
    }

    record ProjectionCriterion(
        List<String> fields,
        ProjectionOperator operator,
        List<String> values
    ) {
    }

    enum ProjectionOperator {
        EQUALS_IGNORE_CASE,
        NUMBER_LESS_THAN_OR_EQUAL,
        ANY_TOKEN_EQUALS_IGNORE_CASE
    }

    record ProjectionQueryResult(
        List<SourceProjectionRecord> records,
        long totalMatches
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

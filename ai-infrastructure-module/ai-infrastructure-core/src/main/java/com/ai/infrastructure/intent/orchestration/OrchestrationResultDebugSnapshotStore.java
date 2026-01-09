package com.ai.infrastructure.intent.orchestration;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores a minimal snapshot of the most recently produced orchestration result.
 *
 * <p>This exists to help test harnesses print canonical error codes in CI logs without
 * depending on LLM message wording or wrapper shapes. The snapshot intentionally excludes
 * message and data to reduce risk of leaking sensitive content.</p>
 */
public final class OrchestrationResultDebugSnapshotStore {

    private static final AtomicReference<Snapshot> LAST = new AtomicReference<>();

    private OrchestrationResultDebugSnapshotStore() {}

    public static void record(String requestId, OrchestrationResult result) {
        if (result == null) {
            return;
        }
        LAST.set(new Snapshot(
            requestId,
            result.getType() != null ? result.getType().name() : null,
            result.isSuccess(),
            result.getErrorCode(),
            Instant.now().toString()
        ));
    }

    public static Snapshot getLast() {
        return LAST.get();
    }

    public record Snapshot(
        String requestId,
        String type,
        boolean success,
        String errorCode,
        String recordedAt
    ) {
        @Override
        public String toString() {
            return "Snapshot{" +
                "requestId=" + Objects.toString(requestId, "null") +
                ", type=" + Objects.toString(type, "null") +
                ", success=" + success +
                ", errorCode=" + Objects.toString(errorCode, "null") +
                ", recordedAt=" + Objects.toString(recordedAt, "null") +
                '}';
        }
    }
}


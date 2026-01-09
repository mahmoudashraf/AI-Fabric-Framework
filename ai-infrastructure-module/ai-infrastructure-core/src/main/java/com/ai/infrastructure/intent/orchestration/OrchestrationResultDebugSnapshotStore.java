package com.ai.infrastructure.intent.orchestration;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Stores a minimal snapshot of the most recently produced orchestration result.
 *
 * <p>This exists to help test harnesses print canonical error codes in CI logs without
 * depending on LLM message wording or wrapper shapes. The snapshot intentionally excludes
 * message and data to reduce risk of leaking sensitive content.</p>
 */
public final class OrchestrationResultDebugSnapshotStore {

    private static final int MAX_RECENT = 25;

    private static final Object LOCK = new Object();
    private static final Deque<Snapshot> RECENT = new ArrayDeque<>(MAX_RECENT);
    private static volatile Snapshot last;

    private OrchestrationResultDebugSnapshotStore() {}

    public static void record(String requestId, OrchestrationResult result) {
        if (result == null) {
            return;
        }
        Snapshot snapshot = new Snapshot(
            requestId,
            result.getType() != null ? result.getType().name() : null,
            result.isSuccess(),
            result.getErrorCode(),
            Instant.now().toString()
        );
        synchronized (LOCK) {
            last = snapshot;
            RECENT.addLast(snapshot);
            while (RECENT.size() > MAX_RECENT) {
                RECENT.removeFirst();
            }
        }
    }

    public static Snapshot getLast() {
        return last;
    }

    /**
     * Returns a defensive copy of the most recent snapshots (oldest -> newest).
     */
    public static List<Snapshot> getRecent() {
        synchronized (LOCK) {
            return new ArrayList<>(RECENT);
        }
    }

    /**
     * Clears stored snapshots (useful for isolating test output).
     */
    public static void clear() {
        synchronized (LOCK) {
            last = null;
            RECENT.clear();
        }
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


package com.ai.fabric.vectorization.runner.service;

import java.util.Set;

public record DataSyncWorkStatus(
    String workId,
    String status,
    String entityType,
    String entityId,
    String errorCode,
    int retryCount,
    int maxRetries
) {

    private static final Set<String> SUCCESSFUL_TERMINAL =
        Set.of("COMPLETED", "SUPERSEDED");
    private static final Set<String> IN_FLIGHT =
        Set.of("COMMIT_PENDING", "PENDING", "PROCESSING");

    public boolean isSuccessfulTerminal() {
        return SUCCESSFUL_TERMINAL.contains(status);
    }

    public boolean isInFlight() {
        return IN_FLIGHT.contains(status);
    }

    public boolean isDeadLetter() {
        return "DEAD_LETTER".equals(status);
    }
}

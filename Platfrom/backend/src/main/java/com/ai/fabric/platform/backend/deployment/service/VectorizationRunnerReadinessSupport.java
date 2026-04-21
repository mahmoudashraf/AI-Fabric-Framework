package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;

import java.time.Instant;

final class VectorizationRunnerReadinessSupport {

    private VectorizationRunnerReadinessSupport() {
    }

    static boolean hasActiveRegistration(VectorizationRunnerSummary runner) {
        return runner != null && "ACTIVE".equalsIgnoreCase(runner.registrationStatus());
    }

    static boolean hasValidToken(VectorizationRunnerSummary runner, Instant now) {
        return runner != null && (runner.tokenExpiresAt() == null || !runner.tokenExpiresAt().isBefore(now));
    }

    static boolean hasActiveSession(VectorizationRunnerSummary runner, Instant now) {
        return runner != null && runner.lastSessionExpiresAt() != null && runner.lastSessionExpiresAt().isAfter(now);
    }

    static boolean isExecutionReady(VectorizationRunnerSummary runner, Instant now) {
        return hasActiveRegistration(runner) && (hasValidToken(runner, now) || hasActiveSession(runner, now));
    }
}

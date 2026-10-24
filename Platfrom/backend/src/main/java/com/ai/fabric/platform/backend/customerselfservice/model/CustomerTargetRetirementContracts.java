package com.ai.fabric.platform.backend.customerselfservice.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** Legacy wire shapes only. Historical records never grant permission to execute retirement. */
public final class CustomerTargetRetirementContracts {
    private CustomerTargetRetirementContracts() { }

    public record PlanRequest(String targetProfileId, List<String> releaseIds, Instant drainDeadline,
                              JsonNode retention, String operationKey, String reason) { }

    public record Command(String requestFingerprint) { }

    public record Summary(String id, String deploymentId, String targetProfileId, String requestFingerprint,
                          String state, String step, Instant drainDeadline, JsonNode retention,
                          int attempt, Instant nextRetryAt, String errorCode, List<String> blockers,
                          boolean consumesCapacity, boolean customerCommandsAllowed,
                          Instant createdAt, Instant startedAt,
                          Instant completedAt, Instant updatedAt) { }
}

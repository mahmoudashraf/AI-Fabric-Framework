package com.ai.fabric.runtime.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record ManagedTaskResponse(String runId, String taskCode, String status, String configurationVersion,
    String taskConfigurationHash, String outputSchemaVersion, JsonNode output, List<Evidence> evidence,
    Failure failure, boolean sideEffectsExecuted, Instant startedAt, Instant completedAt) {
    public record Evidence(String evidenceId, String source, String vectorSpace) {}
    public record Failure(String code, String message) {}
}

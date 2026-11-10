package com.ai.fabric.runtime.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import ai.fabric.execution.action.ActionProposalView;
import ai.fabric.execution.review.ReviewTaskView;
import java.time.Instant;
import java.util.List;

public record ManagedTaskResponse(String runId, String taskCode, String status, String configurationVersion,
    String taskConfigurationHash, String outputSchemaVersion, JsonNode output, List<Evidence> evidence,
    Failure failure, boolean sideEffectsExecuted, Instant startedAt, Instant completedAt,
    ActionProposalView actionProposal, List<ActionExecution> actionExecutions, String sideEffectsStatus,
    JsonNode inputRequirement, ReviewTaskView review) {
    public record Evidence(String evidenceId, String source, String vectorSpace) {}
    public record Failure(String code, String message) {}
    public record ActionExecution(String executionId, String actionName, String status, JsonNode outcome,
                                  Instant startedAt, Instant completedAt) {}
}

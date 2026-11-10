package com.ai.fabric.runtime.tasks;

import ai.fabric.execution.action.ActionProposalDecisionResult;
import java.util.List;

public record ManagedTaskDecisionResponse(String taskCode, String configurationVersion, String taskConfigurationHash,
    String status, ActionProposalDecisionResult decision, List<ManagedTaskResponse.ActionExecution> actionExecutions,
    ManagedTaskResponse.Failure failure) {}

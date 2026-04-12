package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record DeploymentPocTraceSummary(
    String resultType,
    boolean success,
    String message,
    String errorCode,
    String executedAction,
    String answer,
    String actionSummary,
    String routingStrategy,
    List<String> vectorSpaces,
    List<String> candidateVectorSpaces,
    List<String> childResultTypes,
    Long runtimeRequestDurationMs,
    Long runtimeAuthResolutionMs,
    Long runtimeContextBuildMs,
    Long runtimeOrchestrationCallDurationMs,
    Long runtimeNonPipelineDurationMs,
    Long pipelineDurationMs,
    Long retrievalProcessingTimeMs,
    Long embeddingProcessingTimeMs,
    Long embeddingProviderProcessingTimeMs,
    Boolean embeddingCacheHit,
    String embeddingProviderName,
    String embeddingModel,
    Long searchProcessingTimeMs,
    Map<String, Long> stepDurationsMs,
    int documentCount,
    List<DeploymentPocTraceDocumentSummary> documents,
    JsonNode actionValidation
) {
}

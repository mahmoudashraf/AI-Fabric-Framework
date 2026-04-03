package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

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
    int documentCount,
    List<DeploymentPocTraceDocumentSummary> documents,
    JsonNode actionValidation
) {
}

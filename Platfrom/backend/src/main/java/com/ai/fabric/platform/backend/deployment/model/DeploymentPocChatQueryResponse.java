package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

public record DeploymentPocChatQueryResponse(
    boolean success,
    String message,
    String conversationId,
    String sessionId,
    JsonNode result,
    DeploymentPocTraceSummary traceSummary
) {
}

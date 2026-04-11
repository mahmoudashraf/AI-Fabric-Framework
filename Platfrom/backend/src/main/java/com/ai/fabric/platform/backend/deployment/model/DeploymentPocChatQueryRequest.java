package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

public record DeploymentPocChatQueryRequest(
    String query,
    String conversationId,
    String mode,
    String position,
    JsonNode promptPreview,
    DeploymentPocAuthPath authPath
) {
}

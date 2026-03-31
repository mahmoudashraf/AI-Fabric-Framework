package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocChatQueryRequest(
    String query,
    String conversationId,
    String mode,
    String position
) {
}

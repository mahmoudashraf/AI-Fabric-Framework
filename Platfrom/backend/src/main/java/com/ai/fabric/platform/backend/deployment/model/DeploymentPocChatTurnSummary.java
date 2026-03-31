package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocChatTurnSummary(
    String timestamp,
    String userQuery,
    String aiResponse
) {
}

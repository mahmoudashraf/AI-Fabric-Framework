package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocConversationResponse(
    String id,
    String ownerId,
    String status,
    String createdAt,
    String lastInteractionAt,
    List<DeploymentPocChatTurnSummary> turns
) {
}

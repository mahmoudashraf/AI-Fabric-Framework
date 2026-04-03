package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocChatSuggestionsResponse(
    boolean success,
    String message,
    List<String> suggestions,
    String raw
) {
}

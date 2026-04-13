package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocChatSuggestionsRequest(
    String content,
    Integer maxSuggestions,
    DeploymentPocAuthPath authPath
) {
}

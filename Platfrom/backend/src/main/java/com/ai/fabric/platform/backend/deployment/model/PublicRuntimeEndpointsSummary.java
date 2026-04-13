package com.ai.fabric.platform.backend.deployment.model;

public record PublicRuntimeEndpointsSummary(
    String chatBaseUrl,
    String crudBaseUrl,
    String chatQueryUrl,
    String suggestionsUrl,
    String conversationsUrl,
    String conversationItemUrlTemplate,
    String operationalBaseUrl,
    String authContextUrl,
    String authOverviewUrl
) {
}

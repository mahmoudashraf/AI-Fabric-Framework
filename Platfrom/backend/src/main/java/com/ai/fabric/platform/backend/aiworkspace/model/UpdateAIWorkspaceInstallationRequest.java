package com.ai.fabric.platform.backend.aiworkspace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record UpdateAIWorkspaceInstallationRequest(
    String consumerId,
    String displayName,
    String experiencePackCode,
    String experiencePackVersion,
    String connectionMode,
    String connectionProfileCode,
    String connectionProfileVersion,
    JsonNode connectionConfiguration,
    List<String> allowedOrigins,
    JsonNode configuration,
    String deliveryMode,
    String appSlug,
    List<String> customDomains,
    String interactionPolicy,
    String authProfileCode,
    String authProfileVersion,
    JsonNode authConfiguration,
    Long rowVersion
) {
}

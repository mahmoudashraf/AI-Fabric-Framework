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
    Long rowVersion
) {
}

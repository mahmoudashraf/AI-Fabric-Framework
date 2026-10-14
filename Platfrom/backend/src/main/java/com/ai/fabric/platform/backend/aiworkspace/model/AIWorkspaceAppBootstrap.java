package com.ai.fabric.platform.backend.aiworkspace.model;

import com.fasterxml.jackson.databind.JsonNode;

public record AIWorkspaceAppBootstrap(
    String schemaVersion,
    String installationId,
    String displayName,
    String interactionPolicy,
    JsonNode delivery,
    JsonNode authentication,
    JsonNode installer
) {
}

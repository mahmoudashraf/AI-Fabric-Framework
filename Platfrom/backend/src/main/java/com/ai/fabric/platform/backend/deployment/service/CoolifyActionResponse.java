package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CoolifyActionResponse(
    String message,
    String deploymentUuid,
    JsonNode raw
) {
}

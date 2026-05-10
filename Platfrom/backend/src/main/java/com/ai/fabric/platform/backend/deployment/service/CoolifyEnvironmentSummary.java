package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CoolifyEnvironmentSummary(
    String uuid,
    String name,
    String projectUuid,
    String description,
    JsonNode raw
) {
}

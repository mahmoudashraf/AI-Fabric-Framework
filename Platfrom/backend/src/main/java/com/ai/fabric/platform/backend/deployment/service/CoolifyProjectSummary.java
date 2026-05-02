package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CoolifyProjectSummary(
    String uuid,
    String name,
    String description,
    JsonNode raw
) {
}

package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CoolifyApplicationSummary(
    String uuid,
    String name,
    String fqdn,
    String status,
    String imageRepository,
    String imageTag,
    JsonNode raw
) {
}

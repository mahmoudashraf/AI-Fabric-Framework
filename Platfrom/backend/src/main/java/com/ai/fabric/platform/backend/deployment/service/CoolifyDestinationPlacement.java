package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CoolifyDestinationPlacement(
    String destinationUuid,
    String serverUuid,
    String verificationSource,
    JsonNode raw
) {
}

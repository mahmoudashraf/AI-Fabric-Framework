package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record ManagedVectorProvisioningResult(
    JsonNode effectiveProviderConfig,
    JsonNode details
) {
}

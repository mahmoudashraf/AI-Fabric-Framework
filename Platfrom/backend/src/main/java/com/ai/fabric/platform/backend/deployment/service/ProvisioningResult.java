package com.ai.fabric.platform.backend.deployment.service;

public record ProvisioningResult(
    String status,
    String target,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    String detailsJson
) {
}

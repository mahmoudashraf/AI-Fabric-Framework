package com.ai.fabric.platform.backend.marketplace.model;

public record PlatformManagedInferenceEndpointSummary(
    String id,
    String profileRef,
    String serviceId,
    String endpointPurpose,
    String displayName,
    String providerType,
    String protocolType,
    String baseUrl,
    String deploymentName,
    String apiVersion,
    String secretName,
    String status
) {
}

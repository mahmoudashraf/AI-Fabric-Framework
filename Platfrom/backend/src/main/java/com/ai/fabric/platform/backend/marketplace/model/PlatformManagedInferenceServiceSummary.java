package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record PlatformManagedInferenceServiceSummary(
    String id,
    String serviceRef,
    String displayName,
    String serviceKind,
    String deploymentMode,
    String providerType,
    String protocolType,
    String modelId,
    String environmentScope,
    String tierScope,
    String deploymentId,
    Integer desiredReplicas,
    Integer actualReplicas,
    Integer minReplicas,
    Integer maxReplicas,
    String autoscalingMode,
    String baseUrl,
    String privateNetworkUrl,
    String healthPath,
    String secretName,
    String status,
    List<PlatformManagedInferenceEndpointSummary> endpoints
) {
}

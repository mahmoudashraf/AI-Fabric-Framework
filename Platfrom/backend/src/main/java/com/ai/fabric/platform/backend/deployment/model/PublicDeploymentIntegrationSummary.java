package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentIntegrationSummary(
    String preferredIntegrationMode,
    PublicRuntimePostureSummary posture,
    PublicRuntimeEndpointsSummary runtime,
    PublicTrustedBackendAccessSummary trustedBackend,
    PublicRuntimeTokenSummary publicRuntime,
    PublicIntegrationPathSummary paths,
    String guidance
) {
}

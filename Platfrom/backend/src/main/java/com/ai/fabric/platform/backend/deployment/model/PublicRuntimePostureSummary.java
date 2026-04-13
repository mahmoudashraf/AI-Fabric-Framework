package com.ai.fabric.platform.backend.deployment.model;

public record PublicRuntimePostureSummary(
    String runtimeAuthMode,
    boolean verifiedAuthContextRequired,
    boolean hostBackedRuntimeRequired,
    boolean directConnectorAccessSupported
) {
}

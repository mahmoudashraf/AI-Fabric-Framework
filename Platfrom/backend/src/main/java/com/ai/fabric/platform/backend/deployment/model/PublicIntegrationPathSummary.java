package com.ai.fabric.platform.backend.deployment.model;

public record PublicIntegrationPathSummary(
    boolean connectorInternalOnly,
    boolean browserDirectRuntimeAccessSupported,
    String browserDirectChatBaseUrl,
    String browserDirectCrudBaseUrl,
    String backendMediatedRuntimeBaseUrl
) {
}

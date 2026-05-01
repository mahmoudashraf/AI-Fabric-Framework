package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentProviderResourceLogsSummary(
    String handleId,
    DeploymentProviderType providerType,
    String providerResourceUuid,
    int lines,
    String logs,
    Instant fetchedAt
) {
}

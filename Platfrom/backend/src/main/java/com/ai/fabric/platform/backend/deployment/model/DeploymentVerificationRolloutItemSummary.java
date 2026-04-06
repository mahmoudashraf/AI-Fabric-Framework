package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentVerificationRolloutItemSummary(
    String key,
    String displayName,
    String description,
    String verificationProfile,
    boolean writeVerificationSupported,
    String deploymentId,
    String environment,
    boolean exists,
    boolean archived,
    boolean verificationReady,
    String deploymentStatus,
    String activeVersionId,
    String latestReleaseStatus,
    String latestProvisioningStatus,
    String latestVerificationStatus,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    String readinessMessage,
    List<String> missingPrerequisites
) {
}

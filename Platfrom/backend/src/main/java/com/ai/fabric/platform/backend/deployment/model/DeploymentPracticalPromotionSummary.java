package com.ai.fabric.platform.backend.deployment.model;

import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;

import java.util.List;

public record DeploymentPracticalPromotionSummary(
    String status,
    String message,
    String deploymentId,
    String versionId,
    String stagingTargetProfileId,
    String productionTargetProfileId,
    String stagingReleaseId,
    String stagingReleaseStatus,
    String productionReleaseId,
    String productionReleaseStatus,
    PlatformConsumerSummary consumer,
    List<DeploymentProviderResourceLifecycleSummary> resources
) {
}

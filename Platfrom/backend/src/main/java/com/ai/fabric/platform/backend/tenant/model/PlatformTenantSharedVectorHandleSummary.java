package com.ai.fabric.platform.backend.tenant.model;

import java.time.Instant;

public record PlatformTenantSharedVectorHandleSummary(
    String id,
    String resourceStatus,
    String vendor,
    String vectorStrategy,
    String vectorProvisioningMode,
    String vectorStoragePosture,
    String scopeType,
    String rootResourceLabel,
    String rootResourceValue,
    String scopePrefix,
    String tenantHandle,
    String scopePattern,
    String lifecycleOwner,
    String deploymentId,
    String deploymentVersionId,
    String deploymentReleaseId,
    String summaryStatus,
    String summaryMessage,
    Instant createdAt,
    Instant updatedAt,
    boolean cleanupEligible
) {
}

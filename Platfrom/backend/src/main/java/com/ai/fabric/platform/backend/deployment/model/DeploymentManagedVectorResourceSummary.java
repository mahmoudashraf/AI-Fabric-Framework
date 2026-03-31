package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record DeploymentManagedVectorResourceSummary(
    String id,
    String deploymentId,
    String deploymentVersionId,
    String deploymentReleaseId,
    String vendor,
    String vectorStrategy,
    String vectorProvisioningMode,
    String managedMode,
    String resourceType,
    String resourceName,
    String resourceReference,
    String endpoint,
    String resourceStatus,
    String provisioningState,
    List<String> secretReferenceNames,
    JsonNode details,
    Instant createdAt,
    Instant updatedAt
) {
}

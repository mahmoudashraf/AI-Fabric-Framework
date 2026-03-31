package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record BulkDeploymentActionResponse(
    String action,
    int requestedCount,
    int succeededCount,
    int failedCount,
    List<BulkDeploymentActionItemSummary> results
) {
}

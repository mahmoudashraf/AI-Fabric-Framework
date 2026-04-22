package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceRailwayDeploymentSummary(
    String id,
    String status,
    String url,
    String staticUrl,
    String createdAt
) {
}

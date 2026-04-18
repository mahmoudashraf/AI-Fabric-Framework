package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceProbeSummary(
    String status,
    String method,
    String endpoint,
    int statusCode,
    String message,
    String checkedAt
) {
}


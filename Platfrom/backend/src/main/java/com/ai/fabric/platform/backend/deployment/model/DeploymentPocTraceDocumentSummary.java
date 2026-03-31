package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocTraceDocumentSummary(
    String id,
    String title,
    String vectorSpace,
    Double score,
    String source,
    String url
) {
}

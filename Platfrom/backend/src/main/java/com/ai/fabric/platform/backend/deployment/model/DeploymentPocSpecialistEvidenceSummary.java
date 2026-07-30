package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocSpecialistEvidenceSummary(
    String documentId,
    String title,
    Double relevanceScore,
    String source,
    String vectorSpace
) {
}

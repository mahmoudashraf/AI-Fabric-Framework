package com.ai.fabric.runtime.web.dto;

public record DeploymentKnowledgeEvidenceResponse(
    String documentId,
    String title,
    Double relevanceScore,
    String source,
    String vectorSpace
) {
}

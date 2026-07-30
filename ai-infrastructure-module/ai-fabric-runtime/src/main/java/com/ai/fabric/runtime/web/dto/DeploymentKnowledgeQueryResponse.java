package com.ai.fabric.runtime.web.dto;

import java.util.List;

public record DeploymentKnowledgeQueryResponse(
    String status,
    String answer,
    String specialistName,
    String specialistVersion,
    String correlationId,
    List<DeploymentKnowledgeEvidenceResponse> evidence,
    String reasonCode
) {
    public DeploymentKnowledgeQueryResponse {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}

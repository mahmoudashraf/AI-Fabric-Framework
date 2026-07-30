package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocSpecialistQueryResponse(
    String status,
    String answer,
    String specialistName,
    String specialistVersion,
    String correlationId,
    List<DeploymentPocSpecialistEvidenceSummary> evidence,
    String reasonCode
) {
    public DeploymentPocSpecialistQueryResponse {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}

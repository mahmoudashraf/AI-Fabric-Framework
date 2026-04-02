package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentHostedVerificationDiagnosticsSummary(
    String headline,
    int passCount,
    int warningCount,
    int failCount,
    String lastPassMessage,
    String lastWarningMessage,
    String lastFailureMessage,
    List<DeploymentHostedVerificationStepSummary> steps
) {
}

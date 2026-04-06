package com.ai.fabric.platform.backend.deployment.model;

import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;

import java.util.List;

public record DeploymentSecretUsageItemSummary(
    String secretName,
    String displayName,
    boolean required,
    boolean present,
    String source,
    String status,
    List<String> usedByServices,
    List<String> configPaths,
    String summaryMessage,
    String secretPurpose,
    DeploymentSecretResolutionSummary effectiveResolution
) {
}

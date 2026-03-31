package com.ai.fabric.platform.backend.deployment.model;

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
    String summaryMessage
) {
}

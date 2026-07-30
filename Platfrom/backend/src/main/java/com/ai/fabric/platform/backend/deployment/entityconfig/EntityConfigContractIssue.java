package com.ai.fabric.platform.backend.deployment.entityconfig;

public record EntityConfigContractIssue(
    String code,
    String path,
    String message
) {
}

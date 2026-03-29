package com.ai.fabric.platform.backend.deployment.model;

public record DraftValidationIssue(
    String severity,
    String section,
    String code,
    String path,
    String message
) {
}

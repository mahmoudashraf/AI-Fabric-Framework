package com.ai.fabric.platform.backend.aiworkspace.model;

public record AIWorkspaceReadinessCheck(
    String code,
    String status,
    String message
) {
}

package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocResetCapabilities(
    boolean clearRuntimeVectors,
    boolean resetConversation
) {
}

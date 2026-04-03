package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocRuntimeResetResponse(
    boolean success,
    boolean clearedVectors,
    long removedVectors,
    String message,
    List<String> warnings
) {
}

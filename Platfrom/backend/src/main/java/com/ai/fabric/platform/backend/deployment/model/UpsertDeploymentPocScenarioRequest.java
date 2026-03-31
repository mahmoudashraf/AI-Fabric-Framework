package com.ai.fabric.platform.backend.deployment.model;

public record UpsertDeploymentPocScenarioRequest(
    String title,
    String category,
    String prompt,
    String expectedOutcome
) {
}

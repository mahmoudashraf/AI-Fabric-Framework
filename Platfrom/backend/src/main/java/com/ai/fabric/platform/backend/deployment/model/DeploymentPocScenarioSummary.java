package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentPocScenarioSummary(
    String id,
    String source,
    String title,
    String category,
    String prompt,
    String expectedOutcome,
    boolean editable,
    Instant createdAt
) {
}

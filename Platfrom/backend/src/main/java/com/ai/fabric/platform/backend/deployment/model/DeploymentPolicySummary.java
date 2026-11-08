package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPolicySummary(List<String> allowedEnvironments, List<String> tags) {
    public static DeploymentPolicySummary from(DeploymentEnvironmentPolicy policy) {
        return policy == DeploymentEnvironmentPolicy.STAGING_ONLY
            ? new DeploymentPolicySummary(List.of("staging"), List.of("staging-only"))
            : new DeploymentPolicySummary(List.of("staging", "production"), List.of());
    }
}

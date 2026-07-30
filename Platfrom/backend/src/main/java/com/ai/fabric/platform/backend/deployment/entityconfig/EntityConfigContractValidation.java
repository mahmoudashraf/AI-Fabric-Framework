package com.ai.fabric.platform.backend.deployment.entityconfig;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public record EntityConfigContractValidation(
    EntityConfigContractV04 contract,
    ObjectNode normalizedPlatformConfig,
    ObjectNode runtimeConfig,
    List<EntityConfigContractIssue> issues
) {

    public EntityConfigContractValidation {
        issues = List.copyOf(issues);
    }

    public boolean valid() {
        return issues.isEmpty() && contract != null && normalizedPlatformConfig != null && runtimeConfig != null;
    }
}

package com.ai.fabric.platform.backend.deployment.entityconfig;

import java.util.List;

public class EntityConfigContractException extends IllegalArgumentException {

    private final List<EntityConfigContractIssue> issues;

    public EntityConfigContractException(List<EntityConfigContractIssue> issues) {
        super(issues.isEmpty()
            ? "Entity configuration violates AI_ENTITY_CONFIG_V0_4."
            : issues.getFirst().code() + " at " + issues.getFirst().path() + ": " + issues.getFirst().message());
        this.issues = List.copyOf(issues);
    }

    public List<EntityConfigContractIssue> getIssues() {
        return issues;
    }
}

package com.ai.fabric.platform.backend.security.model;

public record DeploymentActivityViewPreferences(
    String categoryFilter,
    String actorRoleFilter,
    String searchTerm
) {
}

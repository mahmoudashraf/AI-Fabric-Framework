package com.ai.fabric.platform.backend.security.model;

public record DeploymentListViewPreferences(
    boolean showArchived,
    String searchTerm,
    String healthFilter,
    String roleFilter,
    String templateFilter
) {
}

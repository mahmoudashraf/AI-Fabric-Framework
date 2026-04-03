package com.ai.fabric.platform.backend.security.model;

public record DeploymentRevisionsViewPreferences(
    String searchTerm,
    String versionStatusFilter,
    String releaseStatusFilter,
    String reindexFilter
) {
}

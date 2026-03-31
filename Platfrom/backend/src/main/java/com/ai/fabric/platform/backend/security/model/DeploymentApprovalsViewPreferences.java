package com.ai.fabric.platform.backend.security.model;

public record DeploymentApprovalsViewPreferences(
    String statusFilter,
    String operationFilter,
    boolean mineOnly,
    String searchTerm
) {
}

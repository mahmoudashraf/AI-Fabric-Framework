package com.ai.fabric.platform.backend.security.model;

import java.time.Instant;
import java.util.List;

public record PlatformUserAccessSummary(
    String id,
    String email,
    String displayName,
    String role,
    String customerId,
    String customerName,
    String customerSlug,
    String status,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt,
    int assignmentCount,
    int adminAssignmentCount,
    int editorAssignmentCount,
    int operatorAssignmentCount,
    int viewerAssignmentCount,
    PlatformUserDeploymentAccessSummary selectedDeploymentAssignment,
    List<PlatformUserDeploymentAccessSummary> assignedDeployments
) {
}

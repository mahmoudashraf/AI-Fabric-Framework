package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;

/** Each reviewed host owns validation of its actual registered requester identity and support features. */
public interface AIWorkspaceSupportReadinessAdapter {
    boolean supports(AIWorkspaceInstallationEntity installation);
    void validate(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment);
    default void validatePublishedProjection(AIWorkspaceInstallationEntity installation, AIWorkspaceAssignmentService.Assignment assignment) {
        validate(installation, assignment);
    }
}

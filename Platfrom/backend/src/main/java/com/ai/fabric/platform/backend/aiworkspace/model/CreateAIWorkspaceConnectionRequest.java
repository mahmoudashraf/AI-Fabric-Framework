package com.ai.fabric.platform.backend.aiworkspace.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAIWorkspaceConnectionRequest(
    @NotBlank @Size(max = 64) String deploymentId,
    @NotBlank @Size(max = 64) String targetProfileId,
    @Size(max = 64) String releaseId,
    @NotBlank @Size(min = 2, max = 255) String displayName,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String operationKey
) {
}

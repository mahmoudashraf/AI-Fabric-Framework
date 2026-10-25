package com.ai.fabric.platform.backend.customerselfservice.model;

import jakarta.validation.constraints.NotBlank;

public final class CustomerDeploymentApplyContracts {
    private CustomerDeploymentApplyContracts() { }

    /** The normal apply engine validates the explicitly selected target and artifact. */
    public record Request(@NotBlank String versionId,
                          @NotBlank String targetProfileId,
                          String sourceArtifactId,
                          @NotBlank String operationKey) { }
}

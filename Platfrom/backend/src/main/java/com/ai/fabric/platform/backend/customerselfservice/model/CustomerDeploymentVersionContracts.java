package com.ai.fabric.platform.backend.customerselfservice.model;

import jakarta.validation.constraints.NotBlank;

public final class CustomerDeploymentVersionContracts {
    private CustomerDeploymentVersionContracts() { }

    public record PublishRequest(@NotBlank String draftId, @NotBlank String targetProfileId,
                                 @NotBlank String operationKey) { }

    public record PublishResult(String deploymentId, String versionId, String versionLabel,
                                String configHash, String targetProfileId) { }
}

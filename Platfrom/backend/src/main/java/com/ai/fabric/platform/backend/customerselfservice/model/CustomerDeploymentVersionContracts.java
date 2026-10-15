package com.ai.fabric.platform.backend.customerselfservice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public final class CustomerDeploymentVersionContracts {
    private CustomerDeploymentVersionContracts() { }

    public record PublishRequest(@NotBlank String targetProfileId,
                                 @PositiveOrZero long expectedEntitlementRevision,
                                 @NotBlank String operationKey) { }

    public record PublishResult(String deploymentId, String versionId, String versionLabel,
                                String configHash, String targetProfileId,
                                long entitlementRevision) { }
}

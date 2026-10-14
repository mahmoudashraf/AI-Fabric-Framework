package com.ai.fabric.platform.backend.customerselfservice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public final class CustomerDeploymentBootstrapContracts {
    private CustomerDeploymentBootstrapContracts() { }

    public record Request(@NotBlank String targetProfileId, @NotBlank String name,
                          @PositiveOrZero long expectedEntitlementRevision, @NotBlank String operationKey) { }

    public record Result(String deploymentId, String tenantId, String offerId, String offerFingerprint,
                         String targetProfileId, long entitlementRevision, String creationStatus) { }
}

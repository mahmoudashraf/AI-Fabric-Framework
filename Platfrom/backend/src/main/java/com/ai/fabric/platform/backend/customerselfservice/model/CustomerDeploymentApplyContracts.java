package com.ai.fabric.platform.backend.customerselfservice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public final class CustomerDeploymentApplyContracts {
    private CustomerDeploymentApplyContracts() { }

    /** The target and runtime artifact are resolved from the server-owned reviewed offer association. */
    public record Request(@NotBlank String versionId,
                          @PositiveOrZero long expectedEntitlementRevision,
                          @NotBlank String operationKey) { }
}

package com.ai.fabric.platform.backend.customerselfservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public final class CustomerDeploymentBootstrapContracts {
    private CustomerDeploymentBootstrapContracts() { }

    public record Request(@NotBlank String targetProfileId, @NotBlank String name,
                          @PositiveOrZero long expectedEntitlementRevision, @NotBlank String operationKey,
                          String aiStateStorageMode) {
        public Request(String targetProfileId, String name, long expectedEntitlementRevision, String operationKey) {
            this(targetProfileId, name, expectedEntitlementRevision, operationKey, null);
        }
    }

    public record Result(String deploymentId, String tenantId, String offerId, String offerFingerprint,
                         String targetProfileId, long entitlementRevision, String creationStatus,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String aiStateStorageMode) {
        public Result(String deploymentId, String tenantId, String offerId, String offerFingerprint,
                      String targetProfileId, long entitlementRevision, String creationStatus) {
            this(deploymentId, tenantId, offerId, offerFingerprint, targetProfileId, entitlementRevision, creationStatus, null);
        }
    }
}

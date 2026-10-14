package com.ai.fabric.platform.backend.customerselfservice.model;

import com.ai.fabric.platform.backend.deployment.model.DocumentStorageBindingSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Customer-safe commands for an offer-reviewed, target-scoped S3 binding. */
public final class CustomerDocumentStorageBindingContracts {
    private CustomerDocumentStorageBindingContracts() { }

    public record BindRequest(
        @PositiveOrZero long expectedEntitlementRevision,
        @NotBlank @Size(max = 128) String operationKey,
        @NotBlank @Size(max = 64) String targetProfileId,
        @NotBlank @Size(max = 64) String bindingSlotId,
        @NotBlank @Size(max = 64) String selectedProfileId,
        @NotBlank @Size(max = 512) String endpoint,
        @NotBlank @Size(max = 128) String region,
        @NotBlank @Size(max = 255) String bucket,
        @Size(max = 1024) String prefix,
        @NotBlank @Size(max = 1024) String accessKey,
        @NotBlank @Size(max = 4096) String secretKey,
        @Size(max = 4096) String sessionToken,
        boolean pathStyleAccess,
        boolean objectVersioningAvailable
    ) { }

    public record RevokeRequest(
        @PositiveOrZero long expectedEntitlementRevision,
        @NotBlank @Size(max = 128) String operationKey,
        @NotBlank @Size(max = 64) String targetProfileId,
        @NotBlank @Size(max = 64) String bindingSlotId
    ) { }

    public record Result(String offerId, String deploymentId, String targetProfileId,
                         String bindingSlotId, String selectedProfileId, String status,
                         DocumentStorageBindingSummary binding) { }
}

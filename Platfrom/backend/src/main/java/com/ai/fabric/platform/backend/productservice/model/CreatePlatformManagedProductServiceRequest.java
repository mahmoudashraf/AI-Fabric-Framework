package com.ai.fabric.platform.backend.productservice.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformManagedProductServiceRequest(
    @NotBlank @Size(min = 3, max = 128) String serviceRef,
    @NotBlank @Size(min = 3, max = 255) String displayName,
    @NotBlank @Size(min = 3, max = 64) String productFamily,
    @NotBlank @Size(min = 3, max = 128) String serviceKind,
    @NotBlank @Size(min = 3, max = 64) String deploymentMode,
    @NotBlank @Size(min = 3, max = 64) String tenantMode,
    @Size(max = 64) String environmentScope,
    @Size(max = 64) String deploymentId,
    @Min(1) @Max(20) Integer desiredReplicas,
    @Min(1) @Max(20) Integer minReplicas,
    @Min(1) @Max(20) Integer maxReplicas,
    @Size(max = 2000) String baseUrl,
    @Size(max = 255) String healthPath,
    @Size(max = 2000) String serviceRoot,
    @Size(max = 2000) String dockerfilePath,
    @Size(max = 255) String secretName,
    UpdatePlatformManagedProductServiceShopifyBillingConfigRequest shopifyBillingConfig
) {
}

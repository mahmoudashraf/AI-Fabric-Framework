package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertDocumentStorageBindingRequest(
    @NotBlank @Size(max = 64) String targetProfileId,
    @NotBlank @Size(max = 64) String connectorType,
    @Size(max = 512) String endpoint,
    @Size(max = 128) String region,
    @Size(max = 255) String bucket,
    @Size(max = 1024) String prefix,
    @Size(max = 1024) String accessKey,
    @Size(max = 4096) String secretKey,
    @Size(max = 4096) String sessionToken,
    Boolean pathStyleAccess,
    Boolean objectVersioningAvailable,
    Boolean allowInsecureEndpoint,
    @Size(max = 512) String mountedRoot
) {
}

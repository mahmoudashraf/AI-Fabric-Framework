package com.ai.fabric.platform.backend.tenant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePlatformTenantRequest(
    @NotBlank @Size(min = 2, max = 255) String name,
    @Size(max = 1000) String description
) {
}

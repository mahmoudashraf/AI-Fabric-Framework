package com.ai.fabric.platform.backend.security.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePlatformUserRequest(
    @NotBlank @Size(min = 2, max = 255) String displayName,
    @NotBlank String role,
    @NotBlank String status,
    String customerId
) {
}

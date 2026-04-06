package com.ai.fabric.platform.backend.security.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformUserRequest(
    @Email @NotBlank String email,
    @NotBlank @Size(min = 2, max = 255) String displayName,
    @NotBlank @Size(min = 10, max = 255) String password,
    @NotBlank String role,
    String customerId
) {
}

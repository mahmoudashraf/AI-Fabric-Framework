package com.ai.fabric.platform.backend.security.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPlatformUserPasswordRequest(
    @NotBlank @Size(min = 10, max = 255) String password
) {
}

package com.ai.fabric.platform.backend.tenant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePlatformConsumerRequest(
    @NotBlank @Size(min = 2, max = 255) String displayName,
    @Size(max = 1000) String description,
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED", flags = Pattern.Flag.CASE_INSENSITIVE) String status
) {
}

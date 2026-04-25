package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerSignupCompleteRequest(
    @NotBlank @Size(max = 255) String workspaceName
) {
}

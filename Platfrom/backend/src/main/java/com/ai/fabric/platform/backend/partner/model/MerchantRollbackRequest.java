package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record MerchantRollbackRequest(
    @NotBlank @Size(max = 255) String requesterName,
    @Email @Size(max = 255) String requesterEmail,
    @NotBlank @Size(max = 2000) String reason
) {
}

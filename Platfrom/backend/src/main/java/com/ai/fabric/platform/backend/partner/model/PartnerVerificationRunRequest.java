package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.NotBlank;

public record PartnerVerificationRunRequest(
    @NotBlank String packId
) {
}

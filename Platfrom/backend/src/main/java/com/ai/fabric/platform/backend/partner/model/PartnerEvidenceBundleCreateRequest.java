package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.NotBlank;

public record PartnerEvidenceBundleCreateRequest(
    @NotBlank String bundleKind,
    String verificationRunId
) {
}

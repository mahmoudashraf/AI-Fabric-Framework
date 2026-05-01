package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Size;

public record PartnerPackageTrialDeactivationRequest(
    @Size(max = 500) String reason
) {
}

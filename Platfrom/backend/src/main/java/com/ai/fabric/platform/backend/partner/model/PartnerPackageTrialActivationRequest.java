package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PartnerPackageTrialActivationRequest(
    @Size(max = 64) String tierKey,
    @Min(1) @Max(30) Integer trialDays,
    @Size(max = 500) String reason
) {
}

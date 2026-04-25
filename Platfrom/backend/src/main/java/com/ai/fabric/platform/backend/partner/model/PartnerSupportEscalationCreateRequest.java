package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record PartnerSupportEscalationCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 32) String severity,
    @NotBlank @Size(max = 8000) String description,
    @Size(max = 8000) String reproductionSteps,
    @Size(max = 4000) String expectedBehavior,
    @Size(max = 4000) String actualBehavior,
    @Size(max = 4000) String impact,
    @Size(max = 4000) String nextAction,
    Instant dueAt,
    List<String> evidenceBundleIds
) {
}

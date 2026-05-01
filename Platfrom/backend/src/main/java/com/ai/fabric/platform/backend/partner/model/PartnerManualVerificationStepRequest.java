package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerManualVerificationStepRequest(
    String runId,
    @NotBlank @Size(max = 64) String status,
    @Size(max = 4000) String evidenceNote,
    @Size(max = 4000) String partnerSafeMessage
) {
}

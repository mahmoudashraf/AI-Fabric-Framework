package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerVerificationStepSummary(
    String stepId,
    String label,
    String status,
    String partnerSafeMessage,
    String suggestedFix,
    List<String> evidence,
    Instant checkedAt,
    int sortOrder
) {
}

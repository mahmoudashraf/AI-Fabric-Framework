package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerVerificationPackSummary(
    String id,
    String name,
    String description,
    List<PartnerVerificationStepSummary> steps
) {
}

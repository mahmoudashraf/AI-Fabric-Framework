package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerProductPackageSummary(
    String profileKey,
    String packageKey,
    String tierKey,
    String displayName,
    String costPosture,
    String verificationPackId,
    Instant lastConfiguredAt
) {
}

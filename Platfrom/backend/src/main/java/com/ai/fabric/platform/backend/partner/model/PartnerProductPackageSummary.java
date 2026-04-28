package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerProductPackageSummary(
    String profileKey,
    String packageKey,
    String tierKey,
    String runtimeProfileKey,
    String vectorProfileKey,
    String displayName,
    String costPosture,
    String vectorStrategy,
    String vectorProvisioningMode,
    String vectorStoragePosture,
    String verificationPackId,
    String lastProvisioningJobId,
    boolean vectorReindexRequired,
    Instant lastReconciledAt
) {
}

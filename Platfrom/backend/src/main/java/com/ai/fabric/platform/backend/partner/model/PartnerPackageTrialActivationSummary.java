package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerPackageTrialActivationSummary(
    String id,
    String shopDomain,
    String packageKey,
    String tierKey,
    String status,
    Integer trialDays,
    Instant trialStartsAt,
    Instant trialEndsAt,
    Instant activatedAt,
    Instant deactivatedAt,
    String activatedByMemberId,
    String deactivatedByMemberId,
    String activationReason,
    String deactivationReason,
    String previousTierKey,
    String previousBillingStatus,
    String activationProvisioningJobId,
    String deactivationProvisioningJobId,
    boolean pastDue,
    boolean deactivationEligible,
    Instant updatedAt
) {
}

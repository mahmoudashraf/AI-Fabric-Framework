package com.ai.fabric.platform.backend.productservice.model;

import java.util.List;

public record PlatformManagedProductServiceBillingSummary(
    String mode,
    String tierKey,
    String planName,
    String status,
    boolean merchantApprovalRequired,
    boolean launchBlocked,
    boolean paidTier,
    boolean actionCapable,
    Integer catalogProductCap,
    String syncCadence,
    boolean poweredByBadgeRequired,
    boolean chatFallbackEnabled,
    boolean requiresExplicitConfirmation,
    boolean auditTrailAvailable,
    List<String> actionPackages,
    List<String> allowedSurfaces,
    List<PlatformManagedProductServiceBillingPlanSummary> availablePlans,
    String message
) {
}

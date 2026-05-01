package com.ai.fabric.platform.backend.productservice.model;

import java.util.List;

public record PlatformManagedProductServiceBillingPlanSummary(
    String tierKey,
    String planName,
    String amount,
    String currencyCode,
    String interval,
    boolean active,
    boolean commerciallyAvailable,
    boolean merchantApprovalSupported,
    boolean actionCapable,
    Integer catalogProductCap,
    String syncCadence,
    boolean poweredByBadgeRequired,
    boolean chatFallbackEnabled,
    boolean requiresExplicitConfirmation,
    boolean auditTrailAvailable,
    List<String> actionPackages,
    List<String> allowedSurfaces,
    String message
) {
}

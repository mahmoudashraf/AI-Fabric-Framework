package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceStoreBillingSummary(
    String shopDomain,
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
    java.util.List<String> allowedSurfaces,
    String message
) {
}

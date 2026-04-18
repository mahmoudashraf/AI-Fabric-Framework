package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceStoreBillingSummary(
    String shopDomain,
    String mode,
    String planName,
    String status,
    boolean merchantApprovalRequired,
    boolean launchBlocked,
    String message
) {
}

package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceBillingSummary(
    String mode,
    String planName,
    String status,
    boolean merchantApprovalRequired,
    boolean launchBlocked,
    String message
) {
}

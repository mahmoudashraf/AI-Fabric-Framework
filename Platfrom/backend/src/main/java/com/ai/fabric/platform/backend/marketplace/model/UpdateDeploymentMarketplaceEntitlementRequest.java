package com.ai.fabric.platform.backend.marketplace.model;

import java.time.Instant;

public record UpdateDeploymentMarketplaceEntitlementRequest(
    String status,
    Instant graceEndsAt,
    Instant accessEndsAt,
    String note
) {
}

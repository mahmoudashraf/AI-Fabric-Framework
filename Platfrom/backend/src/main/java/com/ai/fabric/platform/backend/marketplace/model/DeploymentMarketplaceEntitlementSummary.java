package com.ai.fabric.platform.backend.marketplace.model;

import java.math.BigDecimal;
import java.time.Instant;

public record DeploymentMarketplaceEntitlementSummary(
    String pricingModel,
    BigDecimal amount,
    String currency,
    String billingInterval,
    String status,
    boolean requiresEntitlement,
    boolean entitledForCompilation,
    Instant graceEndsAt,
    Instant accessEndsAt,
    String note,
    Instant updatedAt
) {
}

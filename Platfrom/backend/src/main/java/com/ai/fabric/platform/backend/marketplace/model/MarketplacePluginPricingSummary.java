package com.ai.fabric.platform.backend.marketplace.model;

import java.math.BigDecimal;

public record MarketplacePluginPricingSummary(
    String pricingModel,
    BigDecimal amount,
    String currency,
    String billingInterval,
    Integer trialDays,
    boolean requiresEntitlement
) {
}

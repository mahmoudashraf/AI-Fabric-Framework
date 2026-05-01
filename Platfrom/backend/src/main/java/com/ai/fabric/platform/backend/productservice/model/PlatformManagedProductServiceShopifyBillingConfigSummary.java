package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceShopifyBillingConfigSummary(
    String mode,
    boolean starterEnabled,
    String starterPlanName,
    String starterPlanHandle,
    String starterAmount,
    String starterCurrencyCode,
    String starterInterval,
    Integer starterTrialDays,
    boolean starterTest,
    boolean eliteEnabled,
    String elitePlanName,
    String elitePlanHandle,
    String eliteAmount,
    String eliteCurrencyCode,
    String eliteInterval,
    Integer eliteTrialDays,
    boolean eliteTest
) {
}

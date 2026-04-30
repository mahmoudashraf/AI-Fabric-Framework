package com.ai.fabric.platform.backend.productservice.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdatePlatformManagedProductServiceShopifyBillingConfigRequest(
    @Size(max = 64) String mode,
    Boolean starterEnabled,
    @Size(max = 255) String starterPlanName,
    @Size(max = 128) String starterPlanHandle,
    @Size(max = 32) String starterAmount,
    @Size(max = 8) String starterCurrencyCode,
    @Size(max = 64) String starterInterval,
    @Min(0) @Max(365) Integer starterTrialDays,
    Boolean starterTest,
    Boolean eliteEnabled,
    @Size(max = 255) String elitePlanName,
    @Size(max = 128) String elitePlanHandle,
    @Size(max = 32) String eliteAmount,
    @Size(max = 8) String eliteCurrencyCode,
    @Size(max = 64) String eliteInterval,
    @Min(0) @Max(365) Integer eliteTrialDays,
    Boolean eliteTest
) {
}

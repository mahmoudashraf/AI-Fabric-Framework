package com.ai.fabric.platform.backend.deployment.budget;

import java.util.Set;

/** Exact transport capabilities reviewed for automatic customer-runtime activation. */
final class ProviderUsageRuntimeProfile {
    private static final String RATE_CARD = "openai-gpt4omini-embedding3small-20261018-v1";
    private static final Set<String> MODELS = Set.of("gpt-4o-mini", "text-embedding-3-small");

    private ProviderUsageRuntimeProfile() { }

    static void require(ProviderUsageAllowanceContracts.ProviderBinding binding, String rateCard) {
        if (binding == null || !"openai".equals(binding.provider())
            || !MODELS.equals(Set.copyOf(binding.modelIds())) || !RATE_CARD.equals(rateCard)) {
            throw new IllegalStateException("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        }
    }
}

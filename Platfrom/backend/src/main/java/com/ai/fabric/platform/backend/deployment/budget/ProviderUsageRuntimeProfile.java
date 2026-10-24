package com.ai.fabric.platform.backend.deployment.budget;

import java.util.Set;

/** Exact transport capabilities reviewed for automatic customer-runtime activation. */
final class ProviderUsageRuntimeProfile {
    static final String RATE_CARD = "openai-gpt4omini-embedding3small-20261018-v1";
    private static final Set<String> MODELS = Set.of("gpt-4o-mini", "text-embedding-3-small");
    static final String MINI_RATE_CARD = "openai-gpt4omini-gpt54mini-embedding3small-20261024-v2";
    private static final Set<String> MINI_CHAT_MODELS = Set.of("gpt-4o-mini", "gpt-5.4-mini", "gpt-5.4-mini-2026-03-17");
    private static final Set<String> MINI_MODELS = Set.of("gpt-4o-mini", "gpt-5.4-mini",
        "gpt-5.4-mini-2026-03-17", "text-embedding-3-small");

    private ProviderUsageRuntimeProfile() { }

    static void require(ProviderUsageAllowanceContracts.ProviderBinding binding, String rateCard) {
        if (binding == null || !"openai".equals(binding.provider())
            || !reviewedModels(Set.copyOf(binding.modelIds()), rateCard)) {
            throw new IllegalStateException("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        }
    }

    private static boolean reviewedModels(Set<String> models, String rateCard) {
        if (RATE_CARD.equals(rateCard)) return MODELS.equals(models);
        return MINI_RATE_CARD.equals(rateCard) && MINI_MODELS.equals(models);
    }

    static boolean supportsChatModel(String model, String rateCard) {
        return RATE_CARD.equals(rateCard) ? "gpt-4o-mini".equals(model)
            : MINI_RATE_CARD.equals(rateCard) && MINI_CHAT_MODELS.contains(model);
    }
}

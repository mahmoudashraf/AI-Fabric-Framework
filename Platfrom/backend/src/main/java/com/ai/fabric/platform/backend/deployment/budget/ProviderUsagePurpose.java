package com.ai.fabric.platform.backend.deployment.budget;

/** Trusted worker selection, never a caller-supplied account/credit selector. */
enum ProviderUsagePurpose {
    GENERATION, RERANKING;

    boolean matches(ProviderUsageAllowanceContracts.ProviderBinding binding) {
        boolean reranking = "RERANKING_API_KEY".equals(binding.secretPurpose());
        return this == RERANKING ? reranking : !reranking;
    }
}

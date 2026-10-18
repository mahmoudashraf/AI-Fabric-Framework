package com.ai.fabric.runtime.budget;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.embedding.EmbeddingProvider;
import ai.fabric.provider.onnx.ONNXEmbeddingProvider;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver.OutputCapMode;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Objects;

/** Offline wiring validation only. No approval issuance, model probes or global auto-configuration. */
public final class RuntimeBudgetResolverFactory {
    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1";
    private RuntimeBudgetResolverFactory() { }

    public static BudgetGuardedSpringAiModelResolver create(AIProviderConfig providers, ObservationRegistry observations,
        ProviderBudgetLedger ledger, RuntimeBudgetBindingService budgets, RuntimeBudgetTurnScopes turns,
        EmbeddingProvider localOnnx, OutputCapMode outputCapMode) {
        Objects.requireNonNull(providers); Objects.requireNonNull(ledger); Objects.requireNonNull(turns);
        if (outputCapMode != OutputCapMode.MAX_COMPLETION_TOKENS) throw unsupported();
        if (Boolean.TRUE.equals(providers.getEnableFallback()) || configured(providers.getGenerationFallbackProviders())
            || configured(providers.getEmbeddingFallbackProviders())) throw unsupported();
        String expectedCredential = budgets.scope().credentialIdentity();
        if (!ReviewedOpenAiRateCard.REFERENCE.equals(budgets.scope().rateCardReference())) throw unsupported();
        chat(providers.resolveLlmDefaults());
        chat(providers.resolveOrchestrationLlmDefaults());
        chat(providers.resolveGenerationLlmDefaults());
        credential(expectedCredential, providers.getOpenai().getApiKey());
        endpoint(providers.getOpenai().getBaseUrl());
        if (providers.getOrchestration() != null) {
            credential(expectedCredential, first(providers.getOrchestration().getApiKey(), providers.getOpenai().getApiKey()));
            endpoint(providers.getOrchestration().getBaseUrl());
        }
        if (providers.getGeneration() != null) {
            credential(expectedCredential, first(providers.getGeneration().getApiKey(), providers.getOpenai().getApiKey()));
            endpoint(providers.getGeneration().getBaseUrl());
        }
        String embedding = providers.getEmbeddingProvider();
        if ("openai".equals(embedding)) {
            if (!ReviewedOpenAiRateCard.EMBEDDING_MODEL.equals(providers.resolveEmbeddingDefaults().model())) throw unsupported();
            credential(expectedCredential, first(providers.getEmbeddingApiKey(), providers.getOpenai().getEmbeddingApiKey(),
                providers.getOpenai().getApiKey()));
            endpoint(providers.getEmbeddingBaseUrl());
            endpoint(providers.getOpenai().getEmbeddingBaseUrl());
        } else if (!"onnx".equals(embedding) || localOnnx == null || localOnnx.getClass() != ONNXEmbeddingProvider.class
            || !localOnnx.isAvailable()) {
            throw unsupported();
        }
        return new BudgetGuardedSpringAiModelResolver(providers, observations, ledger,
            new RuntimeOpenAiBudgetScopeResolver(budgets), turns, outputCapMode);
    }

    private static boolean configured(List<String> values) { return values != null && !values.isEmpty(); }
    private static void chat(AIProviderConfig.GenerationDefaults defaults) {
        if (!"openai".equals(defaults.providerName()) || !ReviewedOpenAiRateCard.CHAT_MODEL.equals(defaults.model())
            || defaults.maxTokens() == null || defaults.maxTokens() < 1
            || defaults.maxTokens() > ReviewedOpenAiRateCard.CHAT_MAX_OUTPUT_TOKENS) throw unsupported();
    }
    private static void credential(String expected, String supplied) {
        String effective = first(supplied);
        if (effective == null || !expected.equals(ReviewedOpenAiRateCard.credentialIdentity(effective))) {
            throw new OpenAiBudgetException(OpenAiBudgetException.Code.CREDENTIAL_SCOPE_MISMATCH);
        }
    }
    private static void endpoint(String configured) {
        if (configured != null && !configured.isBlank() && !OPENAI_ENDPOINT.equals(configured.trim())
            && !(OPENAI_ENDPOINT + "/").equals(configured.trim())) throw unsupported();
    }
    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
    private static OpenAiBudgetException unsupported() {
        return new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_CONFIGURATION);
    }
}

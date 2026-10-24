package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.conflict;

/** Pre-debit check of published provider configuration; never supplies or writes provider defaults. */
final class ProviderUsageRuntimeConfiguration {
    private static final int MAX_OUTPUT_TOKENS = 16_384;
    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1";

    private ProviderUsageRuntimeConfiguration() { }

    record OutputCaps(int primary, int orchestration, int generation) { }

    static OutputCaps require(String value, ProviderUsageAllowanceCodec codec) {
        return require(value, codec, ProviderUsageRuntimeProfile.RATE_CARD);
    }

    static OutputCaps require(String value, ProviderUsageAllowanceCodec codec, String rateCard) {
        JsonNode config;
        try { config = codec.read(value, JsonNode.class); }
        catch (ProviderUsageAllowanceFailure invalid) { throw conflict("PROVIDER_USAGE_RUNTIME_CONFIG_INVALID"); }
        if (config == null || !config.isObject()) throw conflict("PROVIDER_USAGE_RUNTIME_CONFIG_INVALID");

        requireProvider(config, "llmProvider", Set.of("openai"));
        requireProvider(config, "orchestrationLlmProvider", Set.of("openai"));
        requireProvider(config, "generationLlmProvider", Set.of("openai"));
        requireProvider(config, "embeddingProvider", Set.of("openai", "onnx"));
        String provider = ManagedDeploymentProfileCatalog.resolveLlmProvider(config);
        if (!"openai".equals(provider)) throw conflict("PROVIDER_USAGE_RUNTIME_PROVIDER_NOT_REVIEWED");
        String model = ManagedDeploymentProfileCatalog.openAiModel(config);
        requireChatModel(model, rateCard);
        requireChatModel(orInherit(ManagedDeploymentProfileCatalog.orchestrationModel(config), model), rateCard);
        requireChatModel(orInherit(ManagedDeploymentProfileCatalog.generationModel(config), model), rateCard);
        if ("openai".equals(ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(config))) {
            requireModel(ManagedDeploymentProfileCatalog.openAiEmbeddingModel(config), "text-embedding-3-small");
        }
        if (ManagedDeploymentProfileCatalog.providerEnableFallback(config)
            || configured(config.get("generationFallbackProviders")) || configured(config.get("embeddingFallbackProviders"))) {
            throw conflict("PROVIDER_USAGE_RUNTIME_RECOVERY_NOT_REVIEWED");
        }
        for (String field : Set.of("openaiBaseUrl", "openaiEmbeddingBaseUrl", "embeddingBaseUrl",
            "orchestrationBaseUrl", "generationBaseUrl")) {
            String endpoint = text(config, field);
            if (!endpoint.isBlank() && !OPENAI_ENDPOINT.equals(endpoint) && !(OPENAI_ENDPOINT + "/").equals(endpoint)) {
                throw conflict("PROVIDER_USAGE_RUNTIME_ENDPOINT_NOT_REVIEWED");
            }
        }

        int primary = cap(config, "openaiMaxTokens", ManagedDeploymentProfileCatalog.openAiMaxTokens(config), null);
        int orchestration = cap(config, "orchestrationMaxTokens", ManagedDeploymentProfileCatalog.orchestrationMaxTokens(config), primary);
        int generation = cap(config, "generationMaxTokens", ManagedDeploymentProfileCatalog.generationMaxTokens(config), primary);
        return new OutputCaps(primary, orchestration, generation);
    }

    private static int cap(JsonNode config, String field, int generated, Integer inherited) {
        JsonNode declared = config.get(field);
        if (declared == null || declared.isNull()) {
            if (inherited != null) return inherited;
            throw conflict("PROVIDER_USAGE_RUNTIME_OUTPUT_CAP_REQUIRED");
        }
        int parsed;
        try {
            if (declared.isIntegralNumber() && declared.canConvertToInt()) parsed = declared.intValue();
            else if (declared.isTextual()) parsed = Integer.parseInt(declared.textValue().trim());
            else throw new NumberFormatException();
        } catch (NumberFormatException invalid) { throw conflict("PROVIDER_USAGE_RUNTIME_OUTPUT_CAP_INVALID"); }
        if (parsed < 1 || parsed > MAX_OUTPUT_TOKENS || generated != parsed) {
            throw conflict("PROVIDER_USAGE_RUNTIME_OUTPUT_CAP_INVALID");
        }
        return parsed;
    }

    private static void requireProvider(JsonNode config, String field, Set<String> supported) {
        String declared = text(config, field);
        if (!declared.isBlank() && !supported.contains(declared.toLowerCase(Locale.ROOT))) {
            throw conflict("PROVIDER_USAGE_RUNTIME_PROVIDER_NOT_REVIEWED");
        }
    }

    private static void requireModel(String actual, String expected) {
        if (!expected.equals(actual)) throw conflict("PROVIDER_USAGE_RUNTIME_MODEL_NOT_REVIEWED");
    }

    private static void requireChatModel(String model, String rateCard) {
        if (!ProviderUsageRuntimeProfile.supportsChatModel(model, rateCard))
            throw conflict("PROVIDER_USAGE_RUNTIME_MODEL_NOT_REVIEWED");
    }

    private static String orInherit(String declared, String inherited) { return declared.isBlank() ? inherited : declared; }

    private static String text(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull()) return "";
        if (!value.isTextual()) throw conflict("PROVIDER_USAGE_RUNTIME_CONFIG_INVALID");
        return value.textValue().trim();
    }

    private static boolean configured(JsonNode value) {
        return value != null && !value.isNull() && !(value.isArray() && value.isEmpty());
    }
}

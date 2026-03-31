package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

public final class ManagedDeploymentProfileCatalog {

    public static final String LLM_PROVIDER_OPENAI = "openai";
    public static final String LLM_PROVIDER_ANTHROPIC = "anthropic";
    public static final String EMBEDDING_PROVIDER_OPENAI = "openai";
    public static final String EMBEDDING_PROVIDER_ONNX = "onnx";
    public static final String VECTOR_STRATEGY_LUCENE = "lucene";
    public static final String VECTOR_STRATEGY_QDRANT = "qdrant";
    public static final String RUNTIME_PROFILE_MANAGED = "runtime-managed";
    public static final String RUNTIME_PROFILE_DEV = "runtime-dev";
    public static final String CONNECTOR_PROFILE_HOSTED = "connector-hosted";
    public static final String CONNECTOR_PROFILE_PASSIVE = "connector-passive";
    public static final String AUTHZ_MODE_REMOTE_HTTP = "REMOTE_HTTP";
    public static final String AUTHZ_MODE_DENY_ALL = "DENY_ALL";
    public static final String CONNECTOR_API_KEY_HEADER = "X-AIFABRIC-API-KEY";
    public static final String ADMIN_API_KEY_HEADER = "X-ADMIN-API-KEY";
    public static final int DEFAULT_QDRANT_PORT = 6333;
    public static final int DEFAULT_QDRANT_GRPC_PORT = 6334;

    public static final Set<String> SUPPORTED_LLM_PROVIDERS = Set.of(
        LLM_PROVIDER_OPENAI,
        LLM_PROVIDER_ANTHROPIC
    );
    public static final Set<String> SUPPORTED_EMBEDDING_PROVIDERS = Set.of(
        EMBEDDING_PROVIDER_OPENAI,
        EMBEDDING_PROVIDER_ONNX
    );
    public static final Set<String> SUPPORTED_VECTOR_STRATEGIES = Set.of(
        VECTOR_STRATEGY_LUCENE,
        VECTOR_STRATEGY_QDRANT
    );
    public static final Set<String> SUPPORTED_RUNTIME_PROFILES = Set.of(
        RUNTIME_PROFILE_MANAGED,
        RUNTIME_PROFILE_DEV
    );
    public static final Set<String> SUPPORTED_CONNECTOR_PROFILES = Set.of(
        CONNECTOR_PROFILE_HOSTED,
        CONNECTOR_PROFILE_PASSIVE
    );
    public static final Set<String> SUPPORTED_AUTHZ_MODES = Set.of(
        AUTHZ_MODE_REMOTE_HTTP,
        AUTHZ_MODE_DENY_ALL
    );

    private ManagedDeploymentProfileCatalog() {
    }

    public static String resolveLlmProvider(JsonNode providerConfig) {
        return normalizeToSupported(
            providerConfig == null ? null : providerConfig.path("llmProvider").asText(null),
            SUPPORTED_LLM_PROVIDERS,
            LLM_PROVIDER_OPENAI
        );
    }

    public static String resolveEmbeddingProvider(JsonNode providerConfig) {
        String llmProvider = resolveLlmProvider(providerConfig);
        return normalizeToSupported(
            providerConfig == null ? null : providerConfig.path("embeddingProvider").asText(null),
            SUPPORTED_EMBEDDING_PROVIDERS,
            defaultEmbeddingProviderFor(llmProvider)
        );
    }

    public static String resolveVectorStrategy(JsonNode providerConfig) {
        return normalizeToSupported(
            providerConfig == null ? null : providerConfig.path("vectorStrategy").asText(null),
            SUPPORTED_VECTOR_STRATEGIES,
            VECTOR_STRATEGY_LUCENE
        );
    }

    public static String resolveRuntimeProfile(JsonNode providerConfig) {
        return normalizeToSupported(
            providerConfig == null ? null : providerConfig.path("runtimeProfile").asText(null),
            SUPPORTED_RUNTIME_PROFILES,
            RUNTIME_PROFILE_MANAGED
        );
    }

    public static String resolveConnectorProfile(JsonNode providerConfig) {
        return normalizeToSupported(
            providerConfig == null ? null : providerConfig.path("connectorProfile").asText(null),
            SUPPORTED_CONNECTOR_PROFILES,
            CONNECTOR_PROFILE_HOSTED
        );
    }

    public static String resolveAuthzMode(JsonNode securityConfig) {
        String raw = securityConfig == null ? null : securityConfig.path("authzMode").asText(null);
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_AUTHZ_MODES.contains(normalized) ? normalized : AUTHZ_MODE_REMOTE_HTTP;
    }

    public static String defaultEmbeddingProviderFor(String llmProvider) {
        return LLM_PROVIDER_ANTHROPIC.equals(normalize(llmProvider))
            ? EMBEDDING_PROVIDER_ONNX
            : EMBEDDING_PROVIDER_OPENAI;
    }

    public static String defaultLlmModel(String llmProvider) {
        return switch (normalize(llmProvider)) {
            case LLM_PROVIDER_ANTHROPIC -> "claude-3-haiku-20240307";
            case LLM_PROVIDER_OPENAI -> "gpt-4o-mini";
            default -> "gpt-4o-mini";
        };
    }

    public static String defaultEmbeddingModel(String embeddingProvider) {
        return switch (normalize(embeddingProvider)) {
            case EMBEDDING_PROVIDER_ONNX -> "all-MiniLM-L6-v2";
            case EMBEDDING_PROVIDER_OPENAI -> "text-embedding-3-small";
            default -> "text-embedding-3-small";
        };
    }

    public static int defaultEmbeddingDimensions(String embeddingProvider) {
        return EMBEDDING_PROVIDER_ONNX.equals(normalize(embeddingProvider)) ? 384 : 512;
    }

    public static boolean usesOpenAi(JsonNode providerConfig) {
        return LLM_PROVIDER_OPENAI.equals(resolveLlmProvider(providerConfig))
            || EMBEDDING_PROVIDER_OPENAI.equals(resolveEmbeddingProvider(providerConfig));
    }

    public static boolean usesAnthropic(JsonNode providerConfig) {
        return LLM_PROVIDER_ANTHROPIC.equals(resolveLlmProvider(providerConfig));
    }

    public static boolean usesQdrant(JsonNode providerConfig) {
        return VECTOR_STRATEGY_QDRANT.equals(resolveVectorStrategy(providerConfig));
    }

    public static boolean runtimeDevDefaultsEnabled(JsonNode providerConfig) {
        return RUNTIME_PROFILE_DEV.equals(resolveRuntimeProfile(providerConfig));
    }

    public static boolean connectorRuntimeProxyEnabled(JsonNode providerConfig) {
        return CONNECTOR_PROFILE_HOSTED.equals(resolveConnectorProfile(providerConfig));
    }

    public static boolean connectorApiKeyEnabled(JsonNode securityConfig) {
        return securityConfig == null || !securityConfig.path("connectorApiKeyEnabled").isBoolean()
            ? true
            : securityConfig.path("connectorApiKeyEnabled").asBoolean();
    }

    public static boolean adminApiKeyEnabled(JsonNode securityConfig) {
        return securityConfig != null && securityConfig.path("adminApiKeyEnabled").asBoolean(false);
    }

    public static String qdrantHost(JsonNode providerConfig) {
        return providerConfig == null ? "" : providerConfig.path("qdrantHost").asText("").trim();
    }

    public static int qdrantPort(JsonNode providerConfig) {
        return positiveOrDefault(providerConfig == null ? 0 : providerConfig.path("qdrantPort").asInt(0), DEFAULT_QDRANT_PORT);
    }

    public static int qdrantGrpcPort(JsonNode providerConfig) {
        return positiveOrDefault(
            providerConfig == null ? 0 : providerConfig.path("qdrantGrpcPort").asInt(0),
            DEFAULT_QDRANT_GRPC_PORT
        );
    }

    public static boolean qdrantPreferGrpc(JsonNode providerConfig) {
        return providerConfig != null && providerConfig.path("qdrantPreferGrpc").asBoolean(false);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static String normalizeToSupported(String raw, Set<String> supported, String fallback) {
        String normalized = normalize(raw);
        return supported.contains(normalized) ? normalized : fallback;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

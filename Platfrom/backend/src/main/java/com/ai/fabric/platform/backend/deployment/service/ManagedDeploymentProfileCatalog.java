package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ManagedDeploymentProfileCatalog {

    public static final String LLM_PROVIDER_OPENAI = "openai";
    public static final String LLM_PROVIDER_AZURE = "azure";
    public static final String LLM_PROVIDER_ANTHROPIC = "anthropic";
    public static final String LLM_PROVIDER_COHERE = "cohere";
    public static final String LLM_PROVIDER_GEMINI = "gemini";
    public static final String EMBEDDING_PROVIDER_OPENAI = "openai";
    public static final String EMBEDDING_PROVIDER_AZURE = "azure";
    public static final String EMBEDDING_PROVIDER_COHERE = "cohere";
    public static final String EMBEDDING_PROVIDER_GEMINI = "gemini";
    public static final String EMBEDDING_PROVIDER_ONNX = "onnx";
    public static final String EMBEDDING_PROVIDER_REST = "rest";
    public static final String VECTOR_STRATEGY_LUCENE = "lucene";
    public static final String VECTOR_STRATEGY_MEMORY = "memory";
    public static final String VECTOR_STRATEGY_PINECONE = "pinecone";
    public static final String VECTOR_STRATEGY_WEAVIATE = "weaviate";
    public static final String VECTOR_STRATEGY_QDRANT = "qdrant";
    public static final String VECTOR_STRATEGY_MILVUS = "milvus";
    public static final String VECTOR_PROVISIONING_MODE_LOCAL_MANAGED = "LOCAL_MANAGED";
    public static final String VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING = "EXTERNAL_EXISTING";
    public static final String VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED = "PLATFORM_MANAGED";
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
    public static final int DEFAULT_WEAVIATE_PORT = 443;
    public static final int DEFAULT_MILVUS_PORT = 19530;
    public static final Set<String> SUPPORTED_PINECONE_METRICS = Set.of(
        "cosine",
        "euclidean",
        "dotproduct"
    );
    public static final Set<String> SUPPORTED_PINECONE_CLOUDS = Set.of(
        "aws",
        "gcp",
        "azure"
    );
    public static final Set<String> SUPPORTED_ZILLIZ_CLOUD_PLANS = Set.of(
        "Free",
        "Serverless",
        "Standard",
        "Enterprise"
    );
    public static final Set<String> SUPPORTED_ZILLIZ_CLOUD_CU_TYPES = Set.of(
        "Performance-optimized",
        "Capacity-optimized"
    );
    public static final Set<String> SUPPORTED_QDRANT_CLOUD_PROVIDERS = Set.of(
        "aws",
        "gcp",
        "azure"
    );

    public static final Set<String> SUPPORTED_LLM_PROVIDERS = Set.of(
        LLM_PROVIDER_OPENAI,
        LLM_PROVIDER_AZURE,
        LLM_PROVIDER_ANTHROPIC,
        LLM_PROVIDER_COHERE,
        LLM_PROVIDER_GEMINI
    );
    public static final Set<String> SUPPORTED_EMBEDDING_PROVIDERS = Set.of(
        EMBEDDING_PROVIDER_OPENAI,
        EMBEDDING_PROVIDER_AZURE,
        EMBEDDING_PROVIDER_COHERE,
        EMBEDDING_PROVIDER_GEMINI,
        EMBEDDING_PROVIDER_ONNX,
        EMBEDDING_PROVIDER_REST
    );
    public static final Set<String> SUPPORTED_VECTOR_STRATEGIES = Set.of(
        VECTOR_STRATEGY_LUCENE,
        VECTOR_STRATEGY_MEMORY,
        VECTOR_STRATEGY_PINECONE,
        VECTOR_STRATEGY_WEAVIATE,
        VECTOR_STRATEGY_QDRANT,
        VECTOR_STRATEGY_MILVUS
    );
    public static final Set<String> SUPPORTED_VECTOR_PROVISIONING_MODES = Set.of(
        VECTOR_PROVISIONING_MODE_LOCAL_MANAGED,
        VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING,
        VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED
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

    public static String resolveVectorProvisioningMode(JsonNode providerConfig) {
        String vectorStrategy = resolveVectorStrategy(providerConfig);
        String fallback = defaultVectorProvisioningMode(
            vectorStrategy,
            pineconeManagedIndexEnabled(providerConfig)
        );
        String raw = providerConfig == null ? null : providerConfig.path("vectorProvisioningMode").asText(null);
        return normalizeVectorProvisioningMode(raw, fallback);
    }

    public static String normalizeVectorProvisioningMode(String raw, String fallback) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_VECTOR_PROVISIONING_MODES.contains(normalized) ? normalized : fallback;
    }

    public static String defaultVectorProvisioningMode(String vectorStrategy, boolean pineconeManagedIndexEnabled) {
        return switch (normalize(vectorStrategy)) {
            case VECTOR_STRATEGY_LUCENE, VECTOR_STRATEGY_MEMORY -> VECTOR_PROVISIONING_MODE_LOCAL_MANAGED;
            case VECTOR_STRATEGY_PINECONE -> pineconeManagedIndexEnabled
                ? VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED
                : VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING;
            case VECTOR_STRATEGY_QDRANT, VECTOR_STRATEGY_WEAVIATE, VECTOR_STRATEGY_MILVUS ->
                VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING;
            default -> VECTOR_PROVISIONING_MODE_LOCAL_MANAGED;
        };
    }

    public static boolean supportsPlatformManagedVector(String vectorStrategy) {
        return VECTOR_STRATEGY_PINECONE.equals(normalize(vectorStrategy))
            || VECTOR_STRATEGY_QDRANT.equals(normalize(vectorStrategy))
            || VECTOR_STRATEGY_MILVUS.equals(normalize(vectorStrategy));
    }

    public static boolean supportsExternalExistingVector(String vectorStrategy) {
        return switch (normalize(vectorStrategy)) {
            case VECTOR_STRATEGY_PINECONE, VECTOR_STRATEGY_QDRANT, VECTOR_STRATEGY_WEAVIATE, VECTOR_STRATEGY_MILVUS -> true;
            default -> false;
        };
    }

    public static boolean supportsLocalManagedVector(String vectorStrategy) {
        return VECTOR_STRATEGY_LUCENE.equals(normalize(vectorStrategy))
            || VECTOR_STRATEGY_MEMORY.equals(normalize(vectorStrategy));
    }

    public static boolean supportsVectorProvisioningMode(String vectorStrategy, String provisioningMode) {
        String normalizedMode = normalizeVectorProvisioningMode(provisioningMode, "");
        if (normalizedMode.isBlank()) {
            return false;
        }
        return switch (normalizedMode) {
            case VECTOR_PROVISIONING_MODE_LOCAL_MANAGED -> supportsLocalManagedVector(vectorStrategy);
            case VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING -> supportsExternalExistingVector(vectorStrategy);
            case VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED -> supportsPlatformManagedVector(vectorStrategy);
            default -> false;
        };
    }

    public static String vectorProvisioningModeGuidance(String vectorStrategy) {
        return switch (normalize(vectorStrategy)) {
            case VECTOR_STRATEGY_LUCENE, VECTOR_STRATEGY_MEMORY ->
                "This vector backend is local to the runtime, so only LOCAL_MANAGED is supported.";
            case VECTOR_STRATEGY_PINECONE ->
                "Pinecone supports EXTERNAL_EXISTING and PLATFORM_MANAGED. PLATFORM_MANAGED uses the formal Pinecone control plane to create or reconcile a serverless index and bind its resolved host back into the deployment.";
            case VECTOR_STRATEGY_QDRANT ->
                "Qdrant supports EXTERNAL_EXISTING and PLATFORM_MANAGED. PLATFORM_MANAGED uses the formal Qdrant Cloud control plane to create a managed cluster and deployment-scoped database key.";
            case VECTOR_STRATEGY_WEAVIATE ->
                "Weaviate supports EXTERNAL_EXISTING. Use Weaviate Cloud or another managed Weaviate service by supplying the existing endpoint and API key. The platform does not currently automate Weaviate Cloud cluster lifecycle because the vendor does not expose a public control-plane API comparable to Pinecone or Qdrant.";
            case VECTOR_STRATEGY_MILVUS ->
                "Milvus supports EXTERNAL_EXISTING and PLATFORM_MANAGED. PLATFORM_MANAGED uses the formal Zilliz Cloud control plane to create or reuse a managed cluster and bind deployment-scoped runtime credentials back into the deployment.";
            default ->
                "Choose a vector provisioning mode that matches the selected vector strategy.";
        };
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
        return switch (normalize(llmProvider)) {
            case LLM_PROVIDER_AZURE -> EMBEDDING_PROVIDER_AZURE;
            case LLM_PROVIDER_ANTHROPIC -> EMBEDDING_PROVIDER_ONNX;
            case LLM_PROVIDER_COHERE -> EMBEDDING_PROVIDER_COHERE;
            case LLM_PROVIDER_GEMINI -> EMBEDDING_PROVIDER_GEMINI;
            case LLM_PROVIDER_OPENAI -> EMBEDDING_PROVIDER_OPENAI;
            default -> EMBEDDING_PROVIDER_OPENAI;
        };
    }

    public static String defaultLlmModel(String llmProvider) {
        return switch (normalize(llmProvider)) {
            case LLM_PROVIDER_AZURE -> "";
            case LLM_PROVIDER_ANTHROPIC -> "claude-3-haiku-20240307";
            case LLM_PROVIDER_COHERE -> "command-r7b-12-2024";
            case LLM_PROVIDER_GEMINI -> "gemini-1.5-flash";
            case LLM_PROVIDER_OPENAI -> "gpt-4o-mini";
            default -> "gpt-4o-mini";
        };
    }

    public static String defaultEmbeddingModel(String embeddingProvider) {
        return switch (normalize(embeddingProvider)) {
            case EMBEDDING_PROVIDER_AZURE -> "";
            case EMBEDDING_PROVIDER_COHERE -> "embed-english-v3.0";
            case EMBEDDING_PROVIDER_GEMINI -> "text-embedding-004";
            case EMBEDDING_PROVIDER_ONNX -> "all-MiniLM-L6-v2";
            case EMBEDDING_PROVIDER_OPENAI -> "text-embedding-3-small";
            case EMBEDDING_PROVIDER_REST -> "all-MiniLM-L6-v2";
            default -> "text-embedding-3-small";
        };
    }

    public static int defaultEmbeddingDimensions(String embeddingProvider) {
        return switch (normalize(embeddingProvider)) {
            case EMBEDDING_PROVIDER_ONNX, EMBEDDING_PROVIDER_REST -> 384;
            case EMBEDDING_PROVIDER_COHERE -> 1024;
            case EMBEDDING_PROVIDER_GEMINI -> 768;
            case EMBEDDING_PROVIDER_OPENAI -> 1536;
            default -> 512;
        };
    }

    public static boolean usesOpenAi(JsonNode providerConfig) {
        return usesLlmProvider(providerConfig, LLM_PROVIDER_OPENAI)
            || EMBEDDING_PROVIDER_OPENAI.equals(resolveEmbeddingProvider(providerConfig));
    }

    public static boolean usesAzure(JsonNode providerConfig) {
        return usesLlmProvider(providerConfig, LLM_PROVIDER_AZURE)
            || EMBEDDING_PROVIDER_AZURE.equals(resolveEmbeddingProvider(providerConfig));
    }

    public static boolean usesAnthropic(JsonNode providerConfig) {
        return usesLlmProvider(providerConfig, LLM_PROVIDER_ANTHROPIC);
    }

    public static boolean usesCohere(JsonNode providerConfig) {
        return usesLlmProvider(providerConfig, LLM_PROVIDER_COHERE)
            || EMBEDDING_PROVIDER_COHERE.equals(resolveEmbeddingProvider(providerConfig));
    }

    public static boolean usesGemini(JsonNode providerConfig) {
        return usesLlmProvider(providerConfig, LLM_PROVIDER_GEMINI)
            || EMBEDDING_PROVIDER_GEMINI.equals(resolveEmbeddingProvider(providerConfig));
    }

    public static boolean usesRestEmbeddings(JsonNode providerConfig) {
        return EMBEDDING_PROVIDER_REST.equals(resolveEmbeddingProvider(providerConfig));
    }

    public static boolean usesPinecone(JsonNode providerConfig) {
        return VECTOR_STRATEGY_PINECONE.equals(resolveVectorStrategy(providerConfig));
    }

    public static boolean pineconePlatformManaged(JsonNode providerConfig) {
        return usesPinecone(providerConfig)
            && VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(resolveVectorProvisioningMode(providerConfig));
    }

    public static boolean pineconeExternalExisting(JsonNode providerConfig) {
        return usesPinecone(providerConfig)
            && VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING.equals(resolveVectorProvisioningMode(providerConfig));
    }

    public static boolean usesWeaviate(JsonNode providerConfig) {
        return VECTOR_STRATEGY_WEAVIATE.equals(resolveVectorStrategy(providerConfig));
    }

    public static boolean usesQdrant(JsonNode providerConfig) {
        return VECTOR_STRATEGY_QDRANT.equals(resolveVectorStrategy(providerConfig));
    }

    public static boolean usesMilvus(JsonNode providerConfig) {
        return VECTOR_STRATEGY_MILVUS.equals(resolveVectorStrategy(providerConfig));
    }

    public static boolean milvusPlatformManaged(JsonNode providerConfig) {
        return usesMilvus(providerConfig)
            && VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(resolveVectorProvisioningMode(providerConfig));
    }

    public static boolean milvusExternalExisting(JsonNode providerConfig) {
        return usesMilvus(providerConfig)
            && VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING.equals(resolveVectorProvisioningMode(providerConfig));
    }

    public static boolean usesMemoryVector(JsonNode providerConfig) {
        return VECTOR_STRATEGY_MEMORY.equals(resolveVectorStrategy(providerConfig));
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

    public static String secretNameForLlmProvider(String llmProvider) {
        return switch (normalize(llmProvider)) {
            case LLM_PROVIDER_OPENAI -> "OPENAI_API_KEY";
            case LLM_PROVIDER_AZURE -> "AZURE_OPENAI_API_KEY";
            case LLM_PROVIDER_ANTHROPIC -> "ANTHROPIC_API_KEY";
            case LLM_PROVIDER_COHERE -> "COHERE_API_KEY";
            case LLM_PROVIDER_GEMINI -> "GEMINI_API_KEY";
            default -> "";
        };
    }

    public static Set<String> selectedLlmProviders(JsonNode providerConfig) {
        Set<String> providers = new LinkedHashSet<>();
        providers.add(resolveLlmProvider(providerConfig));
        addIfPresent(providers, orchestrationLlmProvider(providerConfig));
        addIfPresent(providers, generationLlmProvider(providerConfig));
        providers.removeIf(String::isBlank);
        return Set.copyOf(providers);
    }

    public static boolean usesLlmProvider(JsonNode providerConfig, String providerName) {
        return selectedLlmProviders(providerConfig).contains(normalize(providerName));
    }

    public static Set<String> requiredLlmSecretNames(JsonNode providerConfig) {
        Set<String> names = new LinkedHashSet<>();
        for (String provider : selectedLlmProviders(providerConfig)) {
            addIfPresent(names, secretNameForLlmProvider(provider));
        }
        return Set.copyOf(names);
    }

    public static Map<String, String> providerSecretNamesByLlmSelection(JsonNode providerConfig) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String provider : selectedLlmProviders(providerConfig)) {
            String secretName = secretNameForLlmProvider(provider);
            if (!secretName.isBlank()) {
                names.put(provider, secretName);
            }
        }
        return Map.copyOf(names);
    }

    public static String secretNameForEmbeddingProvider(String embeddingProvider) {
        return switch (normalize(embeddingProvider)) {
            case EMBEDDING_PROVIDER_OPENAI -> "OPENAI_API_KEY";
            case EMBEDDING_PROVIDER_AZURE -> "AZURE_OPENAI_API_KEY";
            case EMBEDDING_PROVIDER_COHERE -> "COHERE_API_KEY";
            case EMBEDDING_PROVIDER_GEMINI -> "GEMINI_API_KEY";
            case EMBEDDING_PROVIDER_ONNX, EMBEDDING_PROVIDER_REST -> "";
            default -> "";
        };
    }

    public static String requiredVectorSecretName(String vectorStrategy) {
        return switch (normalize(vectorStrategy)) {
            case VECTOR_STRATEGY_PINECONE -> "PINECONE_API_KEY";
            default -> "";
        };
    }

    public static String requiredVectorSecretName(JsonNode providerConfig) {
        String vectorStrategy = resolveVectorStrategy(providerConfig);
        if (VECTOR_STRATEGY_PINECONE.equals(vectorStrategy)) {
            return "PINECONE_API_KEY";
        }
        if (qdrantPlatformManaged(providerConfig)) {
            return "QDRANT_CLOUD_MANAGEMENT_API_KEY";
        }
        if (milvusPlatformManaged(providerConfig)) {
            return "ZILLIZ_CLOUD_API_KEY";
        }
        return "";
    }

    public static Set<String> optionalVectorSecretNames(String vectorStrategy) {
        String normalized = normalize(vectorStrategy);
        Set<String> names = new LinkedHashSet<>();
        if (VECTOR_STRATEGY_QDRANT.equals(normalized)) {
            names.add("QDRANT_API_KEY");
        } else if (VECTOR_STRATEGY_WEAVIATE.equals(normalized)) {
            names.add("WEAVIATE_API_KEY");
        } else if (VECTOR_STRATEGY_MILVUS.equals(normalized)) {
            names.add("MILVUS_USERNAME");
            names.add("MILVUS_PASSWORD");
        }
        return Set.copyOf(names);
    }

    public static Set<String> optionalVectorSecretNames(JsonNode providerConfig) {
        String normalized = resolveVectorStrategy(providerConfig);
        Set<String> names = new LinkedHashSet<>();
        if (VECTOR_STRATEGY_QDRANT.equals(normalized) && !qdrantPlatformManaged(providerConfig)) {
            names.add("QDRANT_API_KEY");
        } else if (VECTOR_STRATEGY_WEAVIATE.equals(normalized)) {
            names.add("WEAVIATE_API_KEY");
        } else if (VECTOR_STRATEGY_MILVUS.equals(normalized) && !milvusPlatformManaged(providerConfig)) {
            names.add("MILVUS_USERNAME");
            names.add("MILVUS_PASSWORD");
        }
        return Set.copyOf(names);
    }

    public static String qdrantHost(JsonNode providerConfig) {
        return providerConfig == null ? "" : providerConfig.path("qdrantHost").asText("").trim();
    }

    public static boolean providerEnableFallback(JsonNode providerConfig) {
        return readBoolean(providerConfig, "enableFallback", true);
    }

    public static String orchestrationLlmProvider(JsonNode providerConfig) {
        return normalizeToSupported(text(providerConfig, "orchestrationLlmProvider"), SUPPORTED_LLM_PROVIDERS, "");
    }

    public static String orchestrationModel(JsonNode providerConfig) {
        return text(providerConfig, "orchestrationModel");
    }

    public static int orchestrationMaxTokens(JsonNode providerConfig) {
        return readInt(providerConfig, "orchestrationMaxTokens");
    }

    public static Double orchestrationTemperature(JsonNode providerConfig) {
        return readDouble(providerConfig, "orchestrationTemperature");
    }

    public static int orchestrationTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "orchestrationTimeout");
    }

    public static String generationLlmProvider(JsonNode providerConfig) {
        return normalizeToSupported(text(providerConfig, "generationLlmProvider"), SUPPORTED_LLM_PROVIDERS, "");
    }

    public static String generationModel(JsonNode providerConfig) {
        return text(providerConfig, "generationModel");
    }

    public static int generationMaxTokens(JsonNode providerConfig) {
        return readInt(providerConfig, "generationMaxTokens");
    }

    public static Double generationTemperature(JsonNode providerConfig) {
        return readDouble(providerConfig, "generationTemperature");
    }

    public static int generationTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "generationTimeout");
    }

    public static String openAiBaseUrl(JsonNode providerConfig) {
        return text(providerConfig, "openaiBaseUrl");
    }

    public static boolean openAiValidateOnStartup(JsonNode providerConfig) {
        return readBoolean(providerConfig, "openaiValidateOnStartup");
    }

    public static String openAiModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "openaiModel");
        return configured.isBlank() ? defaultLlmModel(LLM_PROVIDER_OPENAI) : configured;
    }

    public static String openAiEmbeddingModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "openaiEmbeddingModel");
        return configured.isBlank() ? defaultEmbeddingModel(EMBEDDING_PROVIDER_OPENAI) : configured;
    }

    public static int openAiEmbeddingDimensions(JsonNode providerConfig) {
        return positiveOrDefault(
            readInt(providerConfig, "openaiEmbeddingDimensions"),
            defaultEmbeddingDimensions(EMBEDDING_PROVIDER_OPENAI)
        );
    }

    public static int openAiMaxTokens(JsonNode providerConfig) {
        return readInt(providerConfig, "openaiMaxTokens");
    }

    public static Double openAiTemperature(JsonNode providerConfig) {
        return readDouble(providerConfig, "openaiTemperature");
    }

    public static int openAiTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "openaiTimeout");
    }

    public static int openAiPriority(JsonNode providerConfig) {
        return readInt(providerConfig, "openaiPriority");
    }

    public static String anthropicBaseUrl(JsonNode providerConfig) {
        return text(providerConfig, "anthropicBaseUrl");
    }

    public static String anthropicModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "anthropicModel");
        return configured.isBlank() ? defaultLlmModel(LLM_PROVIDER_ANTHROPIC) : configured;
    }

    public static int anthropicMaxTokens(JsonNode providerConfig) {
        return readInt(providerConfig, "anthropicMaxTokens");
    }

    public static Double anthropicTemperature(JsonNode providerConfig) {
        return readDouble(providerConfig, "anthropicTemperature");
    }

    public static int anthropicTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "anthropicTimeout");
    }

    public static int anthropicPriority(JsonNode providerConfig) {
        return readInt(providerConfig, "anthropicPriority");
    }

    public static int qdrantPort(JsonNode providerConfig) {
        return positiveOrDefault(readInt(providerConfig, "qdrantPort"), DEFAULT_QDRANT_PORT);
    }

    public static int qdrantGrpcPort(JsonNode providerConfig) {
        return positiveOrDefault(readInt(providerConfig, "qdrantGrpcPort"), DEFAULT_QDRANT_GRPC_PORT);
    }

    public static boolean qdrantPreferGrpc(JsonNode providerConfig) {
        return readBoolean(providerConfig, "qdrantPreferGrpc");
    }

    public static boolean qdrantManagedCollectionsEnabled(JsonNode providerConfig) {
        return readBoolean(providerConfig, "qdrantManagedCollectionsEnabled");
    }

    public static boolean qdrantPlatformManaged(JsonNode providerConfig) {
        return usesQdrant(providerConfig)
            && VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(resolveVectorProvisioningMode(providerConfig));
    }

    public static boolean qdrantExternalExisting(JsonNode providerConfig) {
        return usesQdrant(providerConfig)
            && VECTOR_PROVISIONING_MODE_EXTERNAL_EXISTING.equals(resolveVectorProvisioningMode(providerConfig));
    }

    public static boolean qdrantCollectionsManagedByPlatform(JsonNode providerConfig) {
        return qdrantPlatformManaged(providerConfig) || qdrantManagedCollectionsEnabled(providerConfig);
    }

    public static boolean managedVectorProvisioningRequested(JsonNode providerConfig) {
        String vectorStrategy = resolveVectorStrategy(providerConfig);
        String provisioningMode = resolveVectorProvisioningMode(providerConfig);
        if (VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(provisioningMode)
            && supportsPlatformManagedVector(vectorStrategy)) {
            return true;
        }
        return usesQdrant(providerConfig) && qdrantManagedCollectionsEnabled(providerConfig);
    }

    public static int qdrantTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "qdrantTimeout");
    }

    public static String qdrantCloudAccountId(JsonNode providerConfig) {
        return text(providerConfig, "qdrantCloudAccountId");
    }

    public static String qdrantCloudProviderId(JsonNode providerConfig) {
        String configured = text(providerConfig, "qdrantCloudProviderId");
        String normalized = configured.isBlank() ? "aws" : configured.toLowerCase(Locale.ROOT);
        return SUPPORTED_QDRANT_CLOUD_PROVIDERS.contains(normalized) ? normalized : "aws";
    }

    public static String qdrantCloudRegionId(JsonNode providerConfig) {
        return text(providerConfig, "qdrantCloudRegionId");
    }

    public static String qdrantCloudPackageId(JsonNode providerConfig) {
        return text(providerConfig, "qdrantCloudPackageId");
    }

    public static String qdrantCloudClusterNameOverride(JsonNode providerConfig) {
        return text(providerConfig, "qdrantCloudClusterNameOverride");
    }

    public static String qdrantRuntimeApiKeySecretName(JsonNode providerConfig) {
        return text(providerConfig, "qdrantRuntimeApiKeySecretName");
    }

    public static String azureEndpoint(JsonNode providerConfig) {
        return text(providerConfig, "azureEndpoint");
    }

    public static String azureDeploymentName(JsonNode providerConfig) {
        return text(providerConfig, "azureDeploymentName");
    }

    public static String azureEmbeddingDeploymentName(JsonNode providerConfig) {
        return text(providerConfig, "azureEmbeddingDeploymentName");
    }

    public static String azureApiVersion(JsonNode providerConfig) {
        String configured = text(providerConfig, "azureApiVersion");
        return configured.isBlank() ? "2024-02-15-preview" : configured;
    }

    public static boolean azureValidateOnStartup(JsonNode providerConfig) {
        return readBoolean(providerConfig, "azureValidateOnStartup");
    }

    public static int azureTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "azureTimeout");
    }

    public static int azurePriority(JsonNode providerConfig) {
        return readInt(providerConfig, "azurePriority");
    }

    public static String cohereModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "cohereModel");
        return configured.isBlank() ? defaultLlmModel(LLM_PROVIDER_COHERE) : configured;
    }

    public static String cohereBaseUrl(JsonNode providerConfig) {
        return text(providerConfig, "cohereBaseUrl");
    }

    public static String cohereEmbeddingModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "cohereEmbeddingModel");
        return configured.isBlank() ? defaultEmbeddingModel(EMBEDDING_PROVIDER_COHERE) : configured;
    }

    public static boolean cohereValidateOnStartup(JsonNode providerConfig) {
        return readBoolean(providerConfig, "cohereValidateOnStartup");
    }

    public static int cohereMaxTokens(JsonNode providerConfig) {
        return readInt(providerConfig, "cohereMaxTokens");
    }

    public static Double cohereTemperature(JsonNode providerConfig) {
        return readDouble(providerConfig, "cohereTemperature");
    }

    public static int cohereTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "cohereTimeout");
    }

    public static int coherePriority(JsonNode providerConfig) {
        return readInt(providerConfig, "coherePriority");
    }

    public static String geminiModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "geminiModel");
        return configured.isBlank() ? defaultLlmModel(LLM_PROVIDER_GEMINI) : configured;
    }

    public static String geminiBaseUrl(JsonNode providerConfig) {
        return text(providerConfig, "geminiBaseUrl");
    }

    public static String geminiEmbeddingModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "geminiEmbeddingModel");
        return configured.isBlank() ? defaultEmbeddingModel(EMBEDDING_PROVIDER_GEMINI) : configured;
    }

    public static boolean geminiValidateOnStartup(JsonNode providerConfig) {
        return readBoolean(providerConfig, "geminiValidateOnStartup");
    }

    public static int geminiMaxTokens(JsonNode providerConfig) {
        return readInt(providerConfig, "geminiMaxTokens");
    }

    public static Double geminiTemperature(JsonNode providerConfig) {
        return readDouble(providerConfig, "geminiTemperature");
    }

    public static int geminiTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "geminiTimeout");
    }

    public static int geminiPriority(JsonNode providerConfig) {
        return readInt(providerConfig, "geminiPriority");
    }

    public static String onnxModelAlias(JsonNode providerConfig) {
        String configured = text(providerConfig, "onnxModelAlias");
        return configured.isBlank() ? defaultEmbeddingModel(EMBEDDING_PROVIDER_ONNX) : configured;
    }

    public static String onnxModelPath(JsonNode providerConfig) {
        return text(providerConfig, "onnxModelPath");
    }

    public static String onnxTokenizerPath(JsonNode providerConfig) {
        return text(providerConfig, "onnxTokenizerPath");
    }

    public static int onnxMaxSequenceLength(JsonNode providerConfig) {
        return positiveOrDefault(readInt(providerConfig, "onnxMaxSequenceLength"), 512);
    }

    public static boolean onnxUseGpu(JsonNode providerConfig) {
        return readBoolean(providerConfig, "onnxUseGpu");
    }

    public static String restEmbeddingBaseUrl(JsonNode providerConfig) {
        return text(providerConfig, "restEmbeddingBaseUrl");
    }

    public static boolean restEmbeddingValidateOnStartup(JsonNode providerConfig) {
        return readBoolean(providerConfig, "restEmbeddingValidateOnStartup");
    }

    public static String restEmbeddingEndpoint(JsonNode providerConfig) {
        String configured = text(providerConfig, "restEmbeddingEndpoint");
        return configured.isBlank() ? "/embed" : configured;
    }

    public static String restEmbeddingBatchEndpoint(JsonNode providerConfig) {
        String configured = text(providerConfig, "restEmbeddingBatchEndpoint");
        return configured.isBlank() ? "/embed/batch" : configured;
    }

    public static String restEmbeddingModel(JsonNode providerConfig) {
        String configured = text(providerConfig, "restEmbeddingModel");
        return configured.isBlank() ? defaultEmbeddingModel(EMBEDDING_PROVIDER_REST) : configured;
    }

    public static int restEmbeddingTimeoutMs(JsonNode providerConfig) {
        return positiveOrDefault(readInt(providerConfig, "restEmbeddingTimeoutMs"), 30000);
    }

    public static String pineconeEnvironment(JsonNode providerConfig) {
        return text(providerConfig, "pineconeEnvironment");
    }

    public static boolean pineconeManagedIndexEnabled(JsonNode providerConfig) {
        return readBoolean(providerConfig, "pineconeManagedIndexEnabled");
    }

    public static String pineconeRuntimeApiKeySecretName(JsonNode providerConfig) {
        return text(providerConfig, "pineconeRuntimeApiKeySecretName");
    }

    public static String pineconeIndexName(JsonNode providerConfig) {
        return text(providerConfig, "pineconeIndexName");
    }

    public static String pineconeProjectId(JsonNode providerConfig) {
        return text(providerConfig, "pineconeProjectId");
    }

    public static String pineconeApiHost(JsonNode providerConfig) {
        return text(providerConfig, "pineconeApiHost");
    }

    public static int pineconeDimensions(JsonNode providerConfig) {
        return positiveOrDefault(readInt(providerConfig, "pineconeDimensions"), 1536);
    }

    public static String pineconeCloud(JsonNode providerConfig) {
        String configured = text(providerConfig, "pineconeCloud");
        return configured.isBlank() ? "aws" : configured.toLowerCase(Locale.ROOT);
    }

    public static String pineconeRegion(JsonNode providerConfig) {
        return text(providerConfig, "pineconeRegion");
    }

    public static String pineconeMetric(JsonNode providerConfig) {
        String configured = text(providerConfig, "pineconeMetric");
        return configured.isBlank() ? "cosine" : configured.toLowerCase(Locale.ROOT);
    }

    public static boolean pineconeDeletionProtectionEnabled(JsonNode providerConfig) {
        return readBoolean(providerConfig, "pineconeDeletionProtectionEnabled");
    }

    public static String weaviateScheme(JsonNode providerConfig) {
        String configured = text(providerConfig, "weaviateScheme");
        return configured.isBlank() ? "https" : configured;
    }

    public static String weaviateHost(JsonNode providerConfig) {
        return text(providerConfig, "weaviateHost");
    }

    public static int weaviatePort(JsonNode providerConfig) {
        return positiveOrDefault(readInt(providerConfig, "weaviatePort"), DEFAULT_WEAVIATE_PORT);
    }

    public static boolean weaviateConsistencyLevelStrong(JsonNode providerConfig) {
        return readBoolean(providerConfig, "weaviateConsistencyLevelStrong");
    }

    public static int weaviateTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "weaviateTimeout");
    }

    public static String milvusHost(JsonNode providerConfig) {
        return text(providerConfig, "milvusHost");
    }

    public static int milvusPort(JsonNode providerConfig) {
        return positiveOrDefault(readInt(providerConfig, "milvusPort"), DEFAULT_MILVUS_PORT);
    }

    public static String milvusDatabaseName(JsonNode providerConfig) {
        String configured = text(providerConfig, "milvusDatabaseName");
        return configured.isBlank() ? "default" : configured;
    }

    public static boolean milvusSecure(JsonNode providerConfig) {
        return readBoolean(providerConfig, "milvusSecure");
    }

    public static boolean milvusFlushOnWrite(JsonNode providerConfig) {
        return readBoolean(providerConfig, "milvusFlushOnWrite");
    }

    public static int milvusTimeout(JsonNode providerConfig) {
        return readInt(providerConfig, "milvusTimeout");
    }

    public static String milvusRuntimeUsernameSecretName(JsonNode providerConfig) {
        return text(providerConfig, "milvusRuntimeUsernameSecretName");
    }

    public static String milvusRuntimePasswordSecretName(JsonNode providerConfig) {
        return text(providerConfig, "milvusRuntimePasswordSecretName");
    }

    public static String zillizCloudProjectId(JsonNode providerConfig) {
        return text(providerConfig, "zillizCloudProjectId");
    }

    public static String zillizCloudRegionId(JsonNode providerConfig) {
        return text(providerConfig, "zillizCloudRegionId");
    }

    public static String zillizCloudClusterNameOverride(JsonNode providerConfig) {
        return text(providerConfig, "zillizCloudClusterNameOverride");
    }

    public static String zillizCloudClusterPlan(JsonNode providerConfig) {
        String configured = text(providerConfig, "zillizCloudClusterPlan");
        if (configured.isBlank()) {
            return "Serverless";
        }
        for (String candidate : SUPPORTED_ZILLIZ_CLOUD_PLANS) {
            if (candidate.equalsIgnoreCase(configured)) {
                return candidate;
            }
        }
        return configured;
    }

    public static String zillizCloudCuType(JsonNode providerConfig) {
        return text(providerConfig, "zillizCloudCuType");
    }

    public static int zillizCloudCuSize(JsonNode providerConfig) {
        return readInt(providerConfig, "zillizCloudCuSize");
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    public static int readInt(JsonNode node, String field) {
        if (node == null) {
            return 0;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        if (value.isIntegralNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return value.asInt(0);
    }

    public static Double readDouble(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isFloatingPointNumber() || value.isIntegralNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean readBoolean(JsonNode node, String field) {
        return readBoolean(node, field, false);
    }

    private static boolean readBoolean(JsonNode node, String field, boolean fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText().trim());
        }
        return value.asBoolean(fallback);
    }

    private static String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("").trim();
    }

    private static String normalizeToSupported(String raw, Set<String> supported, String fallback) {
        String normalized = normalize(raw);
        return supported.contains(normalized) ? normalized : fallback;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }
}

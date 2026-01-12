
package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AI providers.
 *
 * <p>
 * The modular provider architecture separates LLM providers and embedding providers so they can
 * be independently selected and combined. This class exposes strongly typed configuration objects
 * for each supported provider along with helper methods to resolve the active provider defaults.
 * </p>
 *
 * <p>
 * All defaults are production-ready and avoid hard-coded credentials. Secrets must be supplied via
 * environment variables or externalized configuration consistent with Spring Boot best practices.
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {

    /**
     * Whether AI capabilities are globally enabled.
     */
    private boolean enabled = true;

    /**
     * Active LLM provider identifier (e.g. openai, anthropic, cohere, azure).
     */
    private String llmProvider = "openai";

    /**
     * Active embedding provider identifier (e.g. onnx, openai, rest).
     */
    private String embeddingProvider = "onnx";

    /**
     * Enable automatic fallback when the preferred provider fails.
     */
    private Boolean enableFallback = true;

    // Provider specific configuration blocks
    private final OpenAIConfig openai = new OpenAIConfig();
    private final AzureConfig azure = new AzureConfig();
    private final AnthropicConfig anthropic = new AnthropicConfig();
    private final CohereConfig cohere = new CohereConfig();
    private final GeminiConfig gemini = new GeminiConfig();
    private final ONNXConfig onnx = new ONNXConfig();
    private final RestConfig rest = new RestConfig();
    private final PineconeConfig pinecone = new PineconeConfig();
    private final WeaviateConfig weaviate = new WeaviateConfig();
    private final QdrantConfig qdrant = new QdrantConfig();
    private final MilvusConfig milvus = new MilvusConfig();

    /**
     * Resolve defaults for the configured primary LLM provider.
     *
     * @return defaults for the current LLM provider
     */
    public GenerationDefaults resolveLlmDefaults() {
        String provider = normalize(llmProvider);
        return switch (provider) {
            case "anthropic" -> anthropic.toGenerationDefaults("anthropic");
            case "cohere" -> cohere.toGenerationDefaults("cohere");
            case "gemini" -> gemini.toGenerationDefaults("gemini");
            case "azure" -> azure.toGenerationDefaults("azure");
            case "openai" ->
                openai.toGenerationDefaults("openai");
            default -> openai.toGenerationDefaults("openai");
        };
    }

    /**
     * Resolve defaults for the configured primary embedding provider.
     *
     * @return defaults for the current embedding provider
     */
    public EmbeddingDefaults resolveEmbeddingDefaults() {
        String provider = normalize(embeddingProvider);
        return switch (provider) {
            case "openai" -> openai.toEmbeddingDefaults("openai");
            case "azure" -> azure.toEmbeddingDefaults("azure");
            case "gemini" -> gemini.toEmbeddingDefaults("gemini");
            case "cohere" -> cohere.toEmbeddingDefaults("cohere");
            case "rest" -> rest.toEmbeddingDefaults("rest");
            case "onnx" ->
                onnx.toEmbeddingDefaults("onnx");
            default -> onnx.toEmbeddingDefaults("onnx");
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * Normalized generation defaults for the active LLM provider.
     *
     * @param providerName provider identifier
     * @param model model identifier for content generation
     * @param maxTokens token limit per request
     * @param temperature sampling temperature
     * @param timeoutSeconds request timeout
     * @param priority provider priority for selection (higher is preferred)
     */
    public record GenerationDefaults(
        String providerName,
        String model,
        Integer maxTokens,
        Double temperature,
        Integer timeoutSeconds,
        Integer priority
    ) {}

    /**
     * Normalized embedding defaults for the active embedding provider.
     *
     * @param providerName provider identifier
     * @param model embedding model identifier
     */
    public record EmbeddingDefaults(
        String providerName,
        String model
    ) {}

    @Data
    public static class OpenAIConfig {
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer maxTokens;
        private Double temperature;
        private Integer timeout;
        private Integer priority;
        private String embeddingModel;
        /**
         * Optional dimension reduction for text-embedding-3 models.
         * For text-embedding-3-small: can reduce from 1536 to 512-1536
         * For text-embedding-3-large: can reduce from 3072 to 256-3072
         * If null, uses model's default dimensions.
         */
        private Integer embeddingDimensions;

        GenerationDefaults toGenerationDefaults(String providerName) {
            return new GenerationDefaults(
                providerName,
                model,
                maxTokens,
                temperature,
                timeout,
                priority
            );
        }

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, embeddingModel);
        }
    }

    @Data
    public static class AzureConfig {
        private boolean enabled;
        private String apiKey;
        private String endpoint;
        private String deploymentName;
        private String embeddingDeploymentName;
        private String apiVersion;
        private Integer timeout;
        private Integer priority;

        GenerationDefaults toGenerationDefaults(String providerName) {
            return new GenerationDefaults(
                providerName,
                deploymentName,
                2000,
                0.3,
                timeout,
                priority
            );
        }

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, embeddingDeploymentName);
        }
    }

    @Data
    public static class AnthropicConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer maxTokens;
        private Double temperature;
        private Integer timeout;
        private Integer priority;

        GenerationDefaults toGenerationDefaults(String providerName) {
            return new GenerationDefaults(
                providerName,
                model,
                maxTokens,
                temperature,
                timeout,
                priority
            );
        }
    }

    @Data
    public static class GeminiConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String model;
        private String embeddingModel;
        private Integer maxTokens;
        private Double temperature;
        private Integer timeout;
        private Integer priority;

        GenerationDefaults toGenerationDefaults(String providerName) {
            return new GenerationDefaults(
                providerName,
                model,
                maxTokens,
                temperature,
                timeout,
                priority
            );
        }

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, embeddingModel);
        }
    }

    @Data
    public static class CohereConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer maxTokens;
        private Double temperature;
        private Integer timeout;
        private Integer priority;
        private String embeddingModel;

        GenerationDefaults toGenerationDefaults(String providerName) {
            return new GenerationDefaults(
                providerName,
                model,
                maxTokens,
                temperature,
                timeout,
                priority
            );
        }

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, embeddingModel);
        }
    }

    @Data
    public static class ONNXConfig {
        private boolean enabled = true;
        private String modelPath;
        private String tokenizerPath;
        private Integer maxSequenceLength = 512;
        private Boolean useGpu = false;
        private String modelAlias;

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, modelAlias);
        }
    }

    @Data
    public static class WeaviateConfig {
        private boolean enabled;
        private String scheme = "https";
        private String host;
        private Integer port = 443;
        private String apiKey;
        private Integer timeout = 30;
        private Boolean consistencyLevelStrong = false;
    }

    @Data
    public static class QdrantConfig {
        private boolean enabled;
        private String host;
        private Integer port = 6333;
        private String apiKey;
        private Integer timeout = 30;
        private Integer grpcPort = 6334;
        private Boolean preferGrpc = false;
    }

    @Data
    public static class MilvusConfig {
        private boolean enabled;
        private String host;
        private Integer port = 19530;
        private String username;
        private String password;
        private String databaseName = "default";
        private Integer timeout = 30;
        private Boolean secure = false;
        /**
         * When enabled, forces a Milvus flush after every write. This makes writes immediately durable
         * but is extremely expensive (and will drastically slow down integration tests).
         */
        private Boolean flushOnWrite = false;
    }

    @Data
    public static class RestConfig {
        private boolean enabled;
        private String baseUrl;
        private String endpoint = "/embed";
        private String batchEndpoint = "/embed/batch";
        private Integer timeout = 30000;
        private String model;

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, model);
        }
    }

    @Data
    public static class PineconeConfig {
        private boolean enabled;
        private String apiKey;
        private String environment;
        private String indexName;
        private Integer dimensions = 1536;
        private String projectId;
        private String apiHost;
    }
}

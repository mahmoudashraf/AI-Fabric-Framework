
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
    private final ONNXConfig onnx = new ONNXConfig();
    private final RestConfig rest = new RestConfig();
    private final PineconeConfig pinecone = new PineconeConfig();

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
            case "rest" -> rest.toEmbeddingDefaults("rest");
            case "onnx" ->
                onnx.toEmbeddingDefaults("onnx");
            default -> onnx.toEmbeddingDefaults("onnx");
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    // --------------------------------------------------------------------------------------------
    // Legacy accessors (delegating to nested configuration)
    // --------------------------------------------------------------------------------------------

    public String getOpenaiApiKey() {
        return openai.getApiKey();
    }

    public String getOpenaiModel() {
        return openai.getModel();
    }

    public String getOpenaiEmbeddingModel() {
        return openai.getEmbeddingModel();
    }

    public Integer getOpenaiMaxTokens() {
        return openai.getMaxTokens();
    }

    public Double getOpenaiTemperature() {
        return openai.getTemperature();
    }

    public Integer getOpenaiTimeout() {
        return openai.getTimeout();
    }

    public String getOnnxModelPath() {
        return onnx.getModelPath();
    }

    public String getOnnxTokenizerPath() {
        return onnx.getTokenizerPath();
    }

    public Integer getOnnxMaxSequenceLength() {
        return onnx.getMaxSequenceLength();
    }

    public Boolean getOnnxUseGpu() {
        return onnx.getUseGpu();
    }

    public String getRestBaseUrl() {
        return rest.getBaseUrl();
    }

    public String getRestEndpoint() {
        return rest.getEndpoint();
    }

    public String getRestBatchEndpoint() {
        return rest.getBatchEndpoint();
    }

    public Integer getRestTimeout() {
        return rest.getTimeout();
    }

    public String getRestModel() {
        return rest.getModel();
    }

    public String getPineconeApiKey() {
        return pinecone.getApiKey();
    }

    public String getPineconeEnvironment() {
        return pinecone.getEnvironment();
    }

    public String getPineconeIndexName() {
        return pinecone.getIndexName();
    }

    public Integer getPineconeDimensions() {
        return pinecone.getDimensions();
    }

    public String getPineconeProjectId() {
        return pinecone.getProjectId();
    }

    public String getPineconeApiHost() {
        return pinecone.getApiHost();
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
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";
        private Integer maxTokens = 2000;
        private Double temperature = 0.3;
        private Integer timeout = 60;
        private Integer priority = 100;
        private String embeddingModel = "text-embedding-3-small";

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
        private String apiVersion = "2024-02-15-preview";
        private Integer timeout = 60;
        private Integer priority = 90;

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
        private String baseUrl = "https://api.anthropic.com/v1";
        private String model = "claude-3-opus-20240229";
        private Integer maxTokens = 4096;
        private Double temperature = 0.3;
        private Integer timeout = 60;
        private Integer priority = 80;

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
    public static class CohereConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl = "https://api.cohere.ai/v1";
        private String model = "command";
        private Integer maxTokens = 2000;
        private Double temperature = 0.3;
        private Integer timeout = 60;
        private Integer priority = 70;
        private String embeddingModel = "embed-english-v3.0";

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
        private String modelPath = "classpath:/models/embeddings/all-MiniLM-L6-v2.onnx";
        private String tokenizerPath = "classpath:/models/embeddings/tokenizer.json";
        private Integer maxSequenceLength = 512;
        private Boolean useGpu = false;
        private String modelAlias = "all-MiniLM-L6-v2";

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, modelAlias);
        }
    }

    @Data
    public static class RestConfig {
        private boolean enabled;
        private String baseUrl = "http://localhost:8000";
        private String endpoint = "/embed";
        private String batchEndpoint = "/embed/batch";
        private Integer timeout = 30000;
        private String model = "all-MiniLM-L6-v2";

        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, model);
        }
    }

    @Data
    public static class PineconeConfig {
        private boolean enabled;
        private String apiKey;
        private String environment = "us-east-1-aws";
        private String indexName = "ai-infrastructure";
        private Integer dimensions = 1536;
        private String projectId;
        private String apiHost;
    }
}

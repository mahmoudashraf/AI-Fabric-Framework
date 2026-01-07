package com.ai.infrastructure.validation;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates AI provider configuration at application startup.
 *
 * <p>This implements fail-fast validation for selected providers (greenfield).</p>
 */
@Slf4j
public class AIProviderConfigValidator {

    private final AIProviderConfig providerConfig;
    private final AIServiceConfig serviceConfig;

    public AIProviderConfigValidator(AIProviderConfig providerConfig, AIServiceConfig serviceConfig) {
        this.providerConfig = providerConfig;
        this.serviceConfig = serviceConfig;
    }

    @PostConstruct
    public void validateOnStartup() {
        if (providerConfig == null || serviceConfig == null) {
            throw new IllegalStateException("AI provider configuration beans must not be null");
        }

        if (!providerConfig.isEnabled()) {
            log.info("AI providers are disabled (ai.providers.enabled=false). Skipping provider configuration validation.");
            return;
        }

        ValidationResult result = validate();

        if (!result.isValid()) {
            String message = buildErrorMessage(result);
            log.error("AI Provider Configuration Validation Failed:\n{}", message);
            throw new IllegalStateException("Invalid AI provider configuration. See logs for details.");
        }

        for (String warning : result.warnings()) {
            log.warn("AI configuration warning: {}", warning);
        }

        log.info("AI provider configuration validation successful");
    }

    /**
     * Validate current provider/service configuration.
     *
     * @return validation result containing errors and warnings
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();

        validateLlmProvider(result);
        validateEmbeddingProvider(result);
        validateServiceConfig(result);

        return result;
    }

    private void validateLlmProvider(ValidationResult result) {
        String provider = normalize(providerConfig.getLlmProvider());

        switch (provider) {
            case "openai" -> validateOpenAI(result, true);
            case "anthropic" -> validateAnthropic(result);
            case "cohere" -> validateCohere(result);
            case "azure" -> validateAzure(result, true, false);
            default -> result.addError("ai.providers.llm-provider",
                "Unknown LLM provider: '" + provider + "'. Must be one of: openai, anthropic, cohere, azure");
        }
    }

    private void validateEmbeddingProvider(ValidationResult result) {
        String provider = normalize(providerConfig.getEmbeddingProvider());

        switch (provider) {
            case "openai" -> validateOpenAI(result, false);
            case "azure" -> validateAzure(result, false, true);
            case "rest" -> validateRest(result);
            case "onnx" -> validateOnnx(result);
            default -> result.addError("ai.providers.embedding-provider",
                "Unknown embedding provider: '" + provider + "'. Must be one of: openai, azure, rest, onnx");
        }
    }

    private void validateOpenAI(ValidationResult result, boolean isLlm) {
        AIProviderConfig.OpenAIConfig config = providerConfig.getOpenai();

        if (config == null) {
            result.addError("ai.providers.openai", "OpenAI configuration block is missing");
            return;
        }

        if (!config.isEnabled()) {
            if (isLlm) {
                result.addWarning("ai.providers.openai.enabled=false while OpenAI is the selected LLM provider. LLM generation will be unavailable.");
                return;
            }
            result.addError("ai.providers.openai.enabled", "OpenAI is selected but ai.providers.openai.enabled=false");
            return;
        }

        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.openai.api-key",
                "OpenAI API key is required when OpenAI is selected.");
        }

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.openai.base-url",
                "OpenAI base URL is required (e.g. https://api.openai.com/v1).");
        }

        if (isLlm && isBlank(config.getModel())) {
            result.addError("ai.providers.openai.model",
                "OpenAI model is required when OpenAI is the LLM provider.");
        }

        if (!isLlm && isBlank(config.getEmbeddingModel())) {
            result.addError("ai.providers.openai.embedding-model",
                "OpenAI embedding model is required when OpenAI is the embedding provider.");
        }

        if (config.getTimeout() == null || config.getTimeout() <= 0) {
            result.addWarning("ai.providers.openai.timeout is not configured or invalid. Requests may hang longer than expected.");
        }
    }

    private void validateAnthropic(ValidationResult result) {
        AIProviderConfig.AnthropicConfig config = providerConfig.getAnthropic();

        if (config == null) {
            result.addError("ai.providers.anthropic", "Anthropic configuration block is missing");
            return;
        }

        if (!config.isEnabled()) {
            result.addWarning("ai.providers.anthropic.enabled=false while Anthropic is the selected LLM provider. LLM generation will be unavailable.");
            return;
        }

        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.anthropic.api-key", "Anthropic API key is required when Anthropic is selected.");
        }

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.anthropic.base-url", "Anthropic base URL is required.");
        }

        if (isBlank(config.getModel())) {
            result.addError("ai.providers.anthropic.model", "Anthropic model is required when Anthropic is selected.");
        }
    }

    private void validateCohere(ValidationResult result) {
        AIProviderConfig.CohereConfig config = providerConfig.getCohere();

        if (config == null) {
            result.addError("ai.providers.cohere", "Cohere configuration block is missing");
            return;
        }

        if (!config.isEnabled()) {
            result.addWarning("ai.providers.cohere.enabled=false while Cohere is the selected LLM provider. LLM generation will be unavailable.");
            return;
        }

        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.cohere.api-key", "Cohere API key is required when Cohere is selected.");
        }

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.cohere.base-url", "Cohere base URL is required.");
        }

        if (isBlank(config.getModel())) {
            result.addError("ai.providers.cohere.model", "Cohere model is required when Cohere is selected.");
        }
    }

    private void validateAzure(ValidationResult result, boolean isLlm, boolean isEmbedding) {
        AIProviderConfig.AzureConfig config = providerConfig.getAzure();

        if (config == null) {
            result.addError("ai.providers.azure", "Azure configuration block is missing");
            return;
        }

        if (!config.isEnabled()) {
            if (isEmbedding) {
                result.addError("ai.providers.azure.enabled", "Azure is selected but ai.providers.azure.enabled=false");
                return;
            }
            result.addWarning("ai.providers.azure.enabled=false while Azure is the selected LLM provider. LLM generation will be unavailable.");
            return;
        }

        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.azure.api-key", "Azure API key is required when Azure is selected.");
        }

        if (isBlank(config.getEndpoint())) {
            result.addError("ai.providers.azure.endpoint", "Azure endpoint is required (your Azure OpenAI resource URL).");
        }

        if (isBlank(config.getApiVersion())) {
            result.addError("ai.providers.azure.api-version", "Azure api-version is required when Azure is selected.");
        }

        if (isLlm && isBlank(config.getDeploymentName())) {
            result.addError("ai.providers.azure.deployment-name", "Azure deployment-name is required when Azure is the LLM provider.");
        }

        if (isEmbedding && isBlank(config.getEmbeddingDeploymentName())) {
            result.addError("ai.providers.azure.embedding-deployment-name",
                "Azure embedding-deployment-name is required when Azure is the embedding provider.");
        }
    }

    private void validateRest(ValidationResult result) {
        AIProviderConfig.RestConfig config = providerConfig.getRest();

        if (config == null) {
            result.addError("ai.providers.rest", "REST embedding configuration block is missing");
            return;
        }

        if (!config.isEnabled()) {
            result.addError("ai.providers.rest.enabled", "REST is selected but ai.providers.rest.enabled=false");
            return;
        }

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.rest.base-url", "REST embedding base-url is required when REST is selected.");
        }

        if (isBlank(config.getEndpoint())) {
            result.addError("ai.providers.rest.endpoint", "REST embedding endpoint is required when REST is selected.");
        }
    }

    private void validateOnnx(ValidationResult result) {
        AIProviderConfig.ONNXConfig config = providerConfig.getOnnx();

        if (config == null) {
            result.addError("ai.providers.onnx", "ONNX configuration block is missing");
            return;
        }

        if (!config.isEnabled()) {
            result.addError("ai.providers.onnx.enabled", "ONNX is selected but ai.providers.onnx.enabled=false");
            return;
        }

        if (isBlank(config.getModelPath())) {
            result.addError("ai.providers.onnx.model-path", "ONNX model-path is required when ONNX is selected.");
        }

        if (isBlank(config.getTokenizerPath())) {
            result.addWarning("ai.providers.onnx.tokenizer-path is not configured. Falling back to legacy tokenization.");
        }
    }

    private void validateServiceConfig(ValidationResult result) {
        if (serviceConfig.getDefaultTimeout() == null || serviceConfig.getDefaultTimeout() <= 0) {
            result.addWarning("ai.service.default-timeout is not configured or invalid.");
        }

        if (serviceConfig.getMaxRetries() != null && serviceConfig.getMaxRetries() < 0) {
            result.addError("ai.service.max-retries", "Max retries cannot be negative.");
        }

        if (serviceConfig.getThreadPoolSize() != null && serviceConfig.getThreadPoolSize() <= 0) {
            result.addError("ai.service.thread-pool-size", "Thread pool size must be positive.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildErrorMessage(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AI Provider Configuration Errors ===\n");
        for (ValidationIssue error : result.errors()) {
            sb.append("  - ").append(error.key()).append(": ").append(error.message()).append('\n');
        }
        if (!result.warnings().isEmpty()) {
            sb.append("\n=== AI Provider Configuration Warnings ===\n");
            for (String warning : result.warnings()) {
                sb.append("  - ").append(warning).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Validation outcome container.
     */
    public static final class ValidationResult {
        private final List<ValidationIssue> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String key, String message) {
            errors.add(new ValidationIssue(key, message));
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<ValidationIssue> errors() {
            return List.copyOf(errors);
        }

        public List<String> warnings() {
            return List.copyOf(warnings);
        }
    }

    /**
     * Single validation error entry.
     */
    public record ValidationIssue(String key, String message) {}
}


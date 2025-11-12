package com.ai.infrastructure.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.dto.AIConfigurationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Configuration Service
 * 
 * This service manages AI configuration, feature toggles, and service settings.
 * It provides a centralized way to access and modify AI configuration.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service("aiServiceConfigurationService")
@RequiredArgsConstructor
public class AIConfigurationService {
    
    private final AIProviderConfig providerConfig;
    private final AIServiceConfig serviceConfig;
    
    /**
     * Get complete AI configuration
     * 
     * @return complete AI configuration
     */
    public AIConfigurationDto getConfiguration() {
        log.debug("Retrieving complete AI configuration");
        
        AIConfigurationDto config = new AIConfigurationDto();
        
        // Provider configuration
        config.setLlmProvider(providerConfig.getLlmProvider());
        config.setEmbeddingProvider(providerConfig.getEmbeddingProvider());
        config.setProviderDetails(buildProviderDetails());
        config.setOpenaiApiKey(providerConfig.getOpenaiApiKey());
        config.setOpenaiModel(providerConfig.getOpenaiModel());
        config.setOpenaiEmbeddingModel(providerConfig.getOpenaiEmbeddingModel());
        config.setPineconeApiKey(providerConfig.getPineconeApiKey());
        config.setPineconeEnvironment(providerConfig.getPineconeEnvironment());
        config.setPineconeIndexName(providerConfig.getPineconeIndexName());
        
        // Service configuration
        config.setEnabled(serviceConfig.getEnabled());
        config.setAutoConfiguration(serviceConfig.getAutoConfiguration());
        config.setCachingEnabled(serviceConfig.getCachingEnabled());
        config.setMetricsEnabled(serviceConfig.getMetricsEnabled());
        config.setHealthChecksEnabled(serviceConfig.getHealthChecksEnabled());
        config.setLoggingEnabled(serviceConfig.getLoggingEnabled());
        config.setDefaultTimeout(serviceConfig.getDefaultTimeout());
        config.setMaxRetries(serviceConfig.getMaxRetries());
        config.setRetryDelay(serviceConfig.getRetryDelay());
        config.setAsyncEnabled(serviceConfig.getAsyncEnabled());
        config.setThreadPoolSize(serviceConfig.getThreadPoolSize());
        config.setBatchProcessingEnabled(serviceConfig.getBatchProcessingEnabled());
        config.setBatchSize(serviceConfig.getBatchSize());
        config.setRateLimitingEnabled(serviceConfig.getRateLimitingEnabled());
        config.setRateLimitPerMinute(serviceConfig.getRateLimitPerMinute());
        config.setCircuitBreakerEnabled(serviceConfig.getCircuitBreakerEnabled());
        config.setCircuitBreakerThreshold(serviceConfig.getCircuitBreakerThreshold());
        config.setCircuitBreakerTimeout(serviceConfig.getCircuitBreakerTimeout());
        config.setFeatureFlagsEnabled(serviceConfig.getFeatureFlagsEnabled());
        config.setFeatureFlags(serviceConfig.getFeatureFlags());
        config.setServices((Map<String, Object>) (Map<?, ?>) serviceConfig.getServices());
        
        log.debug("Successfully retrieved AI configuration");
        return config;
    }
    
    /**
     * Get provider configuration
     * 
     * @return provider configuration
     */
    public Map<String, Object> getProviderConfiguration() {
        log.debug("Retrieving provider configuration");
        
        Map<String, Object> config = new HashMap<>();
        config.put("llmProvider", providerConfig.getLlmProvider());
        config.put("embeddingProvider", providerConfig.getEmbeddingProvider());
        config.put("providerDetails", buildProviderDetails());
        config.put("openaiApiKey", providerConfig.getOpenaiApiKey());
        config.put("openaiModel", providerConfig.getOpenaiModel());
        config.put("openaiEmbeddingModel", providerConfig.getOpenaiEmbeddingModel());
        config.put("pineconeApiKey", providerConfig.getPineconeApiKey());
        config.put("pineconeEnvironment", providerConfig.getPineconeEnvironment());
        config.put("pineconeIndexName", providerConfig.getPineconeIndexName());
        
        return config;
    }
    
    /**
     * Get service configuration
     * 
     * @return service configuration
     */
    public Map<String, Object> getServiceConfiguration() {
        log.debug("Retrieving service configuration");
        
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", serviceConfig.getEnabled());
        config.put("autoConfiguration", serviceConfig.getAutoConfiguration());
        config.put("cachingEnabled", serviceConfig.getCachingEnabled());
        config.put("metricsEnabled", serviceConfig.getMetricsEnabled());
        config.put("healthChecksEnabled", serviceConfig.getHealthChecksEnabled());
        config.put("loggingEnabled", serviceConfig.getLoggingEnabled());
        config.put("defaultTimeout", serviceConfig.getDefaultTimeout());
        config.put("maxRetries", serviceConfig.getMaxRetries());
        config.put("retryDelay", serviceConfig.getRetryDelay());
        config.put("asyncEnabled", serviceConfig.getAsyncEnabled());
        config.put("threadPoolSize", serviceConfig.getThreadPoolSize());
        config.put("batchProcessingEnabled", serviceConfig.getBatchProcessingEnabled());
        config.put("batchSize", serviceConfig.getBatchSize());
        config.put("rateLimitingEnabled", serviceConfig.getRateLimitingEnabled());
        config.put("rateLimitPerMinute", serviceConfig.getRateLimitPerMinute());
        config.put("circuitBreakerEnabled", serviceConfig.getCircuitBreakerEnabled());
        config.put("circuitBreakerThreshold", serviceConfig.getCircuitBreakerThreshold());
        config.put("circuitBreakerTimeout", serviceConfig.getCircuitBreakerTimeout());
        config.put("featureFlagsEnabled", serviceConfig.getFeatureFlagsEnabled());
        config.put("featureFlags", serviceConfig.getFeatureFlags());
        config.put("services", serviceConfig.getServices());
        
        return config;
    }
    
    /**
     * Check if a feature is enabled
     * 
     * @param featureName the feature name
     * @return true if feature is enabled
     */
    public boolean isFeatureEnabled(String featureName) {
        if (!serviceConfig.getFeatureFlagsEnabled()) {
            return true; // All features enabled if feature flags disabled
        }
        
        return serviceConfig.getFeatureFlags().getOrDefault(featureName, false);
    }
    
    /**
     * Check if a service is enabled
     * 
     * @param serviceName the service name
     * @return true if service is enabled
     */
    public boolean isServiceEnabled(String serviceName) {
        AIServiceConfig.ServiceConfig service = serviceConfig.getServices().get(serviceName);
        return service != null && service.isEnabled();
    }
    
    /**
     * Get service configuration
     * 
     * @param serviceName the service name
     * @return service configuration
     */
    public AIServiceConfig.ServiceConfig getServiceConfig(String serviceName) {
        return serviceConfig.getServices().get(serviceName);
    }
    
    /**
     * Validate configuration
     * 
     * @return validation result
     */
    public Map<String, Object> validateConfiguration() {
        log.debug("Validating AI configuration");
        
        Map<String, Object> result = new HashMap<>();
        boolean isValid = true;
        Map<String, String> errors = new HashMap<>();
        
        // Validate provider configuration
        String llmProvider = providerConfig.getLlmProvider() != null
            ? providerConfig.getLlmProvider().toLowerCase()
            : "openai";
        switch (llmProvider) {
            case "openai" -> {
                if (providerConfig.getOpenai().getApiKey() == null || providerConfig.getOpenai().getApiKey().isBlank()) {
                    errors.put("openaiApiKey", "OpenAI API key is required when OpenAI is the configured LLM provider.");
                    isValid = false;
                }
                if (providerConfig.getOpenai().getModel() == null || providerConfig.getOpenai().getModel().isBlank()) {
                    errors.put("openaiModel", "OpenAI model is required when OpenAI is the configured LLM provider.");
                    isValid = false;
                }
            }
            case "anthropic" -> {
                if (providerConfig.getAnthropic().getApiKey() == null || providerConfig.getAnthropic().getApiKey().isBlank()) {
                    errors.put("anthropicApiKey", "Anthropic API key is required when Anthropic is the configured LLM provider.");
                    isValid = false;
                }
                if (providerConfig.getAnthropic().getModel() == null || providerConfig.getAnthropic().getModel().isBlank()) {
                    errors.put("anthropicModel", "Anthropic model is required when Anthropic is the configured LLM provider.");
                    isValid = false;
                }
            }
            case "cohere" -> {
                if (providerConfig.getCohere().getApiKey() == null || providerConfig.getCohere().getApiKey().isBlank()) {
                    errors.put("cohereApiKey", "Cohere API key is required when Cohere is the configured LLM provider.");
                    isValid = false;
                }
                if (providerConfig.getCohere().getModel() == null || providerConfig.getCohere().getModel().isBlank()) {
                    errors.put("cohereModel", "Cohere model is required when Cohere is the configured LLM provider.");
                    isValid = false;
                }
            }
            default -> {
                // For unsupported providers we rely on downstream validation
            }
        }

        String embeddingProvider = providerConfig.getEmbeddingProvider() != null
            ? providerConfig.getEmbeddingProvider().toLowerCase()
            : "onnx";
        switch (embeddingProvider) {
            case "openai" -> {
                if (providerConfig.getOpenai().getApiKey() == null || providerConfig.getOpenai().getApiKey().isBlank()) {
                    errors.put("openaiEmbeddingApiKey", "OpenAI API key is required when OpenAI is the embedding provider.");
                    isValid = false;
                }
                if (providerConfig.getOpenai().getEmbeddingModel() == null || providerConfig.getOpenai().getEmbeddingModel().isBlank()) {
                    errors.put("openaiEmbeddingModel", "OpenAI embedding model is required when OpenAI is the embedding provider.");
                    isValid = false;
                }
            }
            case "rest" -> {
                if (providerConfig.getRest().getBaseUrl() == null || providerConfig.getRest().getBaseUrl().isBlank()) {
                    errors.put("restBaseUrl", "REST embedding base URL is required when REST is the embedding provider.");
                    isValid = false;
                }
                if (providerConfig.getRest().getEndpoint() == null || providerConfig.getRest().getEndpoint().isBlank()) {
                    errors.put("restEndpoint", "REST embedding endpoint is required when REST is the embedding provider.");
                    isValid = false;
                }
            }
            case "onnx" -> {
                if (providerConfig.getOnnx().getModelPath() == null || providerConfig.getOnnx().getModelPath().isBlank()) {
                    errors.put("onnxModelPath", "ONNX model path is required when ONNX is the embedding provider.");
                    isValid = false;
                }
            }
            default -> {
            }
        }
        
        // Validate service configuration
        if (serviceConfig.getDefaultTimeout() <= 0) {
            errors.put("defaultTimeout", "Default timeout must be positive");
            isValid = false;
        }
        
        if (serviceConfig.getMaxRetries() < 0) {
            errors.put("maxRetries", "Max retries must be non-negative");
            isValid = false;
        }
        
        if (serviceConfig.getRetryDelay() < 0) {
            errors.put("retryDelay", "Retry delay must be non-negative");
            isValid = false;
        }
        
        if (serviceConfig.getThreadPoolSize() <= 0) {
            errors.put("threadPoolSize", "Thread pool size must be positive");
            isValid = false;
        }
        
        if (serviceConfig.getBatchSize() <= 0) {
            errors.put("batchSize", "Batch size must be positive");
            isValid = false;
        }
        
        if (serviceConfig.getRateLimitPerMinute() <= 0) {
            errors.put("rateLimitPerMinute", "Rate limit per minute must be positive");
            isValid = false;
        }
        
        if (serviceConfig.getCircuitBreakerThreshold() <= 0) {
            errors.put("circuitBreakerThreshold", "Circuit breaker threshold must be positive");
            isValid = false;
        }
        
        if (serviceConfig.getCircuitBreakerTimeout() <= 0) {
            errors.put("circuitBreakerTimeout", "Circuit breaker timeout must be positive");
            isValid = false;
        }
        
        result.put("valid", isValid);
        result.put("errors", errors);
        
        log.debug("Configuration validation completed. Valid: {}", isValid);
        return result;
    }
    
    /**
     * Get configuration summary
     * 
     * @return configuration summary
     */
    public Map<String, Object> getConfigurationSummary() {
        log.debug("Generating configuration summary");
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("enabled", serviceConfig.getEnabled());
        summary.put("featuresEnabled", serviceConfig.getFeatureFlags().values().stream().mapToInt(b -> b ? 1 : 0).sum());
        summary.put("totalFeatures", serviceConfig.getFeatureFlags().size());
        summary.put("servicesEnabled", serviceConfig.getServices().values().stream().mapToInt(s -> s.isEnabled() ? 1 : 0).sum());
        summary.put("totalServices", serviceConfig.getServices().size());
        summary.put("cachingEnabled", serviceConfig.getCachingEnabled());
        summary.put("metricsEnabled", serviceConfig.getMetricsEnabled());
        summary.put("healthChecksEnabled", serviceConfig.getHealthChecksEnabled());
        summary.put("asyncEnabled", serviceConfig.getAsyncEnabled());
        summary.put("batchProcessingEnabled", serviceConfig.getBatchProcessingEnabled());
        summary.put("rateLimitingEnabled", serviceConfig.getRateLimitingEnabled());
        summary.put("circuitBreakerEnabled", serviceConfig.getCircuitBreakerEnabled());
        
        return summary;
    }

    private Map<String, Object> buildProviderDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("llmDefaults", Map.of(
            "provider", providerConfig.getLlmProvider(),
            "model", providerConfig.resolveLlmDefaults().model(),
            "maxTokens", providerConfig.resolveLlmDefaults().maxTokens(),
            "temperature", providerConfig.resolveLlmDefaults().temperature(),
            "timeout", providerConfig.resolveLlmDefaults().timeoutSeconds()
        ));
        details.put("embeddingDefaults", Map.of(
            "provider", providerConfig.getEmbeddingProvider(),
            "model", providerConfig.resolveEmbeddingDefaults().model()
        ));
        details.put("openai", providerConfig.getOpenai());
        details.put("anthropic", providerConfig.getAnthropic());
        details.put("cohere", providerConfig.getCohere());
        details.put("onnx", providerConfig.getOnnx());
        details.put("rest", providerConfig.getRest());
        details.put("pinecone", providerConfig.getPinecone());
        return details;
    }
}

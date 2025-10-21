package com.easyluxury.ai.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.dto.AIHealthDto;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.ai.infrastructure.config.AIConfigurationService;
import com.easyluxury.ai.config.EasyLuxuryAIConfig;
import com.easyluxury.ai.config.EasyLuxuryAIConfig.EasyLuxuryAISettings;
import com.easyluxury.ai.dto.AIHealthStatusDto;
import com.easyluxury.ai.dto.AIConfigurationStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Health Service for Easy Luxury
 * 
 * This service provides AI health monitoring and status reporting
 * for the Easy Luxury application, integrating with the AI Infrastructure module.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(EasyLuxuryAIConfig.EasyLuxuryAISettings.class)
public class AIHealthService {
    
    private final AIHealthIndicator aiHealthIndicator;
    private final AIConfigurationService aiConfigurationService;
    private final EasyLuxuryAISettings aiSettings;
    private final AIProviderConfig aiProviderConfig;
    private final AIServiceConfig aiServiceConfig;
    
    /**
     * Get comprehensive AI health status
     * 
     * @return AI health status
     */
    public AIHealthStatusDto getAIHealthStatus() {
        log.debug("Retrieving AI health status");
        
        try {
            // Get basic health from AI module
            Map<String, Object> healthMap = aiHealthIndicator.health();
            Map<String, Object> detailedHealthMap = aiHealthIndicator.getDetailedHealth();
            
            // Build Easy Luxury specific health status
            AIHealthStatusDto status = new AIHealthStatusDto();
            status.setOverallStatus((String) healthMap.get("status"));
            status.setHealthy((Boolean) healthMap.get("healthy"));
            status.setMessage((String) healthMap.get("message"));
            status.setTimestamp(System.currentTimeMillis());
            
            // Add Easy Luxury specific settings
            status.setProductRecommendationsEnabled(aiSettings.getEnableProductRecommendations());
            status.setUserBehaviorTrackingEnabled(aiSettings.getEnableUserBehaviorTracking());
            status.setSmartValidationEnabled(aiSettings.getEnableSmartValidation());
            status.setAiContentGenerationEnabled(aiSettings.getEnableAIContentGeneration());
            status.setAiSearchEnabled(aiSettings.getEnableAISearch());
            status.setAiRAGEnabled(aiSettings.getEnableAIRAG());
            
            // Add configuration details
            status.setConfigurationStatus(getConfigurationStatus());
            
            // Add performance metrics
            status.setPerformanceMetrics(getPerformanceMetrics());
            
            log.debug("AI health status retrieved successfully");
            return status;
            
        } catch (Exception e) {
            log.error("Error retrieving AI health status", e);
            
            AIHealthStatusDto errorStatus = new AIHealthStatusDto();
            errorStatus.setOverallStatus("ERROR");
            errorStatus.setHealthy(false);
            errorStatus.setMessage("Error retrieving AI health status: " + e.getMessage());
            errorStatus.setTimestamp(System.currentTimeMillis());
            errorStatus.setError(e.getClass().getSimpleName());
            
            return errorStatus;
        }
    }
    
    /**
     * Get AI configuration status
     * 
     * @return configuration status
     */
    public AIConfigurationStatusDto getConfigurationStatus() {
        log.debug("Retrieving AI configuration status");
        
        AIConfigurationStatusDto configStatus = new AIConfigurationStatusDto();
        
        // Check provider configuration
        configStatus.setOpenaiConfigured(
            aiProviderConfig.getOpenaiApiKey() != null && 
            !aiProviderConfig.getOpenaiApiKey().trim().isEmpty()
        );
        configStatus.setPineconeConfigured(
            aiProviderConfig.getPineconeApiKey() != null && 
            !aiProviderConfig.getPineconeApiKey().trim().isEmpty()
        );
        
        // Check service configuration
        configStatus.setAiServicesEnabled(aiServiceConfig.getEnabled());
        configStatus.setCachingEnabled(aiServiceConfig.getCachingEnabled());
        configStatus.setMetricsEnabled(aiServiceConfig.getMetricsEnabled());
        configStatus.setHealthChecksEnabled(aiServiceConfig.getHealthChecksEnabled());
        configStatus.setAsyncEnabled(aiServiceConfig.getAsyncEnabled());
        configStatus.setBatchProcessingEnabled(aiServiceConfig.getBatchProcessingEnabled());
        configStatus.setRateLimitingEnabled(aiServiceConfig.getRateLimitingEnabled());
        configStatus.setCircuitBreakerEnabled(aiServiceConfig.getCircuitBreakerEnabled());
        
        // Check Easy Luxury specific settings
        configStatus.setProductRecommendationsEnabled(aiSettings.getEnableProductRecommendations());
        configStatus.setUserBehaviorTrackingEnabled(aiSettings.getEnableUserBehaviorTracking());
        configStatus.setSmartValidationEnabled(aiSettings.getEnableSmartValidation());
        configStatus.setAiContentGenerationEnabled(aiSettings.getEnableAIContentGeneration());
        configStatus.setAiSearchEnabled(aiSettings.getEnableAISearch());
        configStatus.setAiRAGEnabled(aiSettings.getEnableAIRAG());
        
        // Add model information
        configStatus.setDefaultAIModel(aiSettings.getDefaultAIModel());
        configStatus.setDefaultEmbeddingModel(aiSettings.getDefaultEmbeddingModel());
        configStatus.setMaxTokens(aiSettings.getMaxTokens());
        configStatus.setTemperature(aiSettings.getTemperature());
        configStatus.setTimeoutSeconds(aiSettings.getTimeoutSeconds());
        
        // Add index names
        configStatus.setProductIndexName(aiSettings.getProductIndexName());
        configStatus.setUserIndexName(aiSettings.getUserIndexName());
        configStatus.setOrderIndexName(aiSettings.getOrderIndexName());
        
        return configStatus;
    }
    
    /**
     * Get AI performance metrics
     * 
     * @return performance metrics
     */
    public Map<String, Object> getPerformanceMetrics() {
        log.debug("Retrieving AI performance metrics");
        
        Map<String, Object> metrics = new HashMap<>();
        
        // Add service configuration metrics
        metrics.put("defaultTimeout", aiServiceConfig.getDefaultTimeout());
        metrics.put("maxRetries", aiServiceConfig.getMaxRetries());
        metrics.put("retryDelay", aiServiceConfig.getRetryDelay());
        metrics.put("threadPoolSize", aiServiceConfig.getThreadPoolSize());
        metrics.put("batchSize", aiServiceConfig.getBatchSize());
        metrics.put("rateLimitPerMinute", aiServiceConfig.getRateLimitPerMinute());
        metrics.put("circuitBreakerThreshold", aiServiceConfig.getCircuitBreakerThreshold());
        metrics.put("circuitBreakerTimeout", aiServiceConfig.getCircuitBreakerTimeout());
        
        // Add feature flags
        metrics.put("featureFlags", aiServiceConfig.getFeatureFlags());
        
        // Add service-specific configurations
        metrics.put("services", aiServiceConfig.getServices());
        
        return metrics;
    }
    
    /**
     * Validate AI configuration
     * 
     * @return validation result
     */
    public Map<String, Object> validateConfiguration() {
        log.debug("Validating AI configuration");
        
        boolean isValid = aiConfigurationService.validateConfiguration();
        
        // Add Easy Luxury specific validation
        Map<String, String> errors = new HashMap<>();
        if (!isValid) {
            errors.put("ai_configuration", "AI configuration validation failed");
        }
        
        // Validate Easy Luxury specific settings
        if (aiSettings.getMaxTokens() <= 0) {
            errors.put("maxTokens", "Max tokens must be positive");
        }
        
        if (aiSettings.getTemperature() < 0 || aiSettings.getTemperature() > 2) {
            errors.put("temperature", "Temperature must be between 0 and 2");
        }
        
        if (aiSettings.getTimeoutSeconds() <= 0) {
            errors.put("timeoutSeconds", "Timeout seconds must be positive");
        }
        
        if (aiSettings.getProductIndexName() == null || aiSettings.getProductIndexName().trim().isEmpty()) {
            errors.put("productIndexName", "Product index name is required");
        }
        
        if (aiSettings.getUserIndexName() == null || aiSettings.getUserIndexName().trim().isEmpty()) {
            errors.put("userIndexName", "User index name is required");
        }
        
        if (aiSettings.getOrderIndexName() == null || aiSettings.getOrderIndexName().trim().isEmpty()) {
            errors.put("orderIndexName", "Order index name is required");
        }
        
        Map<String, Object> validation = new HashMap<>();
        validation.put("errors", errors);
        validation.put("valid", errors.isEmpty());
        
        return validation;
    }
    
    /**
     * Get AI configuration summary
     * 
     * @return configuration summary
     */
    public Map<String, Object> getConfigurationSummary() {
        log.debug("Retrieving AI configuration summary");
        
        Map<String, Object> summary = aiConfigurationService.getConfigurationSummary();
        
        // Add Easy Luxury specific summary
        Map<String, Object> easyluxurySettings = new HashMap<>();
        easyluxurySettings.put("productRecommendationsEnabled", aiSettings.getEnableProductRecommendations());
        easyluxurySettings.put("userBehaviorTrackingEnabled", aiSettings.getEnableUserBehaviorTracking());
        easyluxurySettings.put("smartValidationEnabled", aiSettings.getEnableSmartValidation());
        easyluxurySettings.put("aiContentGenerationEnabled", aiSettings.getEnableAIContentGeneration());
        easyluxurySettings.put("aiSearchEnabled", aiSettings.getEnableAISearch());
        easyluxurySettings.put("aiRAGEnabled", aiSettings.getEnableAIRAG());
        easyluxurySettings.put("defaultAIModel", aiSettings.getDefaultAIModel());
        easyluxurySettings.put("defaultEmbeddingModel", aiSettings.getDefaultEmbeddingModel());
        easyluxurySettings.put("maxTokens", aiSettings.getMaxTokens());
        easyluxurySettings.put("temperature", aiSettings.getTemperature());
        easyluxurySettings.put("timeoutSeconds", aiSettings.getTimeoutSeconds());
        
        summary.put("easyluxurySettings", easyluxurySettings);
        
        return summary;
    }
}
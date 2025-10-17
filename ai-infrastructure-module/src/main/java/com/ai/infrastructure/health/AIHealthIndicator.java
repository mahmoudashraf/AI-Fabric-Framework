package com.ai.infrastructure.health;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.dto.AIHealthDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// Health indicator without Spring Boot Actuator dependency
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Health Indicator
 * 
 * This component provides health checks for AI services and configuration.
 * It integrates with Spring Boot Actuator for monitoring and health endpoints.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIHealthIndicator {
    
    private final AIProviderConfig providerConfig;
    private final AIServiceConfig serviceConfig;
    
    /**
     * Perform health check
     * 
     * @return health status map
     */
    public Map<String, Object> health() {
        try {
            log.debug("Performing AI health check");
            
            Map<String, Object> details = new HashMap<>();
            
            // Check if AI services are enabled
            if (!serviceConfig.isEnabled()) {
                details.put("status", "DISABLED");
                details.put("message", "AI services are disabled");
                details.put("healthy", false);
                return details;
            }
            
            // Check configuration validity
            boolean configValid = isConfigurationValid();
            if (!configValid) {
                details.put("status", "INVALID_CONFIG");
                details.put("message", "AI configuration is invalid");
                details.put("healthy", false);
                return details;
            }
            
            // Check provider configuration
            boolean providerValid = isProviderConfigurationValid();
            if (!providerValid) {
                details.put("status", "INVALID_PROVIDER_CONFIG");
                details.put("message", "AI provider configuration is invalid");
                details.put("healthy", false);
                return details;
            }
            
            // Check service configuration
            boolean serviceValid = isServiceConfigurationValid();
            if (!serviceValid) {
                details.put("status", "INVALID_SERVICE_CONFIG");
                details.put("message", "AI service configuration is invalid");
                details.put("healthy", false);
                return details;
            }
            
            // All checks passed
            details.put("status", "UP");
            details.put("message", "AI services are healthy");
            details.put("healthy", true);
            details.put("enabled", serviceConfig.isEnabled());
            details.put("featuresEnabled", serviceConfig.getFeatureFlags().values().stream().mapToInt(b -> b ? 1 : 0).sum());
            details.put("totalFeatures", serviceConfig.getFeatureFlags().size());
            details.put("servicesEnabled", serviceConfig.getServices().values().stream().mapToInt(s -> s.isEnabled() ? 1 : 0).sum());
            details.put("totalServices", serviceConfig.getServices().size());
            details.put("cachingEnabled", serviceConfig.isCachingEnabled());
            details.put("metricsEnabled", serviceConfig.isMetricsEnabled());
            details.put("healthChecksEnabled", serviceConfig.isHealthChecksEnabled());
            details.put("asyncEnabled", serviceConfig.isAsyncEnabled());
            details.put("batchProcessingEnabled", serviceConfig.isBatchProcessingEnabled());
            details.put("rateLimitingEnabled", serviceConfig.isRateLimitingEnabled());
            details.put("circuitBreakerEnabled", serviceConfig.isCircuitBreakerEnabled());
            
            log.debug("AI health check completed successfully");
            return details;
            
        } catch (Exception e) {
            log.error("Error during AI health check", e);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("status", "ERROR");
            errorDetails.put("message", "Error during health check: " + e.getMessage());
            errorDetails.put("healthy", false);
            errorDetails.put("error", e.getClass().getSimpleName());
            return errorDetails;
        }
    }
    
    /**
     * Get detailed health information
     * 
     * @return detailed health information
     */
    public AIHealthDto getDetailedHealth() {
        log.debug("Retrieving detailed AI health information");
        
        AIHealthDto health = new AIHealthDto();
        
        // Basic status
        health.setEnabled(serviceConfig.isEnabled());
        health.setStatus(serviceConfig.isEnabled() ? "UP" : "DOWN");
        
        // Configuration status
        health.setConfigurationValid(isConfigurationValid());
        health.setProviderConfigurationValid(isProviderConfigurationValid());
        health.setServiceConfigurationValid(isServiceConfigurationValid());
        
        // Feature status
        health.setFeaturesEnabled(serviceConfig.getFeatureFlags().values().stream().mapToInt(b -> b ? 1 : 0).sum());
        health.setTotalFeatures(serviceConfig.getFeatureFlags().size());
        
        // Service status
        health.setServicesEnabled(serviceConfig.getServices().values().stream().mapToInt(s -> s.isEnabled() ? 1 : 0).sum());
        health.setTotalServices(serviceConfig.getServices().size());
        
        // Capability status
        health.setCachingEnabled(serviceConfig.isCachingEnabled());
        health.setMetricsEnabled(serviceConfig.isMetricsEnabled());
        health.setHealthChecksEnabled(serviceConfig.isHealthChecksEnabled());
        health.setAsyncEnabled(serviceConfig.isAsyncEnabled());
        health.setBatchProcessingEnabled(serviceConfig.isBatchProcessingEnabled());
        health.setRateLimitingEnabled(serviceConfig.isRateLimitingEnabled());
        health.setCircuitBreakerEnabled(serviceConfig.isCircuitBreakerEnabled());
        
        // Provider information
        health.setOpenaiConfigured(providerConfig.getOpenaiApiKey() != null && !providerConfig.getOpenaiApiKey().trim().isEmpty());
        health.setPineconeConfigured(providerConfig.getPineconeApiKey() != null && !providerConfig.getPineconeApiKey().trim().isEmpty());
        
        return health;
    }
    
    /**
     * Check if configuration is valid
     * 
     * @return true if configuration is valid
     */
    private boolean isConfigurationValid() {
        try {
            // Check basic configuration
            return serviceConfig.getDefaultTimeout() > 0 &&
                   serviceConfig.getMaxRetries() >= 0 &&
                   serviceConfig.getRetryDelay() >= 0 &&
                   serviceConfig.getThreadPoolSize() > 0 &&
                   serviceConfig.getBatchSize() > 0 &&
                   serviceConfig.getRateLimitPerMinute() > 0 &&
                   serviceConfig.getCircuitBreakerThreshold() > 0 &&
                   serviceConfig.getCircuitBreakerTimeout() > 0;
        } catch (Exception e) {
            log.warn("Error validating configuration", e);
            return false;
        }
    }
    
    /**
     * Check if provider configuration is valid
     * 
     * @return true if provider configuration is valid
     */
    private boolean isProviderConfigurationValid() {
        try {
            // Check OpenAI configuration
            boolean openaiValid = providerConfig.getOpenaiApiKey() != null && 
                                !providerConfig.getOpenaiApiKey().trim().isEmpty() &&
                                providerConfig.getOpenaiModel() != null && 
                                !providerConfig.getOpenaiModel().trim().isEmpty() &&
                                providerConfig.getOpenaiEmbeddingModel() != null && 
                                !providerConfig.getOpenaiEmbeddingModel().trim().isEmpty();
            
            // Check Pinecone configuration (optional)
            boolean pineconeValid = providerConfig.getPineconeApiKey() == null || 
                                  providerConfig.getPineconeApiKey().trim().isEmpty() ||
                                  (providerConfig.getPineconeEnvironment() != null && 
                                   !providerConfig.getPineconeEnvironment().trim().isEmpty() &&
                                   providerConfig.getPineconeIndexName() != null && 
                                   !providerConfig.getPineconeIndexName().trim().isEmpty());
            
            return openaiValid && pineconeValid;
        } catch (Exception e) {
            log.warn("Error validating provider configuration", e);
            return false;
        }
    }
    
    /**
     * Check if service configuration is valid
     * 
     * @return true if service configuration is valid
     */
    private boolean isServiceConfigurationValid() {
        try {
            // Check service configurations
            return serviceConfig.getServices().values().stream()
                .allMatch(service -> service.getRateLimit() > 0 && service.getTimeout() > 0);
        } catch (Exception e) {
            log.warn("Error validating service configuration", e);
            return false;
        }
    }
}

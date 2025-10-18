package com.ai.infrastructure.monitoring;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.dto.AIHealthDto;
import com.ai.infrastructure.health.AIHealthIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AI Health Service
 * 
 * Comprehensive health monitoring service for AI infrastructure.
 * Provides real-time health status, performance metrics, and observability
 * for all AI services and providers.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIHealthService {
    
    private final AIHealthIndicator healthIndicator;
    private final AIProviderConfig providerConfig;
    private final AIServiceConfig serviceConfig;
    private final AIMetricsService metricsService;
    
    /**
     * Get comprehensive health status
     * 
     * @return comprehensive health status
     */
    public AIHealthDto getHealthStatus() {
        log.debug("Retrieving comprehensive AI health status");
        
        AIHealthDto health = healthIndicator.getDetailedHealth();
        
        // Add performance metrics
        health.setPerformanceMetrics(metricsService.getPerformanceMetrics());
        
        // Add provider status
        health.setProviderStatus(getProviderStatus());
        
        // Add service status
        health.setServiceStatus(getServiceStatus());
        
        // Add system status
        health.setSystemStatus(getSystemStatus());
        
        // Add last updated timestamp
        health.setLastUpdated(LocalDateTime.now());
        
        return health;
    }
    
    /**
     * Perform health check with timeout
     * 
     * @param timeoutSeconds timeout in seconds
     * @return health status
     */
    public AIHealthDto performHealthCheck(int timeoutSeconds) {
        log.debug("Performing AI health check with timeout: {} seconds", timeoutSeconds);
        
        try {
            CompletableFuture<AIHealthDto> healthFuture = CompletableFuture.supplyAsync(() -> {
                return getHealthStatus();
            });
            
            return healthFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.error("Health check failed with timeout", e);
            
            AIHealthDto errorHealth = new AIHealthDto();
            errorHealth.setStatus("TIMEOUT");
            errorHealth.setEnabled(false);
            errorHealth.setLastUpdated(LocalDateTime.now());
            errorHealth.setErrorMessage("Health check timed out after " + timeoutSeconds + " seconds");
            
            return errorHealth;
        }
    }
    
    /**
     * Get provider status
     * 
     * @return provider status map
     */
    private Map<String, Object> getProviderStatus() {
        Map<String, Object> providerStatus = new HashMap<>();
        
        // OpenAI status
        Map<String, Object> openaiStatus = new HashMap<>();
        openaiStatus.put("configured", providerConfig.getOpenaiApiKey() != null && !providerConfig.getOpenaiApiKey().trim().isEmpty());
        openaiStatus.put("model", providerConfig.getOpenaiModel());
        openaiStatus.put("embeddingModel", providerConfig.getOpenaiEmbeddingModel());
        openaiStatus.put("maxTokens", providerConfig.getOpenaiMaxTokens());
        openaiStatus.put("temperature", providerConfig.getOpenaiTemperature());
        openaiStatus.put("timeout", providerConfig.getOpenaiTimeout());
        providerStatus.put("openai", openaiStatus);
        
        // Pinecone status
        Map<String, Object> pineconeStatus = new HashMap<>();
        pineconeStatus.put("configured", providerConfig.getPineconeApiKey() != null && !providerConfig.getPineconeApiKey().trim().isEmpty());
        pineconeStatus.put("environment", providerConfig.getPineconeEnvironment());
        pineconeStatus.put("indexName", providerConfig.getPineconeIndexName());
        pineconeStatus.put("dimensions", providerConfig.getPineconeDimensions());
        providerStatus.put("pinecone", pineconeStatus);
        
        return providerStatus;
    }
    
    /**
     * Get service status
     * 
     * @return service status map
     */
    private Map<String, Object> getServiceStatus() {
        Map<String, Object> serviceStatus = new HashMap<>();
        
        // Core services
        Map<String, Object> coreServices = new HashMap<>();
        coreServices.put("aiCoreService", serviceConfig.getServices().get("aiCoreService").isEnabled());
        coreServices.put("aiEmbeddingService", serviceConfig.getServices().get("aiEmbeddingService").isEnabled());
        coreServices.put("aiSearchService", serviceConfig.getServices().get("aiSearchService").isEnabled());
        serviceStatus.put("core", coreServices);
        
        // RAG services
        Map<String, Object> ragServices = new HashMap<>();
        ragServices.put("ragService", serviceConfig.getServices().get("ragService").isEnabled());
        ragServices.put("vectorDatabaseService", serviceConfig.getServices().get("vectorDatabaseService").isEnabled());
        serviceStatus.put("rag", ragServices);
        
        // Advanced services
        Map<String, Object> advancedServices = new HashMap<>();
        advancedServices.put("behaviorTrackingService", serviceConfig.getServices().get("behaviorTrackingService").isEnabled());
        advancedServices.put("recommendationEngine", serviceConfig.getServices().get("recommendationEngine").isEnabled());
        advancedServices.put("smartValidationService", serviceConfig.getServices().get("smartValidationService").isEnabled());
        serviceStatus.put("advanced", advancedServices);
        
        return serviceStatus;
    }
    
    /**
     * Get system status
     * 
     * @return system status map
     */
    private Map<String, Object> getSystemStatus() {
        Map<String, Object> systemStatus = new HashMap<>();
        
        // JVM metrics
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvmMetrics = new HashMap<>();
        jvmMetrics.put("totalMemory", runtime.totalMemory());
        jvmMetrics.put("freeMemory", runtime.freeMemory());
        jvmMetrics.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        jvmMetrics.put("maxMemory", runtime.maxMemory());
        jvmMetrics.put("availableProcessors", runtime.availableProcessors());
        systemStatus.put("jvm", jvmMetrics);
        
        // AI service metrics
        Map<String, Object> aiMetrics = new HashMap<>();
        aiMetrics.put("totalRequests", metricsService.getTotalRequests());
        aiMetrics.put("successfulRequests", metricsService.getSuccessfulRequests());
        aiMetrics.put("failedRequests", metricsService.getFailedRequests());
        aiMetrics.put("averageResponseTime", metricsService.getAverageResponseTime());
        aiMetrics.put("cacheHitRate", metricsService.getCacheHitRate());
        systemStatus.put("ai", aiMetrics);
        
        return systemStatus;
    }
    
    /**
     * Check if AI services are healthy
     * 
     * @return true if healthy
     */
    public boolean isHealthy() {
        try {
            Map<String, Object> health = healthIndicator.health();
            return (Boolean) health.get("healthy");
        } catch (Exception e) {
            log.error("Error checking health status", e);
            return false;
        }
    }
    
    /**
     * Get health summary
     * 
     * @return health summary
     */
    public Map<String, Object> getHealthSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("healthy", isHealthy());
        summary.put("enabled", serviceConfig.isEnabled());
        summary.put("totalRequests", metricsService.getTotalRequests());
        summary.put("successRate", metricsService.getSuccessRate());
        summary.put("averageResponseTime", metricsService.getAverageResponseTime());
        summary.put("lastUpdated", LocalDateTime.now());
        
        return summary;
    }
}
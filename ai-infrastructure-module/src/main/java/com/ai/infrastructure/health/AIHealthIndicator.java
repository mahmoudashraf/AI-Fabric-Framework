package com.ai.infrastructure.health;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.AIConfigurationService;
import com.ai.infrastructure.dto.AIHealthDto;
import com.ai.infrastructure.monitoring.AIHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI Health Indicator
 * 
 * Spring Boot Actuator health indicator for AI infrastructure services.
 * Provides health status for AI services, providers, and overall system health.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIHealthIndicator implements HealthIndicator {
    
    private final AIHealthService aiHealthService;
    private final AIConfigurationService configurationService;
    private final AIServiceConfig aiServiceConfig;
    
    @Override
    public Health health() {
        try {
            log.debug("Performing AI health check");
            
            // Check if AI services are enabled
            if (!aiServiceConfig.getEnabled()) {
                return Health.down()
                    .withDetail("status", "AI services disabled")
                    .withDetail("timestamp", LocalDateTime.now())
                    .build();
            }
            
            // Get comprehensive health information
            AIHealthDto healthInfo = aiHealthService.getHealthStatus();
            
            // Determine overall health status
            Health.Builder healthBuilder = determineHealthStatus(healthInfo);
            
            // Add detailed health information
            addHealthDetails(healthBuilder, healthInfo);
            
            // Add configuration information
            addConfigurationDetails(healthBuilder);
            
            // Add system information
            addSystemDetails(healthBuilder);
            
            Health health = healthBuilder.build();
            log.debug("AI health check completed: {}", health.getStatus());
            
            return health;
            
        } catch (Exception e) {
            log.error("AI health check failed", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", LocalDateTime.now())
                .withException(e)
                .build();
        }
    }
    
    /**
     * Determine the overall health status based on health information
     */
    private Health.Builder determineHealthStatus(AIHealthDto healthInfo) {
        boolean isHealthy = healthInfo.isHealthy();
        String status = healthInfo.getStatus();
        
        if (isHealthy && "UP".equals(status)) {
            return Health.up();
        } else if ("DEGRADED".equals(status)) {
            return Health.status("DEGRADED");
        } else {
            return Health.down();
        }
    }
    
    /**
     * Add detailed health information to the health response
     */
    private void addHealthDetails(Health.Builder healthBuilder, AIHealthDto healthInfo) {
        healthBuilder
            .withDetail("ai.health.status", healthInfo.getStatus())
            .withDetail("ai.health.healthy", healthInfo.isHealthy())
            .withDetail("ai.health.timestamp", healthInfo.getLastUpdated())
            .withDetail("ai.health.version", healthInfo.getVersion());
        
        // Add error message if present
        if (healthInfo.getErrorMessage() != null && !healthInfo.getErrorMessage().isEmpty()) {
            healthBuilder.withDetail("ai.health.error", healthInfo.getErrorMessage());
        }
        
        // Add performance metrics
        if (healthInfo.getPerformanceMetrics() != null) {
            healthBuilder.withDetail("ai.health.performance", healthInfo.getPerformanceMetrics());
        }
        
        // Add provider status
        if (healthInfo.getProviderStatus() != null) {
            healthBuilder.withDetail("ai.health.providers", healthInfo.getProviderStatus());
        }
        
        // Add service status
        if (healthInfo.getServiceStatus() != null) {
            healthBuilder.withDetail("ai.health.services", healthInfo.getServiceStatus());
        }
        
        // Add system status
        if (healthInfo.getSystemStatus() != null) {
            healthBuilder.withDetail("ai.health.system", healthInfo.getSystemStatus());
        }
    }
    
    /**
     * Add configuration details to the health response
     */
    private void addConfigurationDetails(Health.Builder healthBuilder) {
        try {
            Map<String, Object> configSummary = configurationService.getConfigurationSummary();
            healthBuilder.withDetail("ai.config", configSummary);
            
            // Add specific configuration details
            healthBuilder
                .withDetail("ai.config.enabled", aiServiceConfig.getEnabled())
                .withDetail("ai.config.defaultProvider", aiServiceConfig.getDefaultProvider())
                .withDetail("ai.config.fallbackProvider", aiServiceConfig.getFallbackProvider());
            
            // Add feature flags
            if (aiServiceConfig.getFeatures() != null) {
                healthBuilder.withDetail("ai.config.features", Map.of(
                    "rag", aiServiceConfig.getFeatures().getEnableRAG(),
                    "embeddings", aiServiceConfig.getFeatures().getEnableEmbeddings(),
                    "search", aiServiceConfig.getFeatures().getEnableSearch(),
                    "generation", aiServiceConfig.getFeatures().getEnableGeneration(),
                    "caching", aiServiceConfig.getFeatures().getEnableCaching(),
                    "monitoring", aiServiceConfig.getFeatures().getEnableMonitoring()
                ));
            }
            
        } catch (Exception e) {
            log.warn("Failed to add configuration details to health check", e);
            healthBuilder.withDetail("ai.config.error", "Failed to load configuration details");
        }
    }
    
    /**
     * Add system details to the health response
     */
    private void addSystemDetails(Health.Builder healthBuilder) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            healthBuilder
                .withDetail("ai.system.memory.total", totalMemory)
                .withDetail("ai.system.memory.used", usedMemory)
                .withDetail("ai.system.memory.free", freeMemory)
                .withDetail("ai.system.memory.max", maxMemory)
                .withDetail("ai.system.memory.usagePercent", (double) usedMemory / maxMemory * 100)
                .withDetail("ai.system.processors", runtime.availableProcessors())
                .withDetail("ai.system.uptime", System.currentTimeMillis() - getStartTime());
            
        } catch (Exception e) {
            log.warn("Failed to add system details to health check", e);
            healthBuilder.withDetail("ai.system.error", "Failed to load system details");
        }
    }
    
    /**
     * Get application start time (simplified implementation)
     */
    private long getStartTime() {
        // This is a simplified implementation
        // In a real application, you might want to track the actual start time
        return System.currentTimeMillis() - (24 * 60 * 60 * 1000); // Assume 24 hours ago
    }
    
    /**
     * Get detailed health information
     */
    public AIHealthDto getDetailedHealth() {
        return aiHealthService.getHealthStatus();
    }
    
    /**
     * Check if AI services are healthy
     */
    public boolean isHealthy() {
        try {
            AIHealthDto healthInfo = aiHealthService.getHealthStatus();
            return healthInfo.isHealthy() && "UP".equals(healthInfo.getStatus());
        } catch (Exception e) {
            log.error("Failed to check AI health status", e);
            return false;
        }
    }
    
    /**
     * Get health status summary
     */
    public Map<String, Object> getHealthSummary() {
        try {
            AIHealthDto healthInfo = aiHealthService.getHealthStatus();
            return Map.of(
                "status", healthInfo.getStatus(),
                "healthy", healthInfo.isHealthy(),
                "timestamp", healthInfo.getLastUpdated(),
                "version", healthInfo.getVersion()
            );
        } catch (Exception e) {
            log.error("Failed to get health summary", e);
            return Map.of(
                "status", "ERROR",
                "healthy", false,
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
}
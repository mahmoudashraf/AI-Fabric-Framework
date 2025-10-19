package com.ai.infrastructure.health;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.AIConfigurationService;
import com.ai.infrastructure.dto.AIHealthDto;
import com.ai.infrastructure.monitoring.AIHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class AIHealthIndicator {
    
    private final AIHealthService aiHealthService;
    private final AIConfigurationService configurationService;
    private final AIServiceConfig aiServiceConfig;
    
    /**
     * Get health status
     */
    public Map<String, Object> health() {
        try {
            log.debug("Checking AI infrastructure health...");
            
            // Check if AI services are enabled
            if (!aiServiceConfig.getEnabled()) {
                return Map.of(
                    "status", "DOWN",
                    "reason", "AI services are disabled",
                    "timestamp", LocalDateTime.now()
                );
            }
            
            // Get comprehensive health information
            AIHealthDto healthInfo = aiHealthService.getHealthStatus();
            
            // Determine overall health status
            String status = determineHealthStatus(healthInfo);
            
            // Build health response
            Map<String, Object> healthResponse = Map.of(
                "status", status,
                "enabled", healthInfo.isEnabled(),
                "configurationValid", healthInfo.isConfigurationValid(),
                "featuresEnabled", healthInfo.getFeaturesEnabled(),
                "totalFeatures", healthInfo.getTotalFeatures(),
                "servicesEnabled", healthInfo.getServicesEnabled(),
                "totalServices", healthInfo.getTotalServices(),
                "timestamp", LocalDateTime.now()
            );
            
            // Add detailed information if available
            if (healthInfo.getPerformanceMetrics() != null) {
                healthResponse.put("performanceMetrics", healthInfo.getPerformanceMetrics());
            }
            
            if (healthInfo.getProviderStatus() != null) {
                healthResponse.put("providerStatus", healthInfo.getProviderStatus());
            }
            
            if (healthInfo.getServiceStatus() != null) {
                healthResponse.put("serviceStatus", healthInfo.getServiceStatus());
            }
            
            if (healthInfo.getSystemStatus() != null) {
                healthResponse.put("systemStatus", healthInfo.getSystemStatus());
            }
            
            if (healthInfo.getErrorMessage() != null) {
                healthResponse.put("errorMessage", healthInfo.getErrorMessage());
            }
            
            log.debug("AI infrastructure health check completed: {}", status);
            return healthResponse;
            
        } catch (Exception e) {
            log.error("Error checking AI infrastructure health", e);
            return Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
    
    /**
     * Determine overall health status
     */
    private String determineHealthStatus(AIHealthDto healthInfo) {
        if (!healthInfo.isEnabled()) {
            return "DOWN";
        }
        
        if (!healthInfo.isConfigurationValid()) {
            return "DOWN";
        }
        
        if (healthInfo.getErrorMessage() != null && !healthInfo.getErrorMessage().trim().isEmpty()) {
            return "DOWN";
        }
        
        if (healthInfo.getFeaturesEnabled() == 0) {
            return "DOWN";
        }
        
        if (healthInfo.getServicesEnabled() == 0) {
            return "DOWN";
        }
        
        return "UP";
    }
    
    /**
     * Get health summary
     */
    public Map<String, Object> getHealthSummary() {
        try {
            AIHealthDto healthInfo = aiHealthService.getHealthStatus();
            
            return Map.of(
                "enabled", healthInfo.isEnabled(),
                "status", healthInfo.getStatus(),
                "featuresEnabled", healthInfo.getFeaturesEnabled(),
                "totalFeatures", healthInfo.getTotalFeatures(),
                "servicesEnabled", healthInfo.getServicesEnabled(),
                "totalServices", healthInfo.getTotalServices(),
                "lastUpdated", healthInfo.getLastUpdated(),
                "configurationValid", healthInfo.isConfigurationValid(),
                "providerConfigurationValid", healthInfo.isProviderConfigurationValid(),
                "serviceConfigurationValid", healthInfo.isServiceConfigurationValid()
            );
        } catch (Exception e) {
            log.error("Error getting health summary", e);
            return Map.of(
                "enabled", false,
                "status", "ERROR",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
    
    /**
     * Check if AI services are healthy
     */
    public boolean isHealthy() {
        try {
            Map<String, Object> health = health();
            return "UP".equals(health.get("status"));
        } catch (Exception e) {
            log.error("Error checking health status", e);
            return false;
        }
    }
    
    /**
     * Get detailed health information
     */
    public Map<String, Object> getDetailedHealth() {
        return health();
    }
}
package com.easyluxury.ai.service;

import com.easyluxury.ai.dto.AIHealthStatusDto;
import com.easyluxury.ai.dto.AIConfigurationStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI Monitoring Service
 * 
 * Service for monitoring AI operations, performance metrics, and health status.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIMonitoringService {
    
    private final AIHealthService aiHealthService;
    private final SimpleAIService simpleAIService;
    
    // Performance metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    
    // Service metrics
    private final Map<String, AtomicLong> serviceRequestCounts = new HashMap<>();
    private final Map<String, AtomicLong> serviceErrorCounts = new HashMap<>();
    
    // ==================== Monitoring Operations ====================
    
    /**
     * Record a successful AI operation
     * 
     * @param operationType the type of operation
     * @param processingTimeMs processing time in milliseconds
     */
    public void recordSuccessfulOperation(String operationType, long processingTimeMs) {
        totalRequests.incrementAndGet();
        successfulRequests.incrementAndGet();
        totalProcessingTime.addAndGet(processingTimeMs);
        
        serviceRequestCounts.computeIfAbsent(operationType, k -> new AtomicLong(0)).incrementAndGet();
        
        log.debug("Recorded successful {} operation with processing time: {}ms", operationType, processingTimeMs);
    }
    
    /**
     * Record a failed AI operation
     * 
     * @param operationType the type of operation
     * @param errorMessage the error message
     */
    public void recordFailedOperation(String operationType, String errorMessage) {
        totalRequests.incrementAndGet();
        failedRequests.incrementAndGet();
        
        serviceErrorCounts.computeIfAbsent(operationType, k -> new AtomicLong(0)).incrementAndGet();
        
        log.warn("Recorded failed {} operation: {}", operationType, errorMessage);
    }
    
    /**
     * Get comprehensive monitoring metrics
     * 
     * @return monitoring metrics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMonitoringMetrics() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        long totalTime = totalProcessingTime.get();
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("timestamp", LocalDateTime.now());
        metrics.put("totalRequests", total);
        metrics.put("successfulRequests", successful);
        metrics.put("failedRequests", failed);
        metrics.put("successRate", total > 0 ? (double) successful / total : 0.0);
        metrics.put("averageProcessingTime", total > 0 ? (double) totalTime / total : 0.0);
        metrics.put("serviceRequestCounts", getServiceRequestCounts());
        metrics.put("serviceErrorCounts", getServiceErrorCounts());
        
        return metrics;
    }
    
    /**
     * Get performance statistics
     * 
     * @return performance statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPerformanceStatistics() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        long totalTime = totalProcessingTime.get();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("timestamp", LocalDateTime.now());
        stats.put("totalRequests", total);
        stats.put("successfulRequests", successful);
        stats.put("failedRequests", failed);
        stats.put("successRate", total > 0 ? (double) successful / total : 0.0);
        stats.put("failureRate", total > 0 ? (double) failed / total : 0.0);
        stats.put("averageProcessingTime", total > 0 ? (double) totalTime / total : 0.0);
        stats.put("totalProcessingTime", totalTime);
        
        return stats;
    }
    
    /**
     * Get service-specific metrics
     * 
     * @return service metrics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getServiceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("timestamp", LocalDateTime.now());
        metrics.put("serviceRequestCounts", getServiceRequestCounts());
        metrics.put("serviceErrorCounts", getServiceErrorCounts());
        metrics.put("serviceErrorRates", getServiceErrorRates());
        
        return metrics;
    }
    
    /**
     * Get health status with monitoring data
     * 
     * @return enhanced health status
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEnhancedHealthStatus() {
        try {
            AIHealthStatusDto healthStatus = aiHealthService.getAIHealthStatus();
            Map<String, Object> monitoringMetrics = getMonitoringMetrics();
            
            Map<String, Object> enhancedStatus = new HashMap<>();
            enhancedStatus.put("healthStatus", healthStatus);
            enhancedStatus.put("monitoringMetrics", monitoringMetrics);
            enhancedStatus.put("timestamp", LocalDateTime.now());
            
            return enhancedStatus;
        } catch (Exception e) {
            log.error("Error getting enhanced health status: {}", e.getMessage(), e);
            return Map.of(
                "healthStatus", Map.of("overallStatus", "ERROR", "message", e.getMessage()),
                "monitoringMetrics", getMonitoringMetrics(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
    
    /**
     * Get configuration status with monitoring data
     * 
     * @return enhanced configuration status
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEnhancedConfigurationStatus() {
        try {
            AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();
            Map<String, Object> monitoringMetrics = getMonitoringMetrics();
            
            Map<String, Object> enhancedStatus = new HashMap<>();
            enhancedStatus.put("configurationStatus", configStatus);
            enhancedStatus.put("monitoringMetrics", monitoringMetrics);
            enhancedStatus.put("timestamp", LocalDateTime.now());
            
            return enhancedStatus;
        } catch (Exception e) {
            log.error("Error getting enhanced configuration status: {}", e.getMessage(), e);
            return Map.of(
                "configurationStatus", Map.of("configurationValid", false, "message", e.getMessage()),
                "monitoringMetrics", getMonitoringMetrics(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
    
    /**
     * Reset monitoring metrics
     */
    public void resetMetrics() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        totalProcessingTime.set(0);
        
        serviceRequestCounts.clear();
        serviceErrorCounts.clear();
        
        log.info("AI monitoring metrics reset");
    }
    
    /**
     * Get service health check
     * 
     * @return service health status
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getServiceHealthCheck() {
        try {
            AIHealthStatusDto healthStatus = aiHealthService.getAIHealthStatus();
            Map<String, Object> monitoringMetrics = getMonitoringMetrics();
            
            boolean isHealthy = "HEALTHY".equals(healthStatus.getOverallStatus()) || 
                              "UP".equals(healthStatus.getOverallStatus());
            
            Map<String, Object> healthCheck = new HashMap<>();
            healthCheck.put("service", "AIMonitoringService");
            healthCheck.put("status", isHealthy ? "HEALTHY" : "UNHEALTHY");
            healthCheck.put("timestamp", LocalDateTime.now());
            healthCheck.put("healthStatus", healthStatus);
            healthCheck.put("monitoringMetrics", monitoringMetrics);
            
            return healthCheck;
        } catch (Exception e) {
            log.error("Error performing service health check: {}", e.getMessage(), e);
            return Map.of(
                "service", "AIMonitoringService",
                "status", "UNHEALTHY",
                "timestamp", LocalDateTime.now(),
                "error", e.getMessage()
            );
        }
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Get service request counts
     * 
     * @return service request counts
     */
    private Map<String, Long> getServiceRequestCounts() {
        Map<String, Long> counts = new HashMap<>();
        serviceRequestCounts.forEach((service, count) -> counts.put(service, count.get()));
        return counts;
    }
    
    /**
     * Get service error counts
     * 
     * @return service error counts
     */
    private Map<String, Long> getServiceErrorCounts() {
        Map<String, Long> counts = new HashMap<>();
        serviceErrorCounts.forEach((service, count) -> counts.put(service, count.get()));
        return counts;
    }
    
    /**
     * Get service error rates
     * 
     * @return service error rates
     */
    private Map<String, Double> getServiceErrorRates() {
        Map<String, Double> rates = new HashMap<>();
        
        serviceRequestCounts.forEach((service, requestCount) -> {
            long requests = requestCount.get();
            long errors = serviceErrorCounts.getOrDefault(service, new AtomicLong(0)).get();
            double errorRate = requests > 0 ? (double) errors / requests : 0.0;
            rates.put(service, errorRate);
        });
        
        return rates;
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Get service statistics
     * 
     * @return service statistics
     */
    public Map<String, Object> getServiceStatistics() {
        return Map.of(
            "serviceName", "AIMonitoringService",
            "version", "1.0.0",
            "timestamp", LocalDateTime.now(),
            "status", "ACTIVE",
            "totalRequests", totalRequests.get(),
            "successfulRequests", successfulRequests.get(),
            "failedRequests", failedRequests.get()
        );
    }
}
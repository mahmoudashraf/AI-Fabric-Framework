package com.ai.infrastructure.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * AI Metrics Service
 * 
 * Comprehensive metrics collection and analysis service for AI infrastructure.
 * Tracks performance, usage, errors, and provides analytics for optimization.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AIMetricsService {
    
    // Request counters
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    
    // Response time tracking
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    private final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
    
    // Cache metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    // Provider metrics
    private final Map<String, AtomicLong> providerRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> providerErrors = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> providerResponseTimes = new ConcurrentHashMap<>();
    
    // Service metrics
    private final Map<String, AtomicLong> serviceRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> serviceErrors = new ConcurrentHashMap<>();
    
    // Error tracking
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    /**
     * Record a successful request
     * 
     * @param serviceName service name
     * @param providerName provider name
     * @param responseTimeMs response time in milliseconds
     */
    public void recordSuccess(String serviceName, String providerName, long responseTimeMs) {
        totalRequests.incrementAndGet();
        successfulRequests.incrementAndGet();
        
        updateResponseTime(responseTimeMs);
        updateServiceMetrics(serviceName, true);
        updateProviderMetrics(providerName, true, responseTimeMs);
        
        log.debug("Recorded successful request: service={}, provider={}, responseTime={}ms", 
                 serviceName, providerName, responseTimeMs);
    }
    
    /**
     * Record a failed request
     * 
     * @param serviceName service name
     * @param providerName provider name
     * @param errorType error type
     * @param responseTimeMs response time in milliseconds
     */
    public void recordFailure(String serviceName, String providerName, String errorType, long responseTimeMs) {
        totalRequests.incrementAndGet();
        failedRequests.incrementAndGet();
        
        updateResponseTime(responseTimeMs);
        updateServiceMetrics(serviceName, false);
        updateProviderMetrics(providerName, false, responseTimeMs);
        updateErrorMetrics(errorType);
        
        log.debug("Recorded failed request: service={}, provider={}, error={}, responseTime={}ms", 
                 serviceName, providerName, errorType, responseTimeMs);
    }
    
    /**
     * Record cache hit
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
        log.debug("Recorded cache hit");
    }
    
    /**
     * Record cache miss
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
        log.debug("Recorded cache miss");
    }
    
    /**
     * Get total requests
     * 
     * @return total requests
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }
    
    /**
     * Get successful requests
     * 
     * @return successful requests
     */
    public long getSuccessfulRequests() {
        return successfulRequests.get();
    }
    
    /**
     * Get failed requests
     * 
     * @return failed requests
     */
    public long getFailedRequests() {
        return failedRequests.get();
    }
    
    /**
     * Get success rate
     * 
     * @return success rate (0.0 to 1.0)
     */
    public double getSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) successfulRequests.get() / total;
    }
    
    /**
     * Get average response time
     * 
     * @return average response time in milliseconds
     */
    public double getAverageResponseTime() {
        return averageResponseTime.get();
    }
    
    /**
     * Get cache hit rate
     * 
     * @return cache hit rate (0.0 to 1.0)
     */
    public double getCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total;
    }
    
    /**
     * Get performance metrics
     * 
     * @return performance metrics map
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        
        // Request metrics
        metrics.put("totalRequests", getTotalRequests());
        metrics.put("successfulRequests", getSuccessfulRequests());
        metrics.put("failedRequests", getFailedRequests());
        metrics.put("successRate", getSuccessRate());
        
        // Response time metrics
        metrics.put("averageResponseTime", getAverageResponseTime());
        metrics.put("minResponseTime", getMinResponseTime());
        metrics.put("maxResponseTime", getMaxResponseTime());
        
        // Cache metrics
        metrics.put("cacheHits", cacheHits.get());
        metrics.put("cacheMisses", cacheMisses.get());
        metrics.put("cacheHitRate", getCacheHitRate());
        
        // Provider metrics
        metrics.put("providerMetrics", getProviderMetrics());
        
        // Service metrics
        metrics.put("serviceMetrics", getServiceMetrics());
        
        // Error metrics
        metrics.put("errorMetrics", getErrorMetrics());
        
        return metrics;
    }
    
    /**
     * Get provider metrics
     * 
     * @return provider metrics map
     */
    public Map<String, Object> getProviderMetrics() {
        Map<String, Object> providerMetrics = new ConcurrentHashMap<>();
        
        for (String provider : providerRequests.keySet()) {
            Map<String, Object> providerData = new ConcurrentHashMap<>();
            
            long requests = providerRequests.get(provider).get();
            long errors = providerErrors.get(provider).get();
            List<Long> responseTimes = providerResponseTimes.get(provider);
            
            providerData.put("requests", requests);
            providerData.put("errors", errors);
            providerData.put("successRate", requests > 0 ? (double) (requests - errors) / requests : 0.0);
            providerData.put("averageResponseTime", calculateAverageResponseTime(responseTimes));
            
            providerMetrics.put(provider, providerData);
        }
        
        return providerMetrics;
    }
    
    /**
     * Get service metrics
     * 
     * @return service metrics map
     */
    public Map<String, Object> getServiceMetrics() {
        Map<String, Object> serviceMetrics = new ConcurrentHashMap<>();
        
        for (String service : serviceRequests.keySet()) {
            Map<String, Object> serviceData = new ConcurrentHashMap<>();
            
            long requests = serviceRequests.get(service).get();
            long errors = serviceErrors.get(service).get();
            
            serviceData.put("requests", requests);
            serviceData.put("errors", errors);
            serviceData.put("successRate", requests > 0 ? (double) (requests - errors) / requests : 0.0);
            
            serviceMetrics.put(service, serviceData);
        }
        
        return serviceMetrics;
    }
    
    /**
     * Get error metrics
     * 
     * @return error metrics map
     */
    public Map<String, Object> getErrorMetrics() {
        Map<String, Object> errorMetrics = new ConcurrentHashMap<>();
        
        for (String errorType : errorCounts.keySet()) {
            errorMetrics.put(errorType, errorCounts.get(errorType).get());
        }
        
        return errorMetrics;
    }
    
    /**
     * Reset all metrics
     */
    public void resetMetrics() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        averageResponseTime.set(0.0);
        responseTimes.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        
        providerRequests.clear();
        providerErrors.clear();
        providerResponseTimes.clear();
        serviceRequests.clear();
        serviceErrors.clear();
        errorCounts.clear();
        
        log.info("AI metrics reset");
    }
    
    /**
     * Update response time
     * 
     * @param responseTimeMs response time in milliseconds
     */
    private void updateResponseTime(long responseTimeMs) {
        responseTimes.add(responseTimeMs);
        
        // Keep only last 1000 response times for memory efficiency
        if (responseTimes.size() > 1000) {
            responseTimes.remove(0);
        }
        
        // Update average
        double sum = responseTimes.stream().mapToLong(Long::longValue).sum();
        averageResponseTime.set(sum / responseTimes.size());
    }
    
    /**
     * Update service metrics
     * 
     * @param serviceName service name
     * @param success success flag
     */
    private void updateServiceMetrics(String serviceName, boolean success) {
        serviceRequests.computeIfAbsent(serviceName, k -> new AtomicLong(0)).incrementAndGet();
        
        if (!success) {
            serviceErrors.computeIfAbsent(serviceName, k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    /**
     * Update provider metrics
     * 
     * @param providerName provider name
     * @param success success flag
     * @param responseTimeMs response time in milliseconds
     */
    private void updateProviderMetrics(String providerName, boolean success, long responseTimeMs) {
        providerRequests.computeIfAbsent(providerName, k -> new AtomicLong(0)).incrementAndGet();
        
        if (!success) {
            providerErrors.computeIfAbsent(providerName, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        providerResponseTimes.computeIfAbsent(providerName, k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(responseTimeMs);
    }
    
    /**
     * Update error metrics
     * 
     * @param errorType error type
     */
    private void updateErrorMetrics(String errorType) {
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Calculate average response time for a list
     * 
     * @param responseTimes list of response times
     * @return average response time
     */
    private double calculateAverageResponseTime(List<Long> responseTimes) {
        if (responseTimes == null || responseTimes.isEmpty()) {
            return 0.0;
        }
        
        return responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    /**
     * Get minimum response time
     * 
     * @return minimum response time
     */
    private long getMinResponseTime() {
        if (responseTimes.isEmpty()) {
            return 0;
        }
        return responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
    }
    
    /**
     * Get maximum response time
     * 
     * @return maximum response time
     */
    private long getMaxResponseTime() {
        if (responseTimes.isEmpty()) {
            return 0;
        }
        return responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
    }
}
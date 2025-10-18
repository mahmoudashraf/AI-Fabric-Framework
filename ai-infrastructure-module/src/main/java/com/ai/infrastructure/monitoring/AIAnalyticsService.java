package com.ai.infrastructure.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI Analytics Service
 * 
 * Advanced analytics service for AI infrastructure monitoring.
 * Provides insights, trends, and recommendations for AI service optimization.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAnalyticsService {
    
    private final AIMetricsService metricsService;
    
    // Historical data storage
    private final Map<String, List<AnalyticsDataPoint>> historicalData = new ConcurrentHashMap<>();
    private final Map<String, List<PerformanceDataPoint>> performanceHistory = new ConcurrentHashMap<>();
    
    /**
     * Get comprehensive analytics report
     * 
     * @return analytics report
     */
    public Map<String, Object> getAnalyticsReport() {
        log.debug("Generating comprehensive AI analytics report");
        
        Map<String, Object> report = new HashMap<>();
        
        // Current metrics
        report.put("currentMetrics", metricsService.getPerformanceMetrics());
        
        // Trends analysis
        report.put("trends", analyzeTrends());
        
        // Performance insights
        report.put("performanceInsights", getPerformanceInsights());
        
        // Usage patterns
        report.put("usagePatterns", analyzeUsagePatterns());
        
        // Recommendations
        report.put("recommendations", generateRecommendations());
        
        // Health score
        report.put("healthScore", calculateHealthScore());
        
        // Generated timestamp
        report.put("generatedAt", LocalDateTime.now());
        
        return report;
    }
    
    /**
     * Record analytics data point
     * 
     * @param serviceName service name
     * @param providerName provider name
     * @param responseTimeMs response time in milliseconds
     * @param success success flag
     * @param cacheHit cache hit flag
     */
    public void recordDataPoint(String serviceName, String providerName, long responseTimeMs, boolean success, boolean cacheHit) {
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Record general analytics
        AnalyticsDataPoint dataPoint = new AnalyticsDataPoint(
            timestamp, serviceName, providerName, responseTimeMs, success, cacheHit
        );
        
        historicalData.computeIfAbsent("general", k -> new ArrayList<>()).add(dataPoint);
        
        // Record performance data
        PerformanceDataPoint perfPoint = new PerformanceDataPoint(
            timestamp, serviceName, responseTimeMs, success
        );
        
        performanceHistory.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(perfPoint);
        
        // Clean up old data (keep last 24 hours)
        cleanupOldData();
        
        log.debug("Recorded analytics data point: service={}, provider={}, responseTime={}ms, success={}", 
                 serviceName, providerName, responseTimeMs, success);
    }
    
    /**
     * Analyze trends
     * 
     * @return trends analysis
     */
    private Map<String, Object> analyzeTrends() {
        Map<String, Object> trends = new HashMap<>();
        
        // Response time trends
        trends.put("responseTimeTrend", analyzeResponseTimeTrend());
        
        // Success rate trends
        trends.put("successRateTrend", analyzeSuccessRateTrend());
        
        // Usage trends
        trends.put("usageTrend", analyzeUsageTrend());
        
        // Cache efficiency trends
        trends.put("cacheEfficiencyTrend", analyzeCacheEfficiencyTrend());
        
        return trends;
    }
    
    /**
     * Get performance insights
     * 
     * @return performance insights
     */
    private Map<String, Object> getPerformanceInsights() {
        Map<String, Object> insights = new HashMap<>();
        
        // Performance bottlenecks
        insights.put("bottlenecks", identifyBottlenecks());
        
        // Optimization opportunities
        insights.put("optimizationOpportunities", identifyOptimizationOpportunities());
        
        // Resource utilization
        insights.put("resourceUtilization", analyzeResourceUtilization());
        
        // Error patterns
        insights.put("errorPatterns", analyzeErrorPatterns());
        
        return insights;
    }
    
    /**
     * Analyze usage patterns
     * 
     * @return usage patterns analysis
     */
    private Map<String, Object> analyzeUsagePatterns() {
        Map<String, Object> patterns = new HashMap<>();
        
        // Peak usage times
        patterns.put("peakUsageTimes", identifyPeakUsageTimes());
        
        // Service popularity
        patterns.put("servicePopularity", analyzeServicePopularity());
        
        // Provider usage distribution
        patterns.put("providerDistribution", analyzeProviderDistribution());
        
        // Request patterns
        patterns.put("requestPatterns", analyzeRequestPatterns());
        
        return patterns;
    }
    
    /**
     * Generate recommendations
     * 
     * @return recommendations list
     */
    private List<String> generateRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        // Performance recommendations
        if (metricsService.getAverageResponseTime() > 2000) {
            recommendations.add("Consider implementing caching to reduce response times");
        }
        
        if (metricsService.getCacheHitRate() < 0.3) {
            recommendations.add("Cache hit rate is low - review cache configuration and TTL settings");
        }
        
        if (metricsService.getSuccessRate() < 0.95) {
            recommendations.add("Success rate is below 95% - investigate error patterns and improve error handling");
        }
        
        // Resource recommendations
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsage = (double) usedMemory / maxMemory;
        
        if (memoryUsage > 0.8) {
            recommendations.add("Memory usage is high - consider increasing heap size or optimizing memory usage");
        }
        
        // Provider recommendations
        Map<String, Object> providerMetrics = metricsService.getProviderMetrics();
        for (String provider : providerMetrics.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> providerData = (Map<String, Object>) providerMetrics.get(provider);
            double successRate = (Double) providerData.get("successRate");
            
            if (successRate < 0.9) {
                recommendations.add("Provider " + provider + " has low success rate - consider fallback configuration");
            }
        }
        
        return recommendations;
    }
    
    /**
     * Calculate health score
     * 
     * @return health score (0-100)
     */
    private double calculateHealthScore() {
        double score = 100.0;
        
        // Deduct points for low success rate
        double successRate = metricsService.getSuccessRate();
        if (successRate < 0.95) {
            score -= (0.95 - successRate) * 50;
        }
        
        // Deduct points for high response time
        double avgResponseTime = metricsService.getAverageResponseTime();
        if (avgResponseTime > 1000) {
            score -= Math.min((avgResponseTime - 1000) / 100, 20);
        }
        
        // Deduct points for low cache hit rate
        double cacheHitRate = metricsService.getCacheHitRate();
        if (cacheHitRate < 0.3) {
            score -= (0.3 - cacheHitRate) * 30;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Analyze response time trend
     * 
     * @return response time trend
     */
    private Map<String, Object> analyzeResponseTimeTrend() {
        Map<String, Object> trend = new HashMap<>();
        
        List<AnalyticsDataPoint> recentData = getRecentData(1, ChronoUnit.HOURS);
        
        if (recentData.size() < 2) {
            trend.put("trend", "insufficient_data");
            return trend;
        }
        
        // Calculate trend direction
        double firstHalf = recentData.subList(0, recentData.size() / 2).stream()
            .mapToLong(AnalyticsDataPoint::getResponseTimeMs)
            .average().orElse(0.0);
        
        double secondHalf = recentData.subList(recentData.size() / 2, recentData.size()).stream()
            .mapToLong(AnalyticsDataPoint::getResponseTimeMs)
            .average().orElse(0.0);
        
        double change = ((secondHalf - firstHalf) / firstHalf) * 100;
        
        if (change > 10) {
            trend.put("trend", "increasing");
        } else if (change < -10) {
            trend.put("trend", "decreasing");
        } else {
            trend.put("trend", "stable");
        }
        
        trend.put("changePercent", change);
        trend.put("currentAverage", secondHalf);
        
        return trend;
    }
    
    /**
     * Analyze success rate trend
     * 
     * @return success rate trend
     */
    private Map<String, Object> analyzeSuccessRateTrend() {
        Map<String, Object> trend = new HashMap<>();
        
        List<AnalyticsDataPoint> recentData = getRecentData(1, ChronoUnit.HOURS);
        
        if (recentData.size() < 2) {
            trend.put("trend", "insufficient_data");
            return trend;
        }
        
        // Calculate success rate for first and second half
        long firstHalfSuccess = recentData.subList(0, recentData.size() / 2).stream()
            .mapToLong(dp -> dp.isSuccess() ? 1 : 0).sum();
        double firstHalfRate = (double) firstHalfSuccess / (recentData.size() / 2);
        
        long secondHalfSuccess = recentData.subList(recentData.size() / 2, recentData.size()).stream()
            .mapToLong(dp -> dp.isSuccess() ? 1 : 0).sum();
        double secondHalfRate = (double) secondHalfSuccess / (recentData.size() - recentData.size() / 2);
        
        double change = ((secondHalfRate - firstHalfRate) / firstHalfRate) * 100;
        
        if (change > 5) {
            trend.put("trend", "improving");
        } else if (change < -5) {
            trend.put("trend", "declining");
        } else {
            trend.put("trend", "stable");
        }
        
        trend.put("changePercent", change);
        trend.put("currentRate", secondHalfRate);
        
        return trend;
    }
    
    /**
     * Analyze usage trend
     * 
     * @return usage trend
     */
    private Map<String, Object> analyzeUsageTrend() {
        Map<String, Object> trend = new HashMap<>();
        
        List<AnalyticsDataPoint> recentData = getRecentData(1, ChronoUnit.HOURS);
        
        if (recentData.size() < 2) {
            trend.put("trend", "insufficient_data");
            return trend;
        }
        
        // Count requests per time period
        Map<String, Long> hourlyCounts = recentData.stream()
            .collect(Collectors.groupingBy(
                dp -> dp.getTimestamp().getHour() + ":00",
                Collectors.counting()
            ));
        
        trend.put("hourlyDistribution", hourlyCounts);
        trend.put("totalRequests", recentData.size());
        
        return trend;
    }
    
    /**
     * Analyze cache efficiency trend
     * 
     * @return cache efficiency trend
     */
    private Map<String, Object> analyzeCacheEfficiencyTrend() {
        Map<String, Object> trend = new HashMap<>();
        
        List<AnalyticsDataPoint> recentData = getRecentData(1, ChronoUnit.HOURS);
        
        if (recentData.isEmpty()) {
            trend.put("trend", "no_data");
            return trend;
        }
        
        long cacheHits = recentData.stream().mapToLong(dp -> dp.isCacheHit() ? 1 : 0).sum();
        double cacheHitRate = (double) cacheHits / recentData.size();
        
        trend.put("cacheHitRate", cacheHitRate);
        trend.put("totalRequests", recentData.size());
        trend.put("cacheHits", cacheHits);
        
        return trend;
    }
    
    /**
     * Identify bottlenecks
     * 
     * @return bottlenecks list
     */
    private List<String> identifyBottlenecks() {
        List<String> bottlenecks = new ArrayList<>();
        
        // Check response times by service
        Map<String, Object> serviceMetrics = metricsService.getServiceMetrics();
        for (String service : serviceMetrics.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> serviceData = (Map<String, Object>) serviceMetrics.get(service);
            long requests = (Long) serviceData.get("requests");
            
            if (requests > 100) { // Only check services with significant usage
                List<PerformanceDataPoint> serviceDataPoints = performanceHistory.get(service);
                if (serviceDataPoints != null && !serviceDataPoints.isEmpty()) {
                    double avgResponseTime = serviceDataPoints.stream()
                        .mapToLong(PerformanceDataPoint::getResponseTimeMs)
                        .average().orElse(0.0);
                    
                    if (avgResponseTime > 2000) {
                        bottlenecks.add("Service " + service + " has high average response time: " + avgResponseTime + "ms");
                    }
                }
            }
        }
        
        return bottlenecks;
    }
    
    /**
     * Identify optimization opportunities
     * 
     * @return optimization opportunities
     */
    private List<String> identifyOptimizationOpportunities() {
        List<String> opportunities = new ArrayList<>();
        
        // Cache optimization
        if (metricsService.getCacheHitRate() < 0.5) {
            opportunities.add("Implement more aggressive caching strategy");
        }
        
        // Provider optimization
        Map<String, Object> providerMetrics = metricsService.getProviderMetrics();
        for (String provider : providerMetrics.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> providerData = (Map<String, Object>) providerMetrics.get(provider);
            double avgResponseTime = (Double) providerData.get("averageResponseTime");
            
            if (avgResponseTime > 1500) {
                opportunities.add("Optimize " + provider + " provider configuration for better performance");
            }
        }
        
        return opportunities;
    }
    
    /**
     * Analyze resource utilization
     * 
     * @return resource utilization analysis
     */
    private Map<String, Object> analyzeResourceUtilization() {
        Map<String, Object> utilization = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        utilization.put("memoryUsagePercent", (double) usedMemory / maxMemory * 100);
        utilization.put("usedMemoryMB", usedMemory / (1024 * 1024));
        utilization.put("maxMemoryMB", maxMemory / (1024 * 1024));
        utilization.put("availableProcessors", runtime.availableProcessors());
        
        return utilization;
    }
    
    /**
     * Analyze error patterns
     * 
     * @return error patterns analysis
     */
    private Map<String, Object> analyzeErrorPatterns() {
        Map<String, Object> patterns = new HashMap<>();
        
        Map<String, Object> errorMetrics = metricsService.getErrorMetrics();
        patterns.put("errorDistribution", errorMetrics);
        
        // Find most common errors
        String mostCommonError = errorMetrics.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");
        
        patterns.put("mostCommonError", mostCommonError);
        
        return patterns;
    }
    
    /**
     * Identify peak usage times
     * 
     * @return peak usage times
     */
    private Map<String, Object> identifyPeakUsageTimes() {
        Map<String, Object> peakTimes = new HashMap<>();
        
        List<AnalyticsDataPoint> recentData = getRecentData(24, ChronoUnit.HOURS);
        
        if (recentData.isEmpty()) {
            peakTimes.put("peakHours", Collections.emptyList());
            return peakTimes;
        }
        
        // Group by hour
        Map<Integer, Long> hourlyCounts = recentData.stream()
            .collect(Collectors.groupingBy(
                dp -> dp.getTimestamp().getHour(),
                Collectors.counting()
            ));
        
        // Find peak hours
        List<Integer> peakHours = hourlyCounts.entrySet().stream()
            .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        peakTimes.put("peakHours", peakHours);
        peakTimes.put("hourlyDistribution", hourlyCounts);
        
        return peakTimes;
    }
    
    /**
     * Analyze service popularity
     * 
     * @return service popularity analysis
     */
    private Map<String, Object> analyzeServicePopularity() {
        Map<String, Object> popularity = new HashMap<>();
        
        Map<String, Object> serviceMetrics = metricsService.getServiceMetrics();
        
        // Sort services by request count
        List<Map.Entry<String, Object>> sortedServices = serviceMetrics.entrySet().stream()
            .sorted((e1, e2) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data1 = (Map<String, Object>) e1.getValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> data2 = (Map<String, Object>) e2.getValue();
                return Long.compare((Long) data2.get("requests"), (Long) data1.get("requests"));
            })
            .collect(Collectors.toList());
        
        popularity.put("mostPopularServices", sortedServices.stream()
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList()));
        
        popularity.put("serviceDistribution", serviceMetrics);
        
        return popularity;
    }
    
    /**
     * Analyze provider distribution
     * 
     * @return provider distribution analysis
     */
    private Map<String, Object> analyzeProviderDistribution() {
        Map<String, Object> distribution = new HashMap<>();
        
        Map<String, Object> providerMetrics = metricsService.getProviderMetrics();
        
        // Calculate total requests across all providers
        long totalRequests = providerMetrics.values().stream()
            .mapToLong(provider -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) provider;
                return (Long) data.get("requests");
            })
            .sum();
        
        // Calculate percentage distribution
        Map<String, Double> percentageDistribution = new HashMap<>();
        for (String provider : providerMetrics.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) providerMetrics.get(provider);
            long requests = (Long) data.get("requests");
            double percentage = totalRequests > 0 ? (double) requests / totalRequests * 100 : 0.0;
            percentageDistribution.put(provider, percentage);
        }
        
        distribution.put("percentageDistribution", percentageDistribution);
        distribution.put("totalRequests", totalRequests);
        
        return distribution;
    }
    
    /**
     * Analyze request patterns
     * 
     * @return request patterns analysis
     */
    private Map<String, Object> analyzeRequestPatterns() {
        Map<String, Object> patterns = new HashMap<>();
        
        List<AnalyticsDataPoint> recentData = getRecentData(24, ChronoUnit.HOURS);
        
        if (recentData.isEmpty()) {
            patterns.put("patterns", Collections.emptyList());
            return patterns;
        }
        
        // Analyze request frequency
        patterns.put("totalRequests", recentData.size());
        patterns.put("averageRequestsPerHour", recentData.size() / 24.0);
        
        // Analyze success patterns
        long successfulRequests = recentData.stream().mapToLong(dp -> dp.isSuccess() ? 1 : 0).sum();
        patterns.put("successRate", (double) successfulRequests / recentData.size());
        
        // Analyze cache patterns
        long cacheHits = recentData.stream().mapToLong(dp -> dp.isCacheHit() ? 1 : 0).sum();
        patterns.put("cacheHitRate", (double) cacheHits / recentData.size());
        
        return patterns;
    }
    
    /**
     * Get recent data
     * 
     * @param amount amount
     * @param unit time unit
     * @return recent data points
     */
    private List<AnalyticsDataPoint> getRecentData(long amount, ChronoUnit unit) {
        LocalDateTime cutoff = LocalDateTime.now().minus(amount, unit);
        
        return historicalData.getOrDefault("general", Collections.emptyList()).stream()
            .filter(dp -> dp.getTimestamp().isAfter(cutoff))
            .collect(Collectors.toList());
    }
    
    /**
     * Clean up old data
     */
    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
        
        for (List<AnalyticsDataPoint> data : historicalData.values()) {
            data.removeIf(dp -> dp.getTimestamp().isBefore(cutoff));
        }
        
        for (List<PerformanceDataPoint> data : performanceHistory.values()) {
            data.removeIf(dp -> dp.getTimestamp().isBefore(cutoff));
        }
    }
    
    /**
     * Analytics data point
     */
    private static class AnalyticsDataPoint {
        private final LocalDateTime timestamp;
        private final String serviceName;
        private final String providerName;
        private final long responseTimeMs;
        private final boolean success;
        private final boolean cacheHit;
        
        public AnalyticsDataPoint(LocalDateTime timestamp, String serviceName, String providerName, 
                                long responseTimeMs, boolean success, boolean cacheHit) {
            this.timestamp = timestamp;
            this.serviceName = serviceName;
            this.providerName = providerName;
            this.responseTimeMs = responseTimeMs;
            this.success = success;
            this.cacheHit = cacheHit;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getServiceName() { return serviceName; }
        public String getProviderName() { return providerName; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public boolean isSuccess() { return success; }
        public boolean isCacheHit() { return cacheHit; }
    }
    
    /**
     * Performance data point
     */
    private static class PerformanceDataPoint {
        private final LocalDateTime timestamp;
        private final String serviceName;
        private final long responseTimeMs;
        private final boolean success;
        
        public PerformanceDataPoint(LocalDateTime timestamp, String serviceName, 
                                  long responseTimeMs, boolean success) {
            this.timestamp = timestamp;
            this.serviceName = serviceName;
            this.responseTimeMs = responseTimeMs;
            this.success = success;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getServiceName() { return serviceName; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public boolean isSuccess() { return success; }
    }
}
package com.ai.infrastructure.audit;

import com.ai.infrastructure.dto.AIAuditLog;
import com.ai.infrastructure.dto.AIAuditRequest;
import com.ai.infrastructure.dto.AIAuditResponse;
import com.ai.infrastructure.core.AICoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * AI Audit Service for comprehensive audit logging and monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAuditService {

    private final AICoreService aiCoreService;
    private final Map<String, AIAuditLog> auditLogs = new ConcurrentHashMap<>();
    private final Map<String, List<AIAuditLog>> userAuditLogs = new ConcurrentHashMap<>();
    private final AtomicLong logCounter = new AtomicLong(0);

    /**
     * Log an audit event
     */
    public AIAuditResponse logAuditEvent(AIAuditRequest request) {
        log.info("Logging audit event for user: {}", request.getUserId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Create audit log entry
            AIAuditLog auditLog = createAuditLog(request);
            
            // Store audit log
            auditLogs.put(auditLog.getLogId(), auditLog);
            userAuditLogs.computeIfAbsent(request.getUserId(), k -> new ArrayList<>()).add(auditLog);
            
            // Perform risk assessment
            String riskLevel = assessRisk(auditLog);
            auditLog.setRiskLevel(riskLevel);
            
            // Generate audit insights
            List<String> insights = generateAuditInsights(auditLog);
            auditLog.setInsights(insights);
            
            // Check for anomalies
            boolean hasAnomalies = detectAnomalies(auditLog);
            auditLog.setHasAnomalies(hasAnomalies);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AIAuditResponse.builder()
                .logId(auditLog.getLogId())
                .userId(request.getUserId())
                .operationType(request.getOperationType())
                .riskLevel(riskLevel)
                .hasAnomalies(hasAnomalies)
                .insights(insights)
                .processingTimeMs(processingTime)
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Error logging audit event", e);
            return AIAuditResponse.builder()
                .userId(request.getUserId())
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Create audit log entry
     */
    private AIAuditLog createAuditLog(AIAuditRequest request) {
        return AIAuditLog.builder()
            .logId("AUDIT_" + logCounter.incrementAndGet())
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .operationType(request.getOperationType())
            .timestamp(LocalDateTime.now())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .sessionId(request.getSessionId())
            .resourceType(request.getResourceType())
            .resourceId(request.getResourceId())
            .action(request.getAction())
            .result(request.getResult())
            .details(request.getDetails())
            .metadata(request.getMetadata())
            .build();
    }

    /**
     * Assess risk level for the audit event
     */
    private String assessRisk(AIAuditLog auditLog) {
        int riskScore = 0;
        
        // Check operation type risk
        switch (auditLog.getOperationType().toUpperCase()) {
            case "DELETE":
            case "UPDATE":
                riskScore += 30;
                break;
            case "CREATE":
                riskScore += 20;
                break;
            case "READ":
                riskScore += 10;
                break;
        }
        
        // Check action risk
        if (auditLog.getAction() != null) {
            String action = auditLog.getAction().toUpperCase();
            if (action.contains("ADMIN") || action.contains("SYSTEM")) {
                riskScore += 25;
            }
            if (action.contains("SENSITIVE") || action.contains("PRIVATE")) {
                riskScore += 20;
            }
        }
        
        // Check result risk
        if ("FAILURE".equals(auditLog.getResult())) {
            riskScore += 15;
        }
        
        // Check time-based risk (off-hours access)
        int hour = auditLog.getTimestamp().getHour();
        if (hour < 6 || hour > 22) {
            riskScore += 10;
        }
        
        // Determine risk level
        if (riskScore >= 60) {
            return "HIGH";
        } else if (riskScore >= 30) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Generate audit insights using AI
     */
    private List<String> generateAuditInsights(AIAuditLog auditLog) {
        try {
            String prompt = String.format(
                "Analyze this audit log entry and provide security insights. " +
                "Look for patterns, anomalies, or security concerns:\n\n" +
                "User: %s\n" +
                "Operation: %s\n" +
                "Action: %s\n" +
                "Result: %s\n" +
                "Time: %s\n" +
                "IP: %s\n\n" +
                "Provide 2-3 key insights about this audit event.",
                auditLog.getUserId(),
                auditLog.getOperationType(),
                auditLog.getAction(),
                auditLog.getResult(),
                auditLog.getTimestamp(),
                auditLog.getIpAddress()
            );
            
            String response = aiCoreService.generateText(prompt);
            return Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(insight -> !insight.isEmpty())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("Failed to generate audit insights", e);
            return Collections.singletonList("Unable to generate insights at this time");
        }
    }

    /**
     * Detect anomalies in the audit log
     */
    private boolean detectAnomalies(AIAuditLog auditLog) {
        // Check for unusual access patterns
        if (isUnusualAccessPattern(auditLog)) {
            return true;
        }
        
        // Check for suspicious IP addresses
        if (isSuspiciousIP(auditLog.getIpAddress())) {
            return true;
        }
        
        // Check for rapid successive operations
        if (isRapidSuccessiveOperations(auditLog)) {
            return true;
        }
        
        // Check for unusual time patterns
        if (isUnusualTimePattern(auditLog)) {
            return true;
        }
        
        return false;
    }

    /**
     * Check for unusual access patterns
     */
    private boolean isUnusualAccessPattern(AIAuditLog auditLog) {
        // Check if user is accessing resources they don't normally access
        List<AIAuditLog> userLogs = userAuditLogs.getOrDefault(auditLog.getUserId(), Collections.emptyList());
        
        if (userLogs.size() < 10) {
            return false; // Not enough history
        }
        
        // Check if this is a new resource type for the user
        Set<String> previousResourceTypes = userLogs.stream()
            .map(AIAuditLog::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        return auditLog.getResourceType() != null && 
               !previousResourceTypes.contains(auditLog.getResourceType());
    }

    /**
     * Check for suspicious IP addresses
     */
    private boolean isSuspiciousIP(String ipAddress) {
        if (ipAddress == null) return false;
        
        // Check for known suspicious IP patterns
        String[] suspiciousPatterns = {
            "10.0.0.", "192.168.", "127.0.0.", "0.0.0.0"
        };
        
        return Arrays.stream(suspiciousPatterns)
            .anyMatch(ipAddress::startsWith);
    }

    /**
     * Check for rapid successive operations
     */
    private boolean isRapidSuccessiveOperations(AIAuditLog auditLog) {
        List<AIAuditLog> userLogs = userAuditLogs.getOrDefault(auditLog.getUserId(), Collections.emptyList());
        
        if (userLogs.size() < 5) {
            return false;
        }
        
        // Check if there are more than 10 operations in the last minute
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        long recentOperations = userLogs.stream()
            .filter(log -> log.getTimestamp().isAfter(oneMinuteAgo))
            .count();
        
        return recentOperations > 10;
    }

    /**
     * Check for unusual time patterns
     */
    private boolean isUnusualTimePattern(AIAuditLog auditLog) {
        int hour = auditLog.getTimestamp().getHour();
        
        // Check for access during unusual hours (2 AM - 5 AM)
        return hour >= 2 && hour <= 5;
    }

    /**
     * Get audit logs for a user
     */
    public List<AIAuditLog> getAuditLogs(String userId) {
        return userAuditLogs.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * Get all audit logs
     */
    public List<AIAuditLog> getAllAuditLogs() {
        return auditLogs.values().stream()
            .sorted((l1, l2) -> l2.getTimestamp().compareTo(l1.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Get audit logs by risk level
     */
    public List<AIAuditLog> getAuditLogsByRiskLevel(String riskLevel) {
        return auditLogs.values().stream()
            .filter(log -> riskLevel.equals(log.getRiskLevel()))
            .sorted((l1, l2) -> l2.getTimestamp().compareTo(l1.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Get audit logs with anomalies
     */
    public List<AIAuditLog> getAuditLogsWithAnomalies() {
        return auditLogs.values().stream()
            .filter(AIAuditLog::isHasAnomalies)
            .sorted((l1, l2) -> l2.getTimestamp().compareTo(l1.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Get audit statistics
     */
    public Map<String, Object> getAuditStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalLogs = auditLogs.size();
        long highRiskLogs = auditLogs.values().stream()
            .mapToLong(log -> "HIGH".equals(log.getRiskLevel()) ? 1 : 0)
            .sum();
        
        long anomalyLogs = auditLogs.values().stream()
            .mapToLong(log -> log.isHasAnomalies() ? 1 : 0)
            .sum();
        
        long uniqueUsers = userAuditLogs.size();
        
        stats.put("totalLogs", totalLogs);
        stats.put("highRiskLogs", highRiskLogs);
        stats.put("anomalyLogs", anomalyLogs);
        stats.put("uniqueUsers", uniqueUsers);
        stats.put("highRiskRate", totalLogs > 0 ? (double) highRiskLogs / totalLogs : 0.0);
        stats.put("anomalyRate", totalLogs > 0 ? (double) anomalyLogs / totalLogs : 0.0);
        
        return stats;
    }

    /**
     * Generate audit report
     */
    public Map<String, Object> generateAuditReport(String userId, String period) {
        List<AIAuditLog> userLogs = getAuditLogs(userId);
        
        Map<String, Object> report = new HashMap<>();
        report.put("userId", userId);
        report.put("period", period);
        report.put("totalEvents", userLogs.size());
        report.put("timestamp", LocalDateTime.now());
        
        // Risk level distribution
        Map<String, Long> riskDistribution = userLogs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getRiskLevel() != null ? log.getRiskLevel() : "UNKNOWN",
                Collectors.counting()
            ));
        report.put("riskDistribution", riskDistribution);
        
        // Operation type distribution
        Map<String, Long> operationDistribution = userLogs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getOperationType() != null ? log.getOperationType() : "UNKNOWN",
                Collectors.counting()
            ));
        report.put("operationDistribution", operationDistribution);
        
        // Anomaly count
        long anomalyCount = userLogs.stream()
            .mapToLong(log -> log.isHasAnomalies() ? 1 : 0)
            .sum();
        report.put("anomalyCount", anomalyCount);
        
        // Recent activity (last 24 hours)
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        long recentActivity = userLogs.stream()
            .mapToLong(log -> log.getTimestamp().isAfter(last24Hours) ? 1 : 0)
            .sum();
        report.put("recentActivity", recentActivity);
        
        return report;
    }

    /**
     * Search audit logs
     */
    public List<AIAuditLog> searchAuditLogs(String query, Map<String, Object> filters) {
        return auditLogs.values().stream()
            .filter(log -> matchesQuery(log, query))
            .filter(log -> matchesFilters(log, filters))
            .sorted((l1, l2) -> l2.getTimestamp().compareTo(l1.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Check if log matches search query
     */
    private boolean matchesQuery(AIAuditLog log, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        
        String lowerQuery = query.toLowerCase();
        return (log.getUserId() != null && log.getUserId().toLowerCase().contains(lowerQuery)) ||
               (log.getOperationType() != null && log.getOperationType().toLowerCase().contains(lowerQuery)) ||
               (log.getAction() != null && log.getAction().toLowerCase().contains(lowerQuery)) ||
               (log.getResourceType() != null && log.getResourceType().toLowerCase().contains(lowerQuery));
    }

    /**
     * Check if log matches filters
     */
    private boolean matchesFilters(AIAuditLog log, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            switch (key) {
                case "riskLevel":
                    if (!value.equals(log.getRiskLevel())) {
                        return false;
                    }
                    break;
                case "operationType":
                    if (!value.equals(log.getOperationType())) {
                        return false;
                    }
                    break;
                case "hasAnomalies":
                    if (!value.equals(log.isHasAnomalies())) {
                        return false;
                    }
                    break;
                case "userId":
                    if (!value.equals(log.getUserId())) {
                        return false;
                    }
                    break;
            }
        }
        
        return true;
    }

    /**
     * Clear audit logs for a user
     */
    public void clearAuditLogs(String userId) {
        userAuditLogs.remove(userId);
        auditLogs.entrySet().removeIf(entry -> userId.equals(entry.getValue().getUserId()));
    }

    /**
     * Clear all audit logs
     */
    public void clearAllAuditLogs() {
        auditLogs.clear();
        userAuditLogs.clear();
    }
}
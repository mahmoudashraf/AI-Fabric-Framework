package com.ai.infrastructure.security;

import com.ai.infrastructure.dto.AISecurityEvent;
import com.ai.infrastructure.dto.AISecurityRequest;
import com.ai.infrastructure.dto.AISecurityResponse;
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
 * AI Security Service for threat detection and access control
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AISecurityService {

    private final AICoreService aiCoreService;
    private final Map<String, List<AISecurityEvent>> securityEvents = new ConcurrentHashMap<>();
    private final Map<String, Integer> accessAttempts = new ConcurrentHashMap<>();
    private final AtomicLong eventCounter = new AtomicLong(0);

    /**
     * Analyze request for security threats
     */
    public AISecurityResponse analyzeRequest(AISecurityRequest request) {
        log.info("Analyzing security request for user: {}", request.getUserId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Check for suspicious patterns
            List<String> threats = detectThreats(request);
            
            // Check access control
            boolean accessAllowed = checkAccessControl(request);
            
            // Check rate limiting
            boolean rateLimitExceeded = checkRateLimit(request);
            
            // Generate security score
            double securityScore = calculateSecurityScore(request, threats, accessAllowed, rateLimitExceeded);
            
            // Determine if request should be blocked
            boolean shouldBlock = shouldBlockRequest(threats, accessAllowed, rateLimitExceeded, securityScore);
            
            // Log security event
            logSecurityEvent(request, threats, securityScore, shouldBlock);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AISecurityResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .threatsDetected(threats)
                .securityScore(securityScore)
                .accessAllowed(accessAllowed && !rateLimitExceeded && !shouldBlock)
                .rateLimitExceeded(rateLimitExceeded)
                .shouldBlock(shouldBlock)
                .recommendations(generateRecommendations(threats, securityScore))
                .processingTimeMs(processingTime)
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Error analyzing security request", e);
            return AISecurityResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .accessAllowed(false)
                .shouldBlock(true)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Detect security threats in the request
     */
    private List<String> detectThreats(AISecurityRequest request) {
        List<String> threats = new ArrayList<>();
        
        // Check for injection attacks
        if (containsInjectionPatterns(request.getContent())) {
            threats.add("INJECTION_ATTACK");
        }
        
        // Check for prompt injection
        if (containsPromptInjection(request.getContent())) {
            threats.add("PROMPT_INJECTION");
        }
        
        // Check for data exfiltration attempts
        if (containsDataExfiltrationPatterns(request.getContent())) {
            threats.add("DATA_EXFILTRATION");
        }
        
        // Check for system manipulation
        if (containsSystemManipulation(request.getContent())) {
            threats.add("SYSTEM_MANIPULATION");
        }
        
        // Check for sensitive data exposure
        if (containsSensitiveData(request.getContent())) {
            threats.add("SENSITIVE_DATA_EXPOSURE");
        }
        
        // Use AI to detect complex threats
        List<String> aiThreats = detectAIThreats(request);
        threats.addAll(aiThreats);
        
        return threats;
    }

    /**
     * Check for injection attack patterns
     */
    private boolean containsInjectionPatterns(String content) {
        if (content == null) return false;
        
        String[] patterns = {
            "'; DROP TABLE", "UNION SELECT", "OR 1=1", "AND 1=1",
            "<script>", "javascript:", "onload=", "onerror=",
            "<?php", "<%", "${", "#{"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Check for prompt injection attempts
     */
    private boolean containsPromptInjection(String content) {
        if (content == null) return false;
        
        String[] patterns = {
            "ignore previous instructions", "forget everything", "new instructions",
            "system prompt", "override", "bypass", "jailbreak",
            "pretend to be", "act as if", "roleplay as"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Check for data exfiltration patterns
     */
    private boolean containsDataExfiltrationPatterns(String content) {
        if (content == null) return false;
        
        String[] patterns = {
            "send data to", "upload to", "export all", "download all",
            "copy database", "backup data", "extract information"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Check for system manipulation attempts
     */
    private boolean containsSystemManipulation(String content) {
        if (content == null) return false;
        
        String[] patterns = {
            "change configuration", "modify settings", "update system",
            "restart service", "shutdown", "kill process", "delete file"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Check for sensitive data exposure
     */
    private boolean containsSensitiveData(String content) {
        if (content == null) return false;
        
        // Check for common sensitive data patterns
        String[] patterns = {
            "password", "secret", "key", "token", "api_key",
            "credit card", "ssn", "social security", "passport"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Use AI to detect complex threats
     */
    private List<String> detectAIThreats(AISecurityRequest request) {
        try {
            String prompt = String.format(
                "Analyze this request for security threats. " +
                "Look for: malicious intent, data exfiltration, system manipulation, " +
                "prompt injection, or other security risks. " +
                "Return only the threat types found, one per line, or 'NONE' if no threats:\n\n" +
                "Request: %s\n" +
                "User: %s\n" +
                "Context: %s",
                request.getContent(),
                request.getUserId(),
                request.getContext()
            );
            
            String response = aiCoreService.generateText(prompt);
            return Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(threat -> !threat.isEmpty() && !threat.equals("NONE"))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("AI threat detection failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * Check access control permissions
     */
    private boolean checkAccessControl(AISecurityRequest request) {
        // Check if user has permission for this operation
        if (request.getRequiredPermissions() == null || request.getRequiredPermissions().isEmpty()) {
            return true;
        }
        
        // Check user roles and permissions
        Set<String> userPermissions = getUserPermissions(request.getUserId());
        return request.getRequiredPermissions().stream()
            .allMatch(userPermissions::contains);
    }

    /**
     * Check rate limiting
     */
    private boolean checkRateLimit(AISecurityRequest request) {
        String key = request.getUserId() + ":" + request.getOperationType();
        int attempts = accessAttempts.getOrDefault(key, 0);
        
        // Simple rate limiting: max 100 requests per minute
        if (attempts > 100) {
            return true;
        }
        
        // Increment counter
        accessAttempts.put(key, attempts + 1);
        
        // Reset counter every minute (simplified)
        if (attempts == 0) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    accessAttempts.remove(key);
                }
            }, 60000);
        }
        
        return false;
    }

    /**
     * Calculate security score (0-100, higher is more secure)
     */
    private double calculateSecurityScore(AISecurityRequest request, List<String> threats, 
                                        boolean accessAllowed, boolean rateLimitExceeded) {
        double score = 100.0;
        
        // Deduct points for threats
        score -= threats.size() * 20.0;
        
        // Deduct points for access violations
        if (!accessAllowed) {
            score -= 30.0;
        }
        
        // Deduct points for rate limiting
        if (rateLimitExceeded) {
            score -= 25.0;
        }
        
        // Deduct points for suspicious content length
        if (request.getContent() != null && request.getContent().length() > 10000) {
            score -= 10.0;
        }
        
        // Deduct points for unusual request patterns
        if (isUnusualRequestPattern(request)) {
            score -= 15.0;
        }
        
        return Math.max(0.0, Math.min(100.0, score));
    }

    /**
     * Check if request follows unusual patterns
     */
    private boolean isUnusualRequestPattern(AISecurityRequest request) {
        // Check for rapid successive requests
        String key = request.getUserId() + ":" + request.getOperationType();
        int attempts = accessAttempts.getOrDefault(key, 0);
        
        return attempts > 50; // More than 50 requests in short time
    }

    /**
     * Determine if request should be blocked
     */
    private boolean shouldBlockRequest(List<String> threats, boolean accessAllowed, 
                                     boolean rateLimitExceeded, double securityScore) {
        // Block if critical threats detected
        if (threats.contains("INJECTION_ATTACK") || threats.contains("SYSTEM_MANIPULATION")) {
            return true;
        }
        
        // Block if access denied
        if (!accessAllowed) {
            return true;
        }
        
        // Block if rate limit exceeded
        if (rateLimitExceeded) {
            return true;
        }
        
        // Block if security score too low
        if (securityScore < 30.0) {
            return true;
        }
        
        return false;
    }

    /**
     * Generate security recommendations
     */
    private List<String> generateRecommendations(List<String> threats, double securityScore) {
        List<String> recommendations = new ArrayList<>();
        
        if (threats.contains("INJECTION_ATTACK")) {
            recommendations.add("Implement input validation and sanitization");
        }
        
        if (threats.contains("PROMPT_INJECTION")) {
            recommendations.add("Add prompt injection detection and filtering");
        }
        
        if (threats.contains("DATA_EXFILTRATION")) {
            recommendations.add("Implement data access controls and monitoring");
        }
        
        if (securityScore < 50.0) {
            recommendations.add("Review and strengthen security policies");
        }
        
        if (securityScore < 30.0) {
            recommendations.add("Consider implementing additional security measures");
        }
        
        return recommendations;
    }

    /**
     * Log security event
     */
    private void logSecurityEvent(AISecurityRequest request, List<String> threats, 
                                double securityScore, boolean blocked) {
        AISecurityEvent event = AISecurityEvent.builder()
            .eventId("SEC_" + eventCounter.incrementAndGet())
            .userId(request.getUserId())
            .requestId(request.getRequestId())
            .eventType(blocked ? "BLOCKED_REQUEST" : "SECURITY_CHECK")
            .threatsDetected(threats)
            .securityScore(securityScore)
            .severity(determineSeverity(threats, securityScore))
            .timestamp(LocalDateTime.now())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .context(request.getContext())
            .build();
        
        securityEvents.computeIfAbsent(request.getUserId(), k -> new ArrayList<>()).add(event);
        
        // Keep only last 1000 events per user
        List<AISecurityEvent> userEvents = securityEvents.get(request.getUserId());
        if (userEvents.size() > 1000) {
            userEvents.remove(0);
        }
        
        log.info("Security event logged: {} for user: {}", event.getEventType(), request.getUserId());
    }

    /**
     * Determine event severity
     */
    private String determineSeverity(List<String> threats, double securityScore) {
        if (threats.contains("INJECTION_ATTACK") || threats.contains("SYSTEM_MANIPULATION")) {
            return "CRITICAL";
        }
        
        if (threats.contains("PROMPT_INJECTION") || threats.contains("DATA_EXFILTRATION")) {
            return "HIGH";
        }
        
        if (securityScore < 30.0) {
            return "HIGH";
        }
        
        if (securityScore < 50.0) {
            return "MEDIUM";
        }
        
        return "LOW";
    }

    /**
     * Get user permissions (simplified implementation)
     */
    private Set<String> getUserPermissions(String userId) {
        // Simplified: return basic permissions for all users
        // In real implementation, this would query a user service
        return Set.of("READ", "WRITE", "SEARCH");
    }

    /**
     * Get security events for a user
     */
    public List<AISecurityEvent> getSecurityEvents(String userId) {
        return securityEvents.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * Get all security events
     */
    public List<AISecurityEvent> getAllSecurityEvents() {
        return securityEvents.values().stream()
            .flatMap(List::stream)
            .sorted((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Clear security events for a user
     */
    public void clearSecurityEvents(String userId) {
        securityEvents.remove(userId);
    }

    /**
     * Get security statistics
     */
    public Map<String, Object> getSecurityStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalEvents = securityEvents.values().stream()
            .mapToLong(List::size)
            .sum();
        
        long blockedEvents = securityEvents.values().stream()
            .flatMap(List::stream)
            .mapToLong(event -> "BLOCKED_REQUEST".equals(event.getEventType()) ? 1 : 0)
            .sum();
        
        stats.put("totalEvents", totalEvents);
        stats.put("blockedEvents", blockedEvents);
        stats.put("blockRate", totalEvents > 0 ? (double) blockedEvents / totalEvents : 0.0);
        stats.put("uniqueUsers", securityEvents.size());
        
        return stats;
    }
}
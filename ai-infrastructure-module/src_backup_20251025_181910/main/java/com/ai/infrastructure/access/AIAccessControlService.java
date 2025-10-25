package com.ai.infrastructure.access;

import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
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
 * AI Access Control Service for intelligent access management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAccessControlService {

    private final AICoreService aiCoreService;
    private final Map<String, Set<String>> userRoles = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();
    private final Map<String, List<AIAccessControlRequest>> accessHistory = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> accessPolicies = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(0);

    /**
     * Check access control for a request
     */
    public AIAccessControlResponse checkAccess(AIAccessControlRequest request) {
        log.info("Checking access control for user: {}", request.getUserId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Analyze access request
            String accessDecision = analyzeAccessRequest(request);
            
            // Check role-based access control
            boolean roleAccess = checkRoleBasedAccess(request);
            
            // Check attribute-based access control
            boolean attributeAccess = checkAttributeBasedAccess(request);
            
            // Check time-based access control
            boolean timeAccess = checkTimeBasedAccess(request);
            
            // Check location-based access control
            boolean locationAccess = checkLocationBasedAccess(request);
            
            // Check resource-based access control
            boolean resourceAccess = checkResourceBasedAccess(request);
            
            // Make final access decision
            boolean accessGranted = makeAccessDecision(accessDecision, roleAccess, attributeAccess, 
                timeAccess, locationAccess, resourceAccess);
            
            // Generate access recommendations
            List<String> recommendations = generateAccessRecommendations(request, accessGranted);
            
            // Log access attempt
            logAccessAttempt(request, accessGranted);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AIAccessControlResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .resourceId(request.getResourceId())
                .operationType(request.getOperationType())
                .accessGranted(accessGranted)
                .accessDecision(accessDecision)
                .roleAccess(roleAccess)
                .attributeAccess(attributeAccess)
                .timeAccess(timeAccess)
                .locationAccess(locationAccess)
                .resourceAccess(resourceAccess)
                .recommendations(recommendations)
                .processingTimeMs(processingTime)
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Error checking access control", e);
            return AIAccessControlResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .accessGranted(false)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Analyze access request using AI
     */
    private String analyzeAccessRequest(AIAccessControlRequest request) {
        try {
            String prompt = String.format(
                "Analyze this access request and determine if it should be granted. " +
                "Consider: user behavior, request context, resource sensitivity, and risk factors.\n\n" +
                "User: %s\n" +
                "Resource: %s\n" +
                "Operation: %s\n" +
                "Context: %s\n" +
                "IP: %s\n" +
                "Time: %s\n\n" +
                "Return: GRANT, DENY, or REVIEW",
                request.getUserId(),
                request.getResourceId(),
                request.getOperationType(),
                request.getContext(),
                request.getIpAddress(),
                request.getTimestamp()
            );
            
            String response = aiCoreService.generateText(prompt);
            String decision = response.trim().toUpperCase();
            
            if (Arrays.asList("GRANT", "DENY", "REVIEW").contains(decision)) {
                return decision;
            } else {
                return "REVIEW"; // Default to review for unclear decisions
            }
            
        } catch (Exception e) {
            log.warn("AI access analysis failed, using rule-based decision", e);
            return makeRuleBasedDecision(request);
        }
    }

    /**
     * Make rule-based access decision
     */
    private String makeRuleBasedDecision(AIAccessControlRequest request) {
        // Check if user has required role
        if (!checkRoleBasedAccess(request)) {
            return "DENY";
        }
        
        // Check if user has required permissions
        if (!checkAttributeBasedAccess(request)) {
            return "DENY";
        }
        
        // Check time-based restrictions
        if (!checkTimeBasedAccess(request)) {
            return "DENY";
        }
        
        // Check location-based restrictions
        if (!checkLocationBasedAccess(request)) {
            return "DENY";
        }
        
        return "GRANT";
    }

    /**
     * Check role-based access control
     */
    private boolean checkRoleBasedAccess(AIAccessControlRequest request) {
        Set<String> userRoles = this.userRoles.getOrDefault(request.getUserId(), Collections.emptySet());
        
        if (userRoles.isEmpty()) {
            return false;
        }
        
        // Check if user has required role
        if (request.getRequiredRoles() != null && !request.getRequiredRoles().isEmpty()) {
            return request.getRequiredRoles().stream()
                .anyMatch(userRoles::contains);
        }
        
        // Check if user has required permissions through roles
        if (request.getRequiredPermissions() != null && !request.getRequiredPermissions().isEmpty()) {
            Set<String> userPermissions = userRoles.stream()
                .flatMap(role -> rolePermissions.getOrDefault(role, Collections.emptySet()).stream())
                .collect(Collectors.toSet());
            
            return request.getRequiredPermissions().stream()
                .allMatch(userPermissions::contains);
        }
        
        return true;
    }

    /**
     * Check attribute-based access control
     */
    private boolean checkAttributeBasedAccess(AIAccessControlRequest request) {
        // Check user attributes
        if (request.getUserAttributes() != null) {
            Map<String, Object> attributes = request.getUserAttributes();
            
            // Check department access
            if (attributes.containsKey("department")) {
                String department = (String) attributes.get("department");
                if (!isDepartmentAllowed(department, request.getResourceId())) {
                    return false;
                }
            }
            
            // Check clearance level
            if (attributes.containsKey("clearanceLevel")) {
                int clearanceLevel = (Integer) attributes.get("clearanceLevel");
                if (!isClearanceLevelSufficient(clearanceLevel, request.getResourceId())) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Check time-based access control
     */
    private boolean checkTimeBasedAccess(AIAccessControlRequest request) {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue();
        
        // Check business hours (9 AM - 5 PM, Monday-Friday)
        if (hour < 9 || hour > 17 || dayOfWeek > 5) {
            // Check if user has after-hours access
            Set<String> userRoles = this.userRoles.getOrDefault(request.getUserId(), Collections.emptySet());
            if (!userRoles.contains("ADMIN") && !userRoles.contains("MANAGER")) {
                return false;
            }
        }
        
        // Check if request is within allowed time window
        if (request.getTimeWindow() != null) {
            LocalDateTime requestTime = request.getTimestamp();
            LocalDateTime windowStart = requestTime.minusHours(request.getTimeWindow());
            LocalDateTime windowEnd = requestTime.plusHours(request.getTimeWindow());
            
            if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Check location-based access control
     */
    private boolean checkLocationBasedAccess(AIAccessControlRequest request) {
        if (request.getIpAddress() == null) {
            return true; // No IP restriction
        }
        
        // Check if IP is in allowed range
        if (request.getAllowedIpRanges() != null && !request.getAllowedIpRanges().isEmpty()) {
            return request.getAllowedIpRanges().stream()
                .anyMatch(range -> isIpInRange(request.getIpAddress(), range));
        }
        
        // Check if IP is in blocked range
        if (request.getBlockedIpRanges() != null && !request.getBlockedIpRanges().isEmpty()) {
            return request.getBlockedIpRanges().stream()
                .noneMatch(range -> isIpInRange(request.getIpAddress(), range));
        }
        
        return true;
    }

    /**
     * Check resource-based access control
     */
    private boolean checkResourceBasedAccess(AIAccessControlRequest request) {
        if (request.getResourceId() == null) {
            return true;
        }
        
        // Check if resource is accessible
        if (request.getAccessibleResources() != null && !request.getAccessibleResources().isEmpty()) {
            return request.getAccessibleResources().contains(request.getResourceId());
        }
        
        // Check if resource is restricted
        if (request.getRestrictedResources() != null && !request.getRestrictedResources().isEmpty()) {
            return !request.getRestrictedResources().contains(request.getResourceId());
        }
        
        return true;
    }

    /**
     * Make final access decision
     */
    private boolean makeAccessDecision(String accessDecision, boolean roleAccess, 
                                     boolean attributeAccess, boolean timeAccess, 
                                     boolean locationAccess, boolean resourceAccess) {
        // If AI recommends deny, deny access
        if ("DENY".equals(accessDecision)) {
            return false;
        }
        
        // If AI recommends review, use rule-based decision
        if ("REVIEW".equals(accessDecision)) {
            return roleAccess && attributeAccess && timeAccess && locationAccess && resourceAccess;
        }
        
        // If AI recommends grant, check all conditions
        if ("GRANT".equals(accessDecision)) {
            return roleAccess && attributeAccess && timeAccess && locationAccess && resourceAccess;
        }
        
        // Default to rule-based decision
        return roleAccess && attributeAccess && timeAccess && locationAccess && resourceAccess;
    }

    /**
     * Generate access recommendations
     */
    private List<String> generateAccessRecommendations(AIAccessControlRequest request, boolean accessGranted) {
        List<String> recommendations = new ArrayList<>();
        
        if (!accessGranted) {
            recommendations.add("Access denied. Review your permissions and try again.");
        }
        
        if (!checkRoleBasedAccess(request)) {
            recommendations.add("You do not have the required role for this operation");
        }
        
        if (!checkAttributeBasedAccess(request)) {
            recommendations.add("Your attributes do not meet the access requirements");
        }
        
        if (!checkTimeBasedAccess(request)) {
            recommendations.add("Access is not allowed at this time");
        }
        
        if (!checkLocationBasedAccess(request)) {
            recommendations.add("Access is not allowed from your current location");
        }
        
        if (!checkResourceBasedAccess(request)) {
            recommendations.add("You do not have access to this resource");
        }
        
        return recommendations;
    }

    /**
     * Log access attempt
     */
    private void logAccessAttempt(AIAccessControlRequest request, boolean accessGranted) {
        accessHistory.computeIfAbsent(request.getUserId(), k -> new ArrayList<>()).add(request);
        
        // Keep only last 1000 access attempts per user
        List<AIAccessControlRequest> userHistory = accessHistory.get(request.getUserId());
        if (userHistory.size() > 1000) {
            userHistory.remove(0);
        }
        
        log.info("Access attempt logged: {} for user: {} to resource: {}", 
            accessGranted ? "GRANTED" : "DENIED", request.getUserId(), request.getResourceId());
    }

    /**
     * Check if department is allowed for resource
     */
    private boolean isDepartmentAllowed(String department, String resourceId) {
        // Simplified: allow all departments for now
        // In real implementation, this would check against resource access policies
        return true;
    }

    /**
     * Check if clearance level is sufficient for resource
     */
    private boolean isClearanceLevelSufficient(int clearanceLevel, String resourceId) {
        // Simplified: require level 3 or higher for sensitive resources
        if (resourceId != null && resourceId.contains("SENSITIVE")) {
            return clearanceLevel >= 3;
        }
        
        return clearanceLevel >= 1;
    }

    /**
     * Check if IP is in range
     */
    private boolean isIpInRange(String ip, String range) {
        // Simplified IP range check
        // In real implementation, this would properly parse CIDR notation
        return ip.startsWith(range.substring(0, range.lastIndexOf('.')));
    }

    /**
     * Assign role to user
     */
    public void assignRole(String userId, String role) {
        userRoles.computeIfAbsent(userId, k -> new HashSet<>()).add(role);
    }

    /**
     * Remove role from user
     */
    public void removeRole(String userId, String role) {
        Set<String> roles = userRoles.get(userId);
        if (roles != null) {
            roles.remove(role);
        }
    }

    /**
     * Assign permission to role
     */
    public void assignPermission(String role, String permission) {
        rolePermissions.computeIfAbsent(role, k -> new HashSet<>()).add(permission);
    }

    /**
     * Remove permission from role
     */
    public void removePermission(String role, String permission) {
        Set<String> permissions = rolePermissions.get(role);
        if (permissions != null) {
            permissions.remove(permission);
        }
    }

    /**
     * Get user roles
     */
    public Set<String> getUserRoles(String userId) {
        return userRoles.getOrDefault(userId, Collections.emptySet());
    }

    /**
     * Get role permissions
     */
    public Set<String> getRolePermissions(String role) {
        return rolePermissions.getOrDefault(role, Collections.emptySet());
    }

    /**
     * Get access history for user
     */
    public List<AIAccessControlRequest> getAccessHistory(String userId) {
        return accessHistory.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * Get access statistics
     */
    public Map<String, Object> getAccessStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalUsers = userRoles.size();
        long totalRoles = rolePermissions.size();
        long totalAccessAttempts = accessHistory.values().stream()
            .mapToLong(List::size)
            .sum();
        
        stats.put("totalUsers", totalUsers);
        stats.put("totalRoles", totalRoles);
        stats.put("totalAccessAttempts", totalAccessAttempts);
        
        // Calculate access success rate
        long successfulAccesses = accessHistory.values().stream()
            .flatMap(List::stream)
            .mapToLong(request -> request.isAccessGranted() ? 1 : 0)
            .sum();
        
        double successRate = totalAccessAttempts > 0 ? (double) successfulAccesses / totalAccessAttempts : 0.0;
        stats.put("successRate", successRate);
        
        return stats;
    }

    /**
     * Update access policy
     */
    public void updateAccessPolicy(String policyName, Map<String, Object> policy) {
        accessPolicies.put(policyName, policy);
    }

    /**
     * Get access policy
     */
    public Map<String, Object> getAccessPolicy(String policyName) {
        return accessPolicies.getOrDefault(policyName, Collections.emptyMap());
    }

    /**
     * Clear access data for user
     */
    public void clearAccessData(String userId) {
        userRoles.remove(userId);
        accessHistory.remove(userId);
    }
}
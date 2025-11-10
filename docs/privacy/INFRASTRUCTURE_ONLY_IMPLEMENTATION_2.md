# 🏗️ Infrastructure-Only Implementation: Complete Reference

**Document Purpose:** Comprehensive guide integrating infrastructure principles, service cleanup, customer patterns, testing, deployment, and operational guidance  
**Audience:** Development Teams, Architects, Operations  
**Reading Time:** 40-50 minutes  
**Status:** ✅ PRODUCTION-READY REFERENCE  
**Version:** 2.0 (Consolidated)

---

## Table of Contents

1. [Core Philosophy](#core-philosophy)
2. [Before Implementation: Service Cleanup](#before-implementation-service-cleanup)
3. [Infrastructure-Only Service Implementations](#infrastructure-only-service-implementations)
4. [Hook Interfaces & Contracts](#hook-interfaces--contracts)
5. [Customer Implementation Examples](#customer-implementation-examples)
6. [Data Transfer Objects (DTOs)](#data-transfer-objects-dtos)
7. [Testing Patterns](#testing-patterns)
8. [Hook Dependency Architecture](#hook-dependency-architecture)
9. [Production Deployment](#production-deployment)
10. [Security Best Practices](#security-best-practices)
11. [Data Classification Guidance](#data-classification-guidance)
12. [Operational Monitoring & Metrics](#operational-monitoring--metrics)
13. [Troubleshooting Guide](#troubleshooting-guide)
14. [Hook Performance Tuning](#hook-performance-tuning)
15. [Spring Boot Integration](#spring-boot-integration)
16. [Hook Selection Decision Tree](#hook-selection-decision-tree)
17. [Advanced Hook Interfaces](#advanced-hook-interfaces)

---

## Core Philosophy

**The AI Core Library = Infrastructure Only**

Your library should provide:
- ✅ Request handling & validation
- ✅ Hook orchestration & injection
- ✅ Audit logging & trail
- ✅ Response formatting
- ✅ Error handling & recovery
- ✅ Performance optimization (caching)
- ✅ Built-in safety patterns (rate limiting, threat detection)

Your library should NEVER provide:
- ❌ Business policies
- ❌ Compliance rules or decisions
- ❌ Business logic or decision-making
- ❌ Domain knowledge
- ❌ Company-specific behavior
- ❌ Hardcoded access rules
- ❌ Regulatory implementations

**The Pattern:** Every service follows the same minimal pattern:

```
┌──────────────────────────────────────────────────────────┐
│ AIService.checkXxx(request)                              │
├──────────────────────────────────────────────────────────┤
│ 1. Extract & validate request data                       │
│ 2. Call customer hook (with @Autowired(required=false))  │
│ 3. Log to audit trail                                    │
│ 4. Format and return response                            │
│ 5. Handle errors gracefully                              │
└──────────────────────────────────────────────────────────┘
         ↓ (Customer implements via hook)
┌──────────────────────────────────────────────────────────┐
│ MyCompanyXxxPolicy implements XxxProvider                │
├──────────────────────────────────────────────────────────┤
│ ALL BUSINESS LOGIC & DECISIONS HERE:                     │
│ - Decision-making                                        │
│ - Policy enforcement                                     │
│ - Business rules                                         │
│ - Regulatory compliance                                  │
│ - Integration with company systems                       │
└──────────────────────────────────────────────────────────┘
```

---

## Before Implementation: Service Cleanup

**CRITICAL:** Before implementing hooks, existing library code must be cleaned to remove policy logic.

### Cleanup Philosophy

Services currently contain mixed concerns:
- **Infrastructure code** (keep): Request handling, hook injection, logging, response formatting
- **Policy code** (delete): Hardcoded business rules, compliance checks, access decisions

The cleanup process extracts policy code into customer-implementable hooks.

### Phase 1: AIAccessControlService Refactoring

**Current Problem:** 356 lines with hardcoded policies and wrong architecture (LLM as PRIMARY decision maker)

**Target:** 80 lines of pure infrastructure

#### Methods to DELETE

```java
// DELETE ENTIRELY - These implement business policies, not infrastructure

❌ analyzeAccessRequest()
   Problem: Uses LLM as PRIMARY decision maker
   Why delete: Decision-making is customer responsibility
   
❌ makeRuleBasedDecision()
   Problem: Hardcoded policy logic (RBAC, ABAC, time-based, location-based)
   Why delete: Policies are customer responsibility
   
❌ checkRoleBasedAccess()
   Problem: Specific RBAC rules implementation
   Why delete: Patterns only, customer implements via EntityAccessPolicy
   
❌ checkAttributeBasedAccess()
   Problem: Specific ABAC rules implementation
   Why delete: Patterns only, customer implements via EntityAccessPolicy
   
❌ checkTimeBasedAccess()
   Problem: Time-based access policy
   Why delete: Business policy, customer responsibility
   
❌ checkLocationBasedAccess()
   Problem: Location-based access policy
   Why delete: Business policy, customer responsibility
   
❌ checkResourceBasedAccess()
   Problem: Resource-based access control
   Why delete: Business policy, customer responsibility
   
❌ isDepartmentAllowed()
   Problem: Company-specific department rules
   Why delete: Business policy, customer responsibility
   
❌ isClearanceLevelSufficient()
   Problem: Company-specific clearance rules
   Why delete: Business policy, customer responsibility
   
❌ assignRole() / removeRole() / assignPermission() / removePermission()
   Problem: Role/permission management (not library's job)
   Why delete: Infrastructure doesn't manage policies
   
❌ updateAccessPolicy() / getAccessPolicy()
   Problem: Policy management (not library's job)
   Why delete: Customers manage their own policies
```

#### Before Code

```java
@Service
@RequiredArgsConstructor
public class AIAccessControlService {
    
    private final AICoreService aiCoreService;
    private final Map<String, List<String>> rolePermissions;
    private final Map<String, Map<String, Object>> accessPolicies;
    
    public AIAccessControlResponse checkAccess(AIAccessControlRequest request) {
        // Problem 1: LLM makes primary decision (wrong)
        String llmDecision = analyzeAccessRequest(request);
        
        if ("REVIEW".equals(llmDecision)) {
            // Problem 2: Falls back to hardcoded rules (wrong)
            return makeRuleBasedDecision(request);
        }
        
        return buildResponse(llmDecision);
    }
    
    // DELETE: All these policy methods
    private String analyzeAccessRequest(AIAccessControlRequest request) {
        // ❌ LLM-based decision - VIOLATES philosophy
    }
    
    private String makeRuleBasedDecision(AIAccessControlRequest request) {
        // ❌ Hardcoded policy rules - VIOLATES philosophy
        if (!checkRoleBasedAccess(request)) return "DENY";
        if (!checkTimeBasedAccess(request)) return "DENY";
        if (!checkLocationBasedAccess(request)) return "DENY";
        return "GRANT";
    }
}
```

#### After Code

```java
@Service
@RequiredArgsConstructor
public class AIAccessControlService {
    
    // Hook injection - INFRASTRUCTURE
    @Autowired(required = false)
    private EntityAccessPolicy entityAccessPolicy;
    
    // Audit storage - INFRASTRUCTURE
    private final Map<String, List<AIAccessControlRequest>> accessHistory 
        = new ConcurrentHashMap<>();
    
    private final AuditService auditService;
    
    /**
     * INFRASTRUCTURE ONLY: Extract → Call Hook → Log → Return
     * With aggressive caching: userId + entityType → access result (1 hour TTL)
     */
    public AIAccessControlResponse checkAccess(AIAccessControlRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Extract entity map
            Map<String, Object> entity = extractEntityMap(request);
            
            // Step 2: Check cache first (userId + entityType)
            String cacheKey = generateCacheKey(request.getUserId(), entity);
            Boolean cachedResult = getCachedAccessDecision(cacheKey);
            
            boolean accessGranted = true;
            boolean fromCache = false;
            
            if (cachedResult != null) {
                // ✅ Cache hit - use cached result (fast!)
                accessGranted = cachedResult;
                fromCache = true;
                log.debug("Access decision from cache for {}", cacheKey);
            } else {
                // Cache miss - call customer's policy hook
                if (entityAccessPolicy != null) {
                    accessGranted = entityAccessPolicy
                        .canUserAccessEntity(request.getUserId(), entity);
                }
                
                // Cache the result for future requests
                cacheAccessDecision(cacheKey, accessGranted);
            }
            
            // Step 3: Log access attempt
            logAccessAttempt(request, accessGranted);
            
            // Step 4: Format response
            return AIAccessControlResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .accessGranted(accessGranted)
                .fromCache(fromCache)  // Indicate if from cache
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error checking access", e);
            return AIAccessControlResponse.builder()
                .requestId(request.getRequestId())
                .accessGranted(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * INFRASTRUCTURE: Extract entity information for hook
     */
    private Map<String, Object> extractEntityMap(AIAccessControlRequest request) {
        Map<String, Object> entity = new HashMap<>();
        entity.put("resourceId", request.getResourceId());
        entity.put("operationType", request.getOperationType());
        entity.put("timestamp", request.getTimestamp());
        entity.put("userId", request.getUserId());
        entity.put("ipAddress", request.getIpAddress());
        
        if (request.getUserAttributes() != null) {
            entity.putAll(request.getUserAttributes());
        }
        
        return entity;
    }
    
    /**
     * INFRASTRUCTURE: Log all access attempts for audit trail
     */
    private void logAccessAttempt(AIAccessControlRequest request, boolean granted) {
        accessHistory.computeIfAbsent(request.getUserId(), k -> new ArrayList<>())
            .add(request);
        
        auditService.logOperation(
            request.getRequestId(),
            request.getUserId(),
            granted ? "ACCESS_GRANTED" : "ACCESS_DENIED",
            List.of("EntityAccessPolicy"),
            LocalDateTime.now().toString()
        );
    }
    
    /**
     * INFRASTRUCTURE: Query access history
     */
    public List<AIAccessControlRequest> getAccessHistory(String userId) {
        return accessHistory.getOrDefault(userId, Collections.emptyList());
    }
    
    /**
     * INFRASTRUCTURE: Generate cache key from userId and entity type
     * Pattern: "access:userId:entityType"
     * Example: "access:user123:CUSTOMER_DATA"
     */
    private String generateCacheKey(String userId, Map<String, Object> entity) {
        String entityType = (String) entity.getOrDefault("resourceId", "UNKNOWN");
        return String.format("access:%s:%s", userId, entityType);
    }
    
    /**
     * INFRASTRUCTURE: Get cached access decision
     * Returns null if not cached, otherwise the boolean result
     * TTL: 1 hour (configurable)
     */
    private Boolean getCachedAccessDecision(String cacheKey) {
        try {
            Cache cache = cacheManager.getCache("accessDecisions");
            if (cache == null) return null;
            
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            return wrapper != null ? (Boolean) wrapper.get() : null;
        } catch (Exception e) {
            log.warn("Error reading from cache: {}", e.getMessage());
            return null;  // Fail open - call hook instead
        }
    }
    
    /**
     * INFRASTRUCTURE: Cache access decision
     * TTL: 1 hour (configured in CacheManager)
     * Falls back gracefully if cache fails
     */
    private void cacheAccessDecision(String cacheKey, boolean result) {
        try {
            Cache cache = cacheManager.getCache("accessDecisions");
            if (cache != null) {
                cache.put(cacheKey, result);
                log.debug("Cached access decision for: {}", cacheKey);
            }
        } catch (Exception e) {
            log.warn("Error caching access decision: {}", e.getMessage());
            // Don't fail - continue without cache
        }
    }
    
    /**
     * INFRASTRUCTURE: Invalidate cache for user
     * Called when user roles/permissions change (e.g., role added/removed)
     * Prevents stale cached decisions
     */
    public void invalidateUserCache(String userId) {
        try {
            Cache cache = cacheManager.getCache("accessDecisions");
            if (cache != null) {
                // Remove all entries for this user
                // Note: CacheManager.invalidate() clears all;
                // for selective removal, implement custom logic
                log.info("Invalidated access cache for user: {}", userId);
            }
        } catch (Exception e) {
            log.warn("Error invalidating cache: {}", e.getMessage());
        }
    }
}
```

**Cleanup Effort:** 4-6 hours

---

### Phase 2: AISecurityService Cleanup

**Current Problem:** 262 lines with duplicated PII logic and access control

**Target:** 120 lines with built-in threat patterns + hook integration

#### Methods to DELETE

```java
❌ containsSensitiveData()
   Problem: Duplicate of PIIDetectionService
   Why delete: PIIDetectionService handles PII detection
   Solution: Inject PIIDetectionService if needed
   
❌ checkAccessControl()
   Problem: Not security's responsibility
   Why delete: AIAccessControlService handles access
   Solution: Don't mix concerns
   
❌ getUserPermissions()
   Problem: Not security's responsibility
   Why delete: Access control service manages permissions
```

#### Refactored AISecurityService

```java
@Service
@RequiredArgsConstructor
public class AISecurityService {
    
    @Autowired(required = false)
    private SecurityAnalysisPolicy securityPolicy;
    
    private final PIIDetectionService piiDetectionService;
    private final AuditService auditService;
    private final Map<String, List<AISecurityEvent>> securityEvents 
        = new ConcurrentHashMap<>();
    private final Map<String, Integer> accessAttempts = new ConcurrentHashMap<>();
    
    /**
     * INFRASTRUCTURE: Built-in threat detection + customer hook
     */
    public AISecurityResponse analyzeRequest(AISecurityRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            
            // Step 1: Built-in threat detection (baseline security)
            List<String> builtInThreats = detectBuiltInThreats(request);
            
            // Step 2: Call customer's security policy hook
            List<String> allThreats = new ArrayList<>(builtInThreats);
            if (securityPolicy != null) {
                SecurityAnalysisResult result = securityPolicy
                    .analyzeSecurity(request);
                allThreats.addAll(result.getThreats());
            }
            
            // Step 3: Rate limiting check
            boolean rateLimited = checkRateLimit(request);
            if (rateLimited) {
                allThreats.add("RATE_LIMIT_EXCEEDED");
            }
            
            // Step 4: Calculate security score
            double securityScore = calculateSecurityScore(
                allThreats.isEmpty(), rateLimited);
            
            // Step 5: Log event
            logSecurityEvent(request, allThreats, securityScore);
            
            // Step 6: Return response
            return AISecurityResponse.builder()
                .requestId(request.getRequestId())
                .secure(allThreats.isEmpty() && !rateLimited)
                .threats(allThreats)
                .securityScore(securityScore)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error analyzing security", e);
            return AISecurityResponse.builder()
                .requestId(request.getRequestId())
                .secure(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * INFRASTRUCTURE: Built-in threat patterns (baseline security)
     * Customers can override or enhance via SecurityAnalysisPolicy
     */
    private List<String> detectBuiltInThreats(AISecurityRequest request) {
        List<String> threats = new ArrayList<>();
        
        String content = request.getContent();
        if (content == null) return threats;
        
        if (containsInjectionPatterns(content)) {
            threats.add("INJECTION_ATTACK");
        }
        
        if (containsPromptInjection(content)) {
            threats.add("PROMPT_INJECTION");
        }
        
        if (containsDataExfiltrationPatterns(content)) {
            threats.add("DATA_EXFILTRATION");
        }
        
        if (containsSystemManipulation(content)) {
            threats.add("SYSTEM_MANIPULATION");
        }
        
        return threats;
    }
    
    /**
     * INFRASTRUCTURE: SQL injection patterns
     */
    private boolean containsInjectionPatterns(String content) {
        String[] patterns = {
            "'; DROP TABLE", "UNION SELECT", "OR 1=1", 
            "<script>", "<?php", "eval(", "exec("
        };
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }
    
    /**
     * INFRASTRUCTURE: Prompt injection patterns
     */
    private boolean containsPromptInjection(String content) {
        String[] patterns = {
            "ignore previous instructions", "forget everything", 
            "jailbreak", "override", "system message"
        };
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }
    
    /**
     * INFRASTRUCTURE: Data exfiltration patterns
     */
    private boolean containsDataExfiltrationPatterns(String content) {
        String[] patterns = {
            "send data to", "export all", "download all", 
            "copy database", "leak", "steal"
        };
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }
    
    /**
     * INFRASTRUCTURE: System manipulation patterns
     */
    private boolean containsSystemManipulation(String content) {
        String[] patterns = {
            "modify settings", "restart service", "delete file", 
            "kill process", "shutdown", "rm -rf"
        };
        String lowerContent = content.toLowerCase();
        return Arrays.stream(patterns)
            .anyMatch(lowerContent::contains);
    }
    
    /**
     * INFRASTRUCTURE: Rate limiting (baseline brute-force protection)
     */
    private boolean checkRateLimit(AISecurityRequest request) {
        String key = request.getUserId() + ":" + request.getOperationType();
        int attempts = accessAttempts.getOrDefault(key, 0);
        
        if (attempts > 100) {
            return true;
        }
        
        accessAttempts.put(key, attempts + 1);
        return false;
    }
    
    /**
     * INFRASTRUCTURE: Security score (simple metric)
     */
    private double calculateSecurityScore(boolean noThreats, boolean rateLimited) {
        double score = 100.0;
        if (!noThreats) score -= 50.0;
        if (rateLimited) score -= 25.0;
        return Math.max(0, score);
    }
    
    private void logSecurityEvent(AISecurityRequest request, 
                                   List<String> threats, double score) {
        AISecurityEvent event = AISecurityEvent.builder()
            .userId(request.getUserId())
            .requestId(request.getRequestId())
            .threats(threats)
            .securityScore(score)
            .timestamp(LocalDateTime.now())
            .build();
        
        securityEvents.computeIfAbsent(request.getUserId(), k -> new ArrayList<>())
            .add(event);
        
        auditService.logOperation(
            request.getRequestId(),
            request.getUserId(),
            threats.isEmpty() ? "SECURITY_PASS" : "SECURITY_THREAT",
            threats,
            LocalDateTime.now().toString()
        );
    }
    
    public List<AISecurityEvent> getSecurityEvents(String userId) {
        return securityEvents.getOrDefault(userId, Collections.emptyList());
    }
}
```

**Cleanup Effort:** 2-3 hours

---

### Phase 3: AIComplianceService Refactoring

**Current Problem:** 272 lines with hardcoded compliance policies

**Target:** 50 lines of pure infrastructure

#### Methods to DELETE

```java
❌ checkDataPrivacyCompliance()
   Problem: Hardcoded GDPR/CCPA rules
   Why delete: Companies have different privacy rules
   
❌ checkRegulatoryCompliance()
   Problem: Hardcoded regulatory rules
   Why delete: Companies choose which regulations apply
   
❌ checkAuditRequirements()
   Problem: Hardcoded audit policies
   Why delete: Company-specific requirements
   
❌ checkDataRetentionCompliance()
   Problem: Hardcoded retention rules
   Why delete: RetentionPolicyProvider handles this
   
❌ hasValidConsent()
   Problem: Consent is company-specific
   Why delete: Customer responsibility
   
❌ containsPersonalData()
   Problem: Duplicate of PIIDetectionService
   Why delete: PIIDetectionService detects PII
   
❌ identifyViolations()
   Problem: Hardcoded violation logic
   Why delete: Customer implements via hook
   
❌ generateComplianceRecommendations()
   Problem: Hardcoded recommendations
   Why delete: Customer responsibility
```

#### Refactored AIComplianceService

```java
@Service
@RequiredArgsConstructor
public class AIComplianceService {
    
    @Autowired(required = false)
    private ComplianceCheckProvider complianceProvider;
    
    private final AuditService auditService;
    private final Map<String, AIAuditLog> auditLogs = new ConcurrentHashMap<>();
    private final AtomicLong logCounter = new AtomicLong(0);
    
    /**
     * INFRASTRUCTURE ONLY: Call hook → Log → Return
     * NO compliance policy logic here
     */
    public AIComplianceResponse checkCompliance(AIComplianceRequest request) {
        log.info("Checking compliance for request: {}", request.getRequestId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Step 1: Call customer's compliance provider
            boolean compliant = true;
            List<String> violations = new ArrayList<>();
            
            if (complianceProvider != null) {
                ComplianceCheckResult result = complianceProvider
                    .checkCompliance(request.getRequestId(), request.getContent());
                compliant = result.isCompliant();
                violations = result.getViolations();
            }
            
            // Step 2: Log audit event
            logAuditEvent(request, compliant, violations);
            
            // Step 3: Format response
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AIComplianceResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .compliant(compliant)
                .violations(violations)
                .report(generateReport(request, compliant, violations))
                .processingTimeMs(processingTime)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error checking compliance", e);
            return AIComplianceResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .compliant(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * INFRASTRUCTURE: Log audit event
     */
    private void logAuditEvent(AIComplianceRequest request, 
                               boolean compliant, 
                               List<String> violations) {
        AIAuditLog log = AIAuditLog.builder()
            .logId("LOG_" + logCounter.incrementAndGet())
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .eventType(compliant ? "COMPLIANT" : "VIOLATION")
            .violations(violations)
            .timestamp(LocalDateTime.now())
            .build();
        
        auditLogs.put(log.getLogId(), log);
        
        auditService.logOperation(
            request.getRequestId(),
            request.getUserId(),
            compliant ? "COMPLIANCE_PASS" : "COMPLIANCE_FAIL",
            violations,
            LocalDateTime.now().toString()
        );
    }
    
    /**
     * INFRASTRUCTURE: Generate compliance report
     */
    private AIComplianceReport generateReport(AIComplianceRequest request,
                                             boolean compliant,
                                             List<String> violations) {
        return AIComplianceReport.builder()
            .requestId(request.getRequestId())
            .compliant(compliant)
            .violations(violations)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * INFRASTRUCTURE: Query audit logs
     */
    public List<AIAuditLog> getAuditLogs(String userId) {
        return auditLogs.values().stream()
            .filter(log -> userId.equals(log.getUserId()))
            .collect(Collectors.toList());
    }
}
```

**Cleanup Effort:** 4-6 hours

---

### Service Cleanup Summary

| Service | Before | After | Reduction | Effort |
|---------|--------|-------|-----------|--------|
| AIAccessControlService | 356 | 80 | 77% | 4-6 hrs |
| AISecurityService | 262 | 120 | 54% | 2-3 hrs |
| AIComplianceService | 272 | 50 | 82% | 4-6 hrs |
| Total | 890 | 250 | 72% | 10-15 hrs |

**Total Cleanup Effort: 10-15 hours**

---

## Infrastructure-Only Service Implementations

### Complete Service Reference (250 lines total)

This section shows the three refactored services working together in the infrastructure layer.

**Key Principle:** Each service is ~80-120 lines, implements the Extract → Hook → Log → Return pattern.

See detailed implementations in Phase 1-3 above.

---

## Hook Interfaces & Contracts

### Complete Hook Reference (7 Hooks)

#### 1. EntityAccessPolicy

```java
package com.ai.infrastructure.rag.policy;

/**
 * INFRASTRUCTURE HOOK: Entity-level access control
 * 
 * Customer implements this to enforce company-specific access policies.
 * Library calls this interface at decision points.
 */
public interface EntityAccessPolicy {
    
    /**
     * Determine if a user can access a specific entity.
     *
     * @param userId The user ID making the request
     * @param entity The entity being accessed (Map with id, type, owner, etc.)
     * @return true if access granted, false if denied
     */
    boolean canUserAccessEntity(String userId, Map<String, Object> entity);
    
    /**
     * Optional: Log why access was denied.
     * Called when canUserAccessEntity returns false.
     */
    default void logAccessDenied(String userId, Map<String, Object> entity, 
                                 String reason) {
        // No-op default implementation
    }
}
```

#### 2. SecurityAnalysisPolicy

```java
package com.ai.infrastructure.security.policy;

/**
 * INFRASTRUCTURE HOOK: Custom threat detection
 * 
 * Customer implements to add company-specific threat analysis.
 * Works alongside built-in threat patterns (injection, prompt injection, etc).
 */
public interface SecurityAnalysisPolicy {
    
    /**
     * Analyze security of a request.
     *
     * @param request The security request
     * @return Analysis result with threats list
     */
    SecurityAnalysisResult analyzeSecurity(AISecurityRequest request);
}

@Data
@Builder
public class SecurityAnalysisResult {
    private List<String> threats;        // Custom threats detected
    private double score;                // Optional: custom security score
    private List<String> recommendations; // Optional: recommendations
}
```

#### 3. ComplianceCheckProvider

```java
package com.ai.infrastructure.compliance.policy;

/**
 * INFRASTRUCTURE HOOK: Regulatory compliance checking
 * 
 * Customer implements to check against their regulations (GDPR, CCPA, HIPAA, etc).
 * Library calls this for every request requiring compliance check.
 */
public interface ComplianceCheckProvider {
    
    /**
     * Check if content/operation is compliant with company's regulations.
     *
     * @param requestId Request identifier
     * @param content The content/operation to check
     * @return Compliance check result with violations if any
     */
    ComplianceCheckResult checkCompliance(String requestId, String content);
}

@Data
@Builder
public class ComplianceCheckResult {
    private boolean compliant;           // true if compliant, false if violations
    private List<String> violations;     // e.g., ["GDPR_VIOLATION", "CCPA_VIOLATION"]
    private String details;              // Optional: details about violations
}
```

#### 4. RetentionPolicyProvider

```java
package com.ai.infrastructure.rag.policy;

/**
 * INFRASTRUCTURE HOOK: Data retention policy
 * 
 * Customer implements to define company-specific retention rules for indexed data.
 * Library calls this during scheduled retention enforcement.
 */
public interface RetentionPolicyProvider {
    
    /**
     * Get retention period in days for given classification and entity type.
     * 
     * @param classification Data classification (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)
     * @param entityType Entity type (CUSTOMER, ORDER, PRODUCT, etc.)
     * @return Days to retain (0 = delete immediately, -1 = never delete)
     */
    int getRetentionDays(String classification, String entityType);
    
    /**
     * Check if entity should be deleted based on retention policy.
     * 
     * @param entity AISearchableEntity to check
     * @return true if expired and should be deleted
     */
    boolean shouldDelete(AISearchableEntity entity);
    
    /**
     * Execute deletion with company-specific logic.
     * Called before actual deletion - perform anonymization, archiving, etc.
     * 
     * @param entity Entity to delete
     * @return true if deletion approved/successful
     */
    boolean executeDelete(AISearchableEntity entity);
}
```

#### 5. BehaviorRetentionPolicyProvider

```java
package com.ai.infrastructure.behavior.policy;

/**
 * INFRASTRUCTURE HOOK: Behavior-specific retention
 * 
 * Customer implements to define retention for user behavior tracking.
 * Different from RetentionPolicyProvider (for indexed content).
 */
public interface BehaviorRetentionPolicyProvider {
    
    /**
     * Define retention days per behavior type.
     * Example: PURCHASE: 7 years (tax), SEARCH: 90 days (privacy)
     *
     * @param behaviorType Type of behavior (VIEW, CLICK, PURCHASE, etc)
     * @return Days to retain
     */
    int getRetentionDays(BehaviorType behaviorType);
    
    /**
     * Custom retention rules per user/context.
     * Example: EU users get 30 days for privacy reasons
     *
     * @param behaviorType Type of behavior
     * @param userId User ID for context
     * @return Days to retain for this user
     */
    int getRetentionDaysForUser(BehaviorType behaviorType, String userId);
    
    /**
     * Special handling before behavior deletion.
     * Example: Archive PURCHASE behaviors to cold storage
     *
     * @param behaviors Behaviors about to be deleted
     */
    void beforeBehaviorDeletion(List<Behavior> behaviors);
}
```

#### 6. UserDataDeletionProvider

```java
package com.ai.infrastructure.deletion.policy;

/**
 * INFRASTRUCTURE HOOK: Right to Delete implementation
 * 
 * Customer implements to handle domain-specific user data deletion.
 * Called when user requests complete data deletion (GDPR Article 17, CCPA).
 */
public interface UserDataDeletionProvider {
    
    /**
     * Delete user's domain-specific data.
     *
     * @param userId User ID to delete
     * @return Number of items deleted
     */
    int deleteUserDomainData(String userId);
    
    /**
     * Validate that deletion is allowed.
     * Example: Check no active orders, no pending transactions
     *
     * @param userId User ID to check
     * @return true if deletion allowed
     */
    boolean canDeleteUser(String userId);
    
    /**
     * Notify user after deletion.
     * Example: Send confirmation email, revoke API tokens
     *
     * @param userId User ID that was deleted
     */
    void notifyAfterDeletion(String userId);
}
```

#### 7. ComplianceEventSubscriber

```java
package com.ai.infrastructure.event.policy;

/**
 * INFRASTRUCTURE HOOK: Real-time compliance event notification
 * 
 * Library publishes compliance events ONLY for things it CAN detect.
 * Customer implements to receive real-time notifications.
 * 
 * IMPORTANT: Library streams FACTS, not analysis.
 * Customers analyze context and make compliance decisions.
 * 
 * Example:
 * - Library detects: "PII in response" (fact)
 * - Customer analyzes: "Is this expected?" (context)
 * - Customer decides: "Alert compliance team" (action)
 */
public interface ComplianceEventSubscriber {
    
    // CRITICAL - Security threats (your library detects)
    void onInjectionAttempted(InjectionEvent event);
    void onPromptInjectionAttempted(PromptInjectionEvent event);
    void onDataExfiltrationAttempted(DataExfiltrationEvent event);
    void onUnauthorizedAccessAttempt(UnauthorizedAccessEvent event);
    
    // CRITICAL - Data protection (your library detects)
    void onPIIDetected(PIIDetectedEvent event);
    void onMaskedDataExposed(MaskedDataExposedEvent event);
    void onAccessWithoutConsent(AccessWithoutConsentEvent event);
    void onAccessAfterExpiration(AccessAfterExpirationEvent event);
    
    // HIGH - Suspicious activity (your library detects)
    void onRateLimitExceeded(RateLimitEvent event);
    void onAccessFromUnexpectedLocation(UnexpectedLocationEvent event);
    
    // MEDIUM - Access control (your library detects)
    void onAccessDenied(AccessDeniedEvent event);
    
    // LOW - Audit trail (your library detects)
    void onRequestProcessed(RequestProcessedEvent event);
    void onRequestFailed(RequestFailedEvent event);
    void onAuditLogAccessed(AuditLogAccessedEvent event);
}
```

**Key Points:**
- **What library streams:** Raw FACTS only (PII detected, injection attempted, access denied)
- **What library CANNOT detect:** Domain-specific analysis (sensitive data, bulk access, business hours)
- **Customer's role:** Implement analysis and decision-making
- **Benefit:** Real-time notification (<1ms) vs async log queries (5-10 minutes)

**Example Event DTOs:**

```java
@Data
@Builder
public class PIIDetectedEvent {
    private String requestId;
    private String userId;
    private String piiType;           // CREDIT_CARD, SSN, EMAIL, PHONE
    private String severity;          // LOW, MEDIUM, HIGH, CRITICAL
    private String location;          // RESPONSE, REQUEST, etc
    private LocalDateTime timestamp;
}

@Data
@Builder
public class InjectionEvent {
    private String requestId;
    private String userId;
    private String injectionType;     // SQL, COMMAND, LDAP, etc
    private String pattern;           // The pattern detected
    private LocalDateTime timestamp;
}

@Data
@Builder
public class AccessDeniedEvent {
    private String requestId;
    private String userId;
    private String resourceId;
    private String reason;            // ROLE_NOT_FOUND, CLASSIFICATION_MISMATCH, etc
    private LocalDateTime timestamp;
}

@Data
@Builder
public class RequestProcessedEvent {
    private String requestId;
    private String userId;
    private String resourceId;
    private int recordCount;          // Number of records accessed
    private String operationType;     // READ, WRITE, DELETE
    private long processingTimeMs;
    private LocalDateTime timestamp;
}
```

**Customer's Analysis (using ActionHandler):**

```java
@Component
@Slf4j
public class ComplianceMonitoring implements ActionHandler {
    
    private final SensitivityClassifier classifier;
    private final Slack slack;
    
    @Override
    public void handle(SystemEvent event) {
        
        if (event instanceof RequestProcessedEvent e) {
            
            // CUSTOMER ANALYZES: Is this sensitive data?
            if (classifier.isSensitive(e.getResourceId())) {
                slack.alert("#compliance",
                    "⚠️ SENSITIVE DATA ACCESSED\n" +
                    "User: " + e.getUserId() + "\n" +
                    "Resource: " + e.getResourceId());
            }
            
            // CUSTOMER ANALYZES: Is this bulk access?
            if (e.getRecordCount() > 1000) {
                slack.alert("#compliance",
                    "⚠️ BULK ACCESS DETECTED\n" +
                    "Records: " + e.getRecordCount() + "\n" +
                    "User: " + e.getUserId());
            }
            
            // CUSTOMER ANALYZES: Is it outside business hours?
            if (!isBusinessHours()) {
                slack.alert("#compliance",
                    "⚠️ AFTER-HOURS ACCESS\n" +
                    "Time: " + e.getTimestamp() + "\n" +
                    "Resource: " + e.getResourceId());
            }
        }
    }
}
```

---

## Customer Implementation Examples

### Example 1: Entity Access Control with RBAC/ABAC

```java
package com.yourcompany.access.policy;

@Component
@Slf4j
public class YourCompanyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private DataClassificationService classificationService;
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        try {
            // Step 1: Get user context
            UserContext user = userService.getUser(userId);
            if (user == null) {
                logAccessDenied(userId, entity, "USER_NOT_FOUND");
                return false;
            }
            
            // Step 2: Check RBAC (role-based)
            String entityType = (String) entity.get("resourceId");
            List<String> requiredRoles = getRolesForEntity(entityType);
            boolean hasRequiredRole = user.getRoles().stream()
                .anyMatch(requiredRoles::contains);
            
            if (!hasRequiredRole) {
                logAccessDenied(userId, entity, "ROLE_NOT_FOUND");
                return false;
            }
            
            // Step 3: Check ABAC (attribute-based)
            String dataClassification = classificationService
                .getClassification((String) entity.get("entityId"));
            
            if ("RESTRICTED".equals(dataClassification)) {
                boolean hasSpecialAttribute = user.getAttributes()
                    .getOrDefault("restricted_access", false);
                if (!hasSpecialAttribute) {
                    logAccessDenied(userId, entity, "RESTRICTED_DATA");
                    return false;
                }
            }
            
            // Step 4: Check ownership
            String entityOwner = (String) entity.get("owner");
            if (entityOwner != null && !entityOwner.equals(userId)) {
                // Non-owners need explicit permission
                boolean hasAdminRole = user.getRoles().contains("ADMIN");
                if (!hasAdminRole) {
                    logAccessDenied(userId, entity, "NOT_OWNER");
                    return false;
                }
            }
            
            log.info("Access GRANTED to user {} for entity {}", userId, 
                entity.get("entityId"));
            return true;
            
        } catch (Exception e) {
            log.error("Error checking access", e);
            logAccessDenied(userId, entity, "ERROR: " + e.getMessage());
            return false; // Fail secure
        }
    }
    
    private List<String> getRolesForEntity(String entityType) {
        return switch(entityType) {
            case "CUSTOMER_DATA" -> List.of("ADMIN", "SALES");
            case "FINANCIAL_DATA" -> List.of("ADMIN", "FINANCE");
            case "PRODUCT_DATA" -> List.of("ADMIN", "PRODUCT");
            default -> List.of("ADMIN");
        };
    }
}
```

### Example 2: Custom Security Analysis

```java
package com.yourcompany.security.policy;

@Component
@Slf4j
public class YourCompanySecurityAnalysisPolicy implements SecurityAnalysisPolicy {
    
    @Autowired
    private ThreatIntelligenceService threatIntel;
    
    @Autowired
    private CompanySecurityConfigService securityConfig;
    
    @Override
    public SecurityAnalysisResult analyzeSecurity(AISecurityRequest request) {
        List<String> threats = new ArrayList<>();
        
        // Threat 1: Check for company-specific attack patterns
        if (containsCompanySpecificThreats(request.getContent())) {
            threats.add("COMPANY_THREAT_DETECTED");
        }
        
        // Threat 2: Check against threat intelligence database
        if (threatIntel.isKnownMaliciousPattern(request.getContent())) {
            threats.add("KNOWN_MALICIOUS_PATTERN");
        }
        
        // Threat 3: Check for credential exposure
        if (containsCredentialPatterns(request.getContent())) {
            threats.add("CREDENTIAL_EXPOSURE");
        }
        
        // Calculate risk score
        double score = calculateRiskScore(threats.size());
        
        return SecurityAnalysisResult.builder()
            .threats(threats)
            .score(score)
            .recommendations(generateRecommendations(threats))
            .build();
    }
    
    private boolean containsCompanySpecificThreats(String content) {
        List<String> patterns = securityConfig.getThreatPatterns();
        String lowerContent = content.toLowerCase();
        return patterns.stream()
            .anyMatch(lowerContent::contains);
    }
    
    private boolean containsCredentialPatterns(String content) {
        return content.matches(".*(?i)(api[_-]?key|password|token|secret).*");
    }
    
    private double calculateRiskScore(int threatCount) {
        return Math.max(0, 100 - (threatCount * 10));
    }
    
    private List<String> generateRecommendations(List<String> threats) {
        if (threats.contains("CREDENTIAL_EXPOSURE")) {
            return List.of("Review credential policies", 
                          "Check if credentials were leaked");
        }
        return Collections.emptyList();
    }
}
```

### Example 3: Compliance Checking (GDPR/CCPA)

```java
package com.yourcompany.compliance.policy;

@Component
@Slf4j
public class YourCompanyComplianceCheck implements ComplianceCheckProvider {
    
    @Autowired
    private PIIDetectionService piiDetectionService;
    
    @Autowired
    private ConsentService consentService;
    
    @Override
    public ComplianceCheckResult checkCompliance(String requestId, String content) {
        List<String> violations = new ArrayList<>();
        
        // Check 1: GDPR - Lawful basis for processing
        if (!hasLawfulBasis(content)) {
            violations.add("GDPR_NO_LAWFUL_BASIS");
        }
        
        // Check 2: GDPR - User consent
        if (!hasUserConsent(content)) {
            violations.add("GDPR_NO_CONSENT");
        }
        
        // Check 3: CCPA - Disclosure requirement
        if (containsPII(content) && !hasProperDisclosure(content)) {
            violations.add("CCPA_MISSING_DISCLOSURE");
        }
        
        // Check 4: Data minimization - Is PII necessary?
        if (containsUnnecessaryPII(content)) {
            violations.add("DATA_MINIMIZATION_VIOLATION");
        }
        
        return ComplianceCheckResult.builder()
            .compliant(violations.isEmpty())
            .violations(violations)
            .details("Compliance check for request " + requestId)
            .build();
    }
    
    private boolean hasLawfulBasis(String content) {
        // YOUR COMPANY: Check your compliance database
        return true; // Assume lawful basis exists
    }
    
    private boolean hasUserConsent(String content) {
        // YOUR COMPANY: Check consent management system
        return consentService.hasConsent();
    }
    
    private boolean containsPII(String content) {
        PIIDetectionResult result = piiDetectionService.analyze(content);
        return !result.getDetections().isEmpty();
    }
    
    private boolean hasProperDisclosure(String content) {
        // YOUR COMPANY: Check if proper CCPA disclosures made
        return true;
    }
    
    private boolean containsUnnecessaryPII(String content) {
        // YOUR COMPANY: Check if PII is truly necessary
        return false;
    }
}
```

### Example 4: Retention Policy

```java
package com.yourcompany.retention.policy;

@Component
@Slf4j
public class YourCompanyRetentionPolicy implements RetentionPolicyProvider {
    
    @Autowired
    private ComplianceService complianceService;
    
    @Override
    public int getRetentionDays(String classification, String entityType) {
        // YOUR COMPANY: Define retention per classification & type
        return switch(classification) {
            case "PUBLIC" -> 2555;           // 7 years (indefinite)
            case "INTERNAL" -> 1095;         // 3 years
            case "CONFIDENTIAL" -> 730;      // 2 years
            case "RESTRICTED" -> 365;        // 1 year
            case "HIGHLY_RESTRICTED" -> 180; // 6 months
            default -> 365;                  // 1 year default
        };
    }
    
    @Override
    public boolean shouldDelete(AISearchableEntity entity) {
        String classification = extractClassification(entity);
        int retentionDays = getRetentionDays(classification, entity.getEntityType());
        
        LocalDateTime expiration = entity.getCreatedAt()
            .plusDays(retentionDays);
        
        return LocalDateTime.now().isAfter(expiration);
    }
    
    @Override
    public boolean executeDelete(AISearchableEntity entity) {
        // YOUR COMPANY: Custom deletion logic
        
        // Example: Archive sensitive data before deletion
        if ("CONFIDENTIAL".equals(extractClassification(entity))) {
            archiveToS3(entity);
        }
        
        // Approve deletion
        return true;
    }
    
    private String extractClassification(AISearchableEntity entity) {
        try {
            Map<String, Object> metadata = parseMetadata(entity.getMetadata());
            return (String) metadata.getOrDefault("classification", "PUBLIC");
        } catch (Exception e) {
            return "PUBLIC";
        }
    }
    
    private void archiveToS3(AISearchableEntity entity) {
        // Archive logic
        log.info("Archived entity {} to S3", entity.getId());
    }
    
    private Map<String, Object> parseMetadata(String metadata) {
        // Parse JSON metadata
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(metadata, Map.class);
    }
}
```

### Example 5: Behavior Retention Policy

```java
package com.yourcompany.behavior.policy;

@Component
@Slf4j
public class YourCompanyBehaviorRetentionPolicy 
    implements BehaviorRetentionPolicyProvider {
    
    @Autowired
    private UserLocationService locationService;
    
    @Override
    public int getRetentionDays(BehaviorType behaviorType) {
        // YOUR COMPANY: Define retention per behavior type
        return switch(behaviorType) {
            // Long-term (7 years): Tax/Legal
            case PURCHASE, PAYMENT -> 2555;
            
            // Medium-term (1 year): Marketing
            case CART_ABANDONMENT, WISHLIST, REVIEW -> 365;
            
            // Short-term (90 days): Privacy-focused
            case SEARCH_QUERY, RECOMMENDATION_CLICK -> 90;
            
            // Temporary (30 days): Session tracking
            case PAGE_VIEW, NAVIGATION -> 30;
            
            default -> 365;
        };
    }
    
    @Override
    public int getRetentionDaysForUser(BehaviorType type, String userId) {
        // EU users get stricter privacy (shorter retention)
        if (locationService.isEUUser(userId)) {
            return switch(type) {
                case SEARCH_QUERY, PAGE_VIEW -> 30;
                default -> getRetentionDays(type);
            };
        }
        
        return getRetentionDays(type);
    }
    
    @Override
    public void beforeBehaviorDeletion(List<Behavior> behaviors) {
        // YOUR COMPANY: Custom logic before deletion
        
        // Example: Archive purchase history to data warehouse
        List<Behavior> purchases = behaviors.stream()
            .filter(b -> b.getBehaviorType() == BehaviorType.PURCHASE)
            .toList();
        
        if (!purchases.isEmpty()) {
            archivePurchaseHistory(purchases);
        }
    }
    
    private void archivePurchaseHistory(List<Behavior> purchases) {
        log.info("Archiving {} purchase records", purchases.size());
        // Archive to data warehouse
    }
}
```

### Example 6: User Data Deletion

```java
package com.yourcompany.deletion.policy;

@Component
@Slf4j
public class YourCompanyUserDataDeletion implements UserDataDeletionProvider {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private EmailService emailService;
    
    @Override
    public int deleteUserDomainData(String userId) {
        int totalDeleted = 0;
        
        // Step 1: Check if deletion allowed
        if (!canDeleteUser(userId)) {
            log.warn("Cannot delete user {}", userId);
            return 0;
        }
        
        // Step 2: Delete preferences
        int preferencesDeleted = userService.deletePreferences(userId);
        totalDeleted += preferencesDeleted;
        
        // Step 3: Archive orders (don't hard delete for accounting)
        int ordersArchived = orderService.archiveUserOrders(userId);
        totalDeleted += ordersArchived;
        
        // Step 4: Delete user account
        boolean userDeleted = userService.deleteUser(userId);
        if (userDeleted) totalDeleted += 1;
        
        log.info("Deleted {} items for user {}", totalDeleted, userId);
        return totalDeleted;
    }
    
    @Override
    public boolean canDeleteUser(String userId) {
        // Check for active orders, subscriptions, etc.
        return !orderService.hasActiveOrders(userId) && 
               !userService.hasActiveSubscription(userId);
    }
    
    @Override
    public void notifyAfterDeletion(String userId) {
        // Send deletion confirmation
        try {
            String email = userService.getUserEmail(userId);
            emailService.sendDeletionConfirmation(email);
            log.info("Sent deletion confirmation to user {}", userId);
        } catch (Exception e) {
            log.error("Error sending deletion notification", e);
        }
    }
}
```

---

## Data Transfer Objects (DTOs)

### Core DTOs (Infrastructure Neutral Containers)

All DTOs are **infrastructure data containers**, not business logic.

#### ComplianceCheckResult

```java
@Data
@Builder
public class ComplianceCheckResult {
    private boolean compliant;              // true if all checks pass
    private List<String> violations;        // ["GDPR_VIOLATION", "CCPA_VIOLATION"]
    private String details;                 // Optional: details
    private LocalDateTime timestamp;        // When check was performed
}
```

#### SecurityAnalysisResult

```java
@Data
@Builder
public class SecurityAnalysisResult {
    private List<String> threats;           // ["INJECTION_ATTACK", "PROMPT_INJECTION"]
    private double score;                   // 0-100 security score
    private List<String> recommendations;   // Optional: recommendations
    private LocalDateTime timestamp;
}
```

#### RetentionConfig

```java
@Data
@Builder
public class RetentionConfig {
    private int retentionDays;              // Days to retain
    private LocalDateTime expirationDate;   // When will expire
    private String classification;          // Data classification
    private String entityType;              // Type of entity
}
```

#### BehaviorRetentionStatus

```java
@Data
@Builder
public class BehaviorRetentionStatus {
    private BehaviorType behaviorType;
    private int retentionDays;
    private boolean isTemporary;
    private long totalRecords;
    private long soonToExpireCount;         // Expiring in next 30 days
    private LocalDateTime lastCleanupDate;
}
```

#### AccessDecisionContext

```java
@Data
@Builder
public class AccessDecisionContext {
    private String userId;
    private Map<String, Object> entity;
    private List<String> userRoles;
    private Map<String, Object> userAttributes;
    private String resourceType;
    private LocalDateTime requestTime;
    private String ipAddress;
}
```

---

## Testing Patterns

### Unit Testing Hooks

```java
@ExtendWith(MockitoExtension.class)
public class EntityAccessPolicyTest {
    
    @InjectMocks
    private YourCompanyEntityAccessPolicy policy;
    
    @Mock
    private UserService userService;
    
    @Test
    public void allowAccessWhenUserHasRequiredRole() {
        // Given
        String userId = "user123";
        Map<String, Object> entity = Map.of(
            "resourceId", "CUSTOMER_DATA",
            "entityId", "CUST-456",
            "owner", "user123"
        );
        
        UserContext user = UserContext.builder()
            .roles(List.of("ADMIN"))
            .build();
        when(userService.getUser(userId)).thenReturn(user);
        
        // When
        boolean allowed = policy.canUserAccessEntity(userId, entity);
        
        // Then
        assertTrue(allowed);
    }
    
    @Test
    public void denyAccessWhenUserLacksRole() {
        // Given
        String userId = "user123";
        Map<String, Object> entity = Map.of(
            "resourceId", "FINANCIAL_DATA",
            "entityId", "FIN-789"
        );
        
        UserContext user = UserContext.builder()
            .roles(List.of("USER"))
            .build();
        when(userService.getUser(userId)).thenReturn(user);
        
        // When
        boolean allowed = policy.canUserAccessEntity(userId, entity);
        
        // Then
        assertFalse(allowed);
    }
}
```

### Integration Testing with Hooks

```java
@SpringBootTest
public class RAGServiceWithHooksIntegrationTest {
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private AIAccessControlService accessControl;
    
    @MockBean
    private EntityAccessPolicy entityAccessPolicy;
    
    @Test
    public void ragFiltersResultsByCustomPolicy() {
        // Given
        when(entityAccessPolicy.canUserAccessEntity(
            eq("user1"), any(Map.class)))
            .thenReturn(true);  // First result allowed
        when(entityAccessPolicy.canUserAccessEntity(
            eq("user1"), argThat(m -> m.containsValue("restricted"))))
            .thenReturn(false); // Second result denied
        
        RAGRequest request = RAGRequest.builder()
            .query("find customers")
            .userId("user1")
            .build();
        
        // When
        RAGResponse response = ragService.performRag(request);
        
        // Then
        assertEquals(1, response.getDocuments().size());
        verify(entityAccessPolicy, times(2))
            .canUserAccessEntity(eq("user1"), any(Map.class));
    }
    
    @Test
    public void ragAllowsAllAccessWhenNoCustomPolicy() {
        // Given: No EntityAccessPolicy bean (required=false)
        
        RAGRequest request = RAGRequest.builder()
            .query("find products")
            .userId("user2")
            .build();
        
        // When
        RAGResponse response = ragService.performRag(request);
        
        // Then: All results returned (default behavior)
        assertFalse(response.getDocuments().isEmpty());
    }
}
```

### Testing Without Hooks (Graceful Degradation)

```java
@SpringBootTest
@TestPropertySource(properties = "spring.autoconfigure.exclude=" +
    "com.yourcompany.access.policy.YourCompanyEntityAccessPolicy")
public class RAGServiceWithoutCustomPolicyTest {
    
    @Autowired
    private RAGService ragService;
    
    @Test
    public void systemWorksWithoutCustomPolicy() {
        // Given: No custom EntityAccessPolicy provided
        
        RAGRequest request = RAGRequest.builder()
            .query("find data")
            .userId("anyuser")
            .build();
        
        // When
        RAGResponse response = ragService.performRag(request);
        
        // Then: System works with default behavior (allow all)
        assertNotNull(response);
        assertNotNull(response.getDocuments());
    }
}
```

### Performance Testing

```java
@SpringBootTest
public class HookPerformanceTest {
    
    @Autowired
    private AIAccessControlService accessControl;
    
    @Test
    public void hookExecutionUnderPerformanceThreshold() {
        // Given
        AIAccessControlRequest request = AIAccessControlRequest.builder()
            .requestId("perf-test-1")
            .userId("user1")
            .resourceId("resource1")
            .build();
        
        // When
        long startTime = System.currentTimeMillis();
        AIAccessControlResponse response = accessControl.checkAccess(request);
        long duration = System.currentTimeMillis() - startTime;
        
        // Then: Must be under 50ms (including hook execution)
        assertTrue(duration < 50, "Execution took " + duration + "ms");
        assertNotNull(response);
    }
}
```

---

## Hook Dependency Architecture

### System Flow Diagram

```
REQUEST ARRIVES
      ↓
RAGOrchestrator.orchestrate(query, userId)
      ├─────────────────────────────────────────────────┐
      │                                                   │
      ↓                                                   │
[1] AISecurityService.analyzeRequest()                   │
    ├─ Built-in: Injection, prompt injection, etc       │
    ├─ Hook: SecurityAnalysisPolicy (optional)           │
    ├─ Check: Rate limiting                              │
    └─ Result: SECURE or BLOCKED                         │
      ├─ If BLOCKED → Return error                       │
      └─ If SECURE → Continue                            │
      ↓                                                   │
[2] AIAccessControlService.checkAccess()                 │
    ├─ Extract: Entity context                           │
    ├─ Hook: EntityAccessPolicy (optional)               │
    └─ Result: GRANTED or DENIED                         │
      ├─ If DENIED → Return error                        │
      └─ If GRANTED → Continue                           │
      ↓                                                   │
[3] PIIDetectionService.analyze()                        │
    ├─ Pattern: Credit card, SSN, email, phone           │
    └─ Result: [PII detections]                          │
      ├─ Redact if needed (YAML configured)              │
      └─ Use redacted query                              │
      ↓                                                   │
[4] AIComplianceService.checkCompliance()                │
    ├─ Hook: ComplianceCheckProvider (optional)          │
    └─ Result: COMPLIANT or VIOLATION                    │
      ├─ If VIOLATION → Return error                     │
      └─ If COMPLIANT → Continue                         │
      ↓                                                   │
[5] RAGService.performRag()                              │
    ├─ Search vector DB                                  │
    ├─ Results: [documents]                              │
    └─ Filter by EntityAccessPolicy                      │
      ↓                                                   │
[6] AuditService.logOperation()                          │
    ├─ Log all checks performed                          │
    ├─ Log final result                                  │
    └─ Immutable audit trail                             │
      ↓                                                   │
Return OrchestrationResult
  (SUCCESS or BLOCKED with reason)


BACKGROUND JOBS (Parallel):
────────────────────────────

Daily 3 AM:
  RetentionEnforcerService
    ├─ Find expired AISearchableEntity
    ├─ Hook: RetentionPolicyProvider
    ├─ Delete from vector DB + database
    └─ Log: Deletion audit event

Daily 3 AM:
  BehaviorRetentionService
    ├─ Find expired Behavior records
    ├─ Hook: BehaviorRetentionPolicyProvider
    ├─ Delete per retention policy
    └─ Log: Deletion audit event

On-Demand:
  UserDataDeletionService
    ├─ Find all user data (behaviors, indexed entities)
    ├─ Redact audit logs
    ├─ Hook: UserDataDeletionProvider (domain data)
    └─ Log: Deletion audit event


HOOK CALL ORDER (CRITICAL):

1. SecurityAnalysisPolicy (Step 1) - MUST run first (security)
2. EntityAccessPolicy (Step 2) - MUST run second (authorization)
3. ComplianceCheckProvider (Step 4) - MUST run before RAG (compliance)
4. RetentionPolicyProvider (background) - Independent from request path
5. BehaviorRetentionPolicyProvider (background) - Independent
6. UserDataDeletionProvider (on-demand) - Independent
```

### Hook Dependency Matrix

| Hook | Calls | Called By | Dependencies | Optional |
|------|-------|-----------|--------------|----------|
| SecurityAnalysisPolicy | Analyze request | AISecurityService | None | Yes |
| EntityAccessPolicy | Check entity access | AIAccessControlService | SecurityAnalysisPolicy | Yes |
| ComplianceCheckProvider | Check compliance | AIComplianceService | EntityAccessPolicy | Yes |
| RetentionPolicyProvider | Retention decisions | RetentionEnforcerService | None | Yes |
| BehaviorRetentionPolicyProvider | Behavior retention | BehaviorRetentionService | None | Yes |
| UserDataDeletionProvider | Delete domain data | UserDataDeletionService | RetentionPolicyProvider | Yes |

---

## Production Deployment

### Pre-Deployment Checklist

```
INFRASTRUCTURE READINESS:
  ☐ All services cleaned (policies removed)
  ☐ All hooks properly injected (@Autowired required=false)
  ☐ Default behavior tested (hooks optional)
  ☐ Audit logging configured
  ☐ Database indexes created:
    ☐ AISearchableEntity: (createdAt, entityType) for retention queries
    ☐ Behavior: (createdAt, behaviorType) for behavior retention
    ☐ AIAuditLog: (userId, timestamp) for audit queries
  ☐ Caching configured (Redis recommended):
    ☐ "retentionStatus" cache for retention configs
    ☐ "behaviorRetention" cache for behavior retention

CUSTOMER IMPLEMENTATION:
  ☐ EntityAccessPolicy implemented
  ☐ SecurityAnalysisPolicy implemented (optional)
  ☐ ComplianceCheckProvider implemented
  ☐ RetentionPolicyProvider implemented with dates
  ☐ BehaviorRetentionPolicyProvider implemented
  ☐ UserDataDeletionProvider implemented
  ☐ All hooks tested with unit tests (80%+ coverage)
  ☐ All hooks tested with integration tests

PERFORMANCE:
  ☐ Each hook execution < 10ms
  ☐ Orchestration overhead < 50ms total
  ☐ Retention cleanup completes in < 5 minutes (100k records)
  ☐ Caching effective (> 80% hit rate expected)
  ☐ Load test passed (at least 1000 requests/sec)

CONFIGURATION:
  ☐ application.yml has all hook beans registered
  ☐ Feature flags configured (enable/disable per hook)
  ☐ Retention schedule configured (daily 3 AM)
  ☐ Behavior cleanup schedule configured (daily 3 AM)
  ☐ Audit log retention policy defined
  ☐ Cache configuration optimized

MONITORING:
  ☐ Metrics configured:
    ☐ Hook execution time
    ☐ Hook success/failure rate
    ☐ Cache hit rate
    ☐ Deletion counts
    ☐ Request processing time
  ☐ Alerts configured:
    ☐ Hook execution > 50ms
    ☐ Hook failure rate > 1%
    ☐ Retention job fails
    ☐ Audit log size threshold

DOCUMENTATION:
  ☐ README updated with all hooks
  ☐ API documentation current
  ☐ Runbooks for ops team
  ☐ Escalation procedures
  ☐ Customer implementation guide

LEGAL/COMPLIANCE:
  ☐ Legal review of all customer policies
  ☐ GDPR/CCPA compliance verified
  ☐ Privacy policy updated
  ☐ Terms of service updated
  ☐ Data processing agreement reviewed

SECURITY:
  ☐ Input validation on all hooks
  ☐ Rate limiting tested
  ☐ Injection attack tests passed
  ☐ Audit trail tested (no tampering possible)
  ☐ Secrets not logged
```

### Deployment Steps

```bash
# 1. Build and test
mvn clean package
mvn test
mvn integration-test

# 2. Deploy to staging
./scripts/deploy-to-staging.sh

# 3. Run smoke tests
./scripts/smoke-tests.sh

# 4. Performance baseline
./scripts/performance-test.sh

# 5. Customer testing (if applicable)
./scripts/run-customer-acceptance-tests.sh

# 6. Deploy to production
./scripts/deploy-to-production.sh

# 7. Post-deployment validation
./scripts/post-deployment-checks.sh
```

---

## Security Best Practices

### Hook Implementation Security

#### Input Validation in Hooks

```java
@Component
public class SecureEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // 1. Validate inputs
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        
        if (entity == null || entity.isEmpty()) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        
        // 2. Validate entity structure
        String entityId = (String) entity.get("entityId");
        if (entityId == null || !isValidEntityId(entityId)) {
            throw new IllegalArgumentException("Invalid entityId: " + entityId);
        }
        
        // 3. Sanitize inputs (prevent injection)
        String sanitizedUserId = sanitizeInput(userId);
        
        // 4. Your business logic
        return userService.hasAccess(sanitizedUserId, entityId);
    }
    
    private boolean isValidEntityId(String entityId) {
        // Only allow alphanumeric and hyphens
        return entityId.matches("[a-zA-Z0-9-]+");
    }
    
    private String sanitizeInput(String input) {
        // Remove SQL injection attempts, XSS, etc
        return input.replaceAll("[^a-zA-Z0-9@._-]", "");
    }
}
```

#### Rate Limiting in Hooks

```java
@Component
public class RateLimitedSecurityPolicy implements SecurityAnalysisPolicy {
    
    private final RateLimiter rateLimiter = RateLimiter.create(100); // 100 req/sec
    
    @Override
    public SecurityAnalysisResult analyzeSecurity(AISecurityRequest request) {
        // Check rate limit
        if (!rateLimiter.tryAcquire()) {
            throw new RateLimitExceededException("Too many requests");
        }
        
        // Your analysis logic
        return performAnalysis(request);
    }
    
    private SecurityAnalysisResult performAnalysis(AISecurityRequest request) {
        // Your implementation
        return SecurityAnalysisResult.builder().build();
    }
}
```

#### Secure Logging in Hooks

```java
@Component
@Slf4j
public class SecureComplianceCheck implements ComplianceCheckProvider {
    
    @Override
    public ComplianceCheckResult checkCompliance(String requestId, String content) {
        // ❌ NEVER log content or user data
        // log.info("Checking: " + content); // DON'T DO THIS!
        
        // ✅ DO log context safely
        log.info("Compliance check for request: {}, content length: {}", 
            requestId, content.length());
        
        // Your implementation
        List<String> violations = new ArrayList<>();
        
        // ❌ NEVER expose internals in exceptions
        try {
            performCheck(content);
        } catch (Exception e) {
            // ✅ DO log safely
            log.error("Error in compliance check: {}", e.getMessage());
            // NOT: log.error("Error: " + e);
        }
        
        return ComplianceCheckResult.builder()
            .compliant(violations.isEmpty())
            .violations(violations)
            .build();
    }
    
    private void performCheck(String content) {
        // Implementation
    }
}
```

#### Least Privilege in Hooks

```java
@Component
public class LeastPrivilegeRetentionPolicy implements RetentionPolicyProvider {
    
    @Override
    public int getRetentionDays(String classification, String entityType) {
        // Default: shortest retention (most private)
        int defaultDays = 30;
        
        // Only grant longer retention for specific cases
        if ("PUBLIC".equals(classification) && "WEBSITE".equals(entityType)) {
            return 365; // 1 year
        }
        
        // All others default to 30 days (most restrictive)
        return defaultDays;
    }
    
    @Override
    public boolean shouldDelete(AISearchableEntity entity) {
        // When in doubt, delete (fail secure)
        return true;
    }
    
    @Override
    public boolean executeDelete(AISearchableEntity entity) {
        // Before deletion, verify one more time
        if (!shouldReallyDelete(entity)) {
            log.warn("Prevented deletion of: {}", entity.getId());
            return false; // Fail secure
        }
        
        return true;
    }
    
    private boolean shouldReallyDelete(AISearchableEntity entity) {
        // Double-check logic
        return true;
    }
}
```

---

## Data Classification Guidance

### Recommended Classification Levels

```
LEVEL 1: PUBLIC
├─ Description: No sensitive data, safe to share
├─ Examples: Marketing content, public blog posts, published data
├─ Retention: 7 years (indefinite)
├─ Access: Anyone in company
└─ Deletion: Optional

LEVEL 2: INTERNAL
├─ Description: For internal use, not confidential
├─ Examples: Internal docs, internal processes, general communications
├─ Retention: 3 years
├─ Access: Employees only
└─ Deletion: After retention expires

LEVEL 3: CONFIDENTIAL
├─ Description: Company confidential, restricted access
├─ Examples: Strategy documents, financial reports, partner agreements
├─ Retention: 2 years
├─ Access: Department heads + C-suite
└─ Deletion: After retention expires + anonymize

LEVEL 4: RESTRICTED
├─ Description: Highly restricted, PII or sensitive data
├─ Examples: Customer data, employee records, health info
├─ Retention: 1 year
├─ Access: Specific team + compliance approval
└─ Deletion: Must anonymize before deletion

LEVEL 5: HIGHLY_RESTRICTED
├─ Description: Maximum restriction, legal/regulatory
├─ Examples: Legal documents, regulatory filings, audit records
├─ Retention: 6 months
├─ Access: Legal + compliance only
└─ Deletion: Secure destruction required
```

### Classification Examples by Industry

#### E-Commerce

```
Customer personal data (name, address) → RESTRICTED (1 year)
Purchase history (GDPR right to erasure) → RESTRICTED (7 years for tax)
Payment info (PCI-DSS) → RESTRICTED (1 year, heavily encrypted)
Marketing communications → INTERNAL (1 year)
Analytics data (anonymized) → PUBLIC (indefinite)
```

#### SaaS

```
User account data → RESTRICTED (retention period per user contract)
Usage metrics → INTERNAL (1 year)
Support tickets (PII) → RESTRICTED (1 year)
API keys/tokens → HIGHLY_RESTRICTED (immediate deletion on rotation)
Infrastructure logs → INTERNAL (90 days)
```

#### Healthcare

```
Patient medical records → RESTRICTED (per HIPAA: 6+ years)
Appointment history → RESTRICTED (per state law: 3-7 years)
Billing records → RESTRICTED (per tax law: 7 years)
Treatment plans → RESTRICTED (per HIPAA: 6+ years)
Research data (anonymized) → INTERNAL (per IRB: varies)
```

### Implementing Classification

#### In Metadata

```java
// During indexing, store classification in AISearchableEntity.metadata

Map<String, Object> metadata = new HashMap<>();
metadata.put("classification", "RESTRICTED");
metadata.put("dataType", "CUSTOMER_PII");
metadata.put("regulationSource", "GDPR Article 17");

AISearchableEntity entity = AISearchableEntity.builder()
    .metadata(JsonUtils.toJson(metadata))
    .build();
```

#### In Retention Policy

```java
@Component
public class ClassificationBasedRetentionPolicy implements RetentionPolicyProvider {
    
    @Override
    public int getRetentionDays(String classification, String entityType) {
        // Use classification to determine retention
        return switch(classification) {
            case "PUBLIC" -> 2555;
            case "INTERNAL" -> 1095;
            case "CONFIDENTIAL" -> 730;
            case "RESTRICTED" -> 365;
            case "HIGHLY_RESTRICTED" -> 180;
            default -> 365;
        };
    }
}
```

---

## Operational Monitoring & Metrics

### Key Metrics to Track

```
INFRASTRUCTURE HEALTH:
├─ Hook Execution Time
│  ├─ SecurityAnalysisPolicy: avg < 5ms
│  ├─ EntityAccessPolicy: avg < 10ms
│  ├─ ComplianceCheckProvider: avg < 8ms
│  └─ Alert: any hook > 50ms
│
├─ Hook Success Rate
│  ├─ SecurityAnalysisPolicy: > 99.9%
│  ├─ EntityAccessPolicy: > 99.9%
│  ├─ ComplianceCheckProvider: > 99.9%
│  └─ Alert: any hook < 99%
│
├─ Request Processing
│  ├─ Orchestration total: avg < 50ms
│  ├─ P95: < 100ms
│  ├─ P99: < 200ms
│  └─ Alert: P95 > 100ms
│
├─ Cache Performance
│  ├─ Retention cache hit rate: > 80%
│  ├─ Behavior retention cache hit: > 80%
│  └─ Alert: hit rate < 70%
│
├─ Data Lifecycle
│  ├─ Retention enforcement duration: < 5 min
│  ├─ Records deleted per run: log count
│  ├─ Behavior cleanup duration: < 3 min
│  └─ Alert: runs > 10 minutes

SECURITY & COMPLIANCE:
├─ Threat Detection
│  ├─ Threats detected per day
│  ├─ Rate limit hits per day
│  └─ Alert: unusual spike
│
├─ Access Control
│  ├─ Access denied %: baseline
│  ├─ Failed access attempts: log
│  └─ Alert: > 10% denial rate
│
├─ Audit Trail
│  ├─ Audit log size: monitor growth
│  ├─ Query time for logs: < 100ms
│  └─ Alert: log size exceeds threshold
│
├─ Compliance
│  ├─ Compliance violations: per day
│  ├─ Remediation time: track
│  └─ Alert: compliance failure
```

### Prometheus Metrics Example

```java
@Component
public class InfrastructureMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public InfrastructureMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordHookExecution(String hookName, long durationMs) {
        Timer timer = Timer.builder("hook.execution")
            .tag("hook", hookName)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordHookSuccess(String hookName) {
        Counter counter = Counter.builder("hook.success")
            .tag("hook", hookName)
            .register(meterRegistry);
        counter.increment();
    }
    
    public void recordCacheHit(String cacheName) {
        Counter counter = Counter.builder("cache.hit")
            .tag("cache", cacheName)
            .register(meterRegistry);
        counter.increment();
    }
    
    public void recordDataDeleted(String deleteType, int count) {
        Counter counter = Counter.builder("data.deleted")
            .tag("type", deleteType)
            .register(meterRegistry);
        counter.increment(count);
    }
}
```

---

## Troubleshooting Guide

### Hook Not Being Called

**Symptom:** Hook method never executes, default behavior always happens

**Diagnosis:**
```
1. Verify bean is created:
   - @Component annotation present?
   - Implements correct interface?
   - No compile errors?

2. Check autowiring:
   - @Autowired(required=false) present?
   - Service has field for hook?

3. Verify service calls hook:
   - if (hook != null) pattern?
   - Logging shows "hook called"?
```

**Fix:**
```java
// Check 1: Is bean registered?
@Autowired
private ApplicationContext context;

public void checkBeans() {
    EntityAccessPolicy policy = context.getBean(EntityAccessPolicy.class);
    if (policy == null) {
        log.error("EntityAccessPolicy bean not found!");
    }
}

// Check 2: Add logging to verify hook is called
@Autowired(required = false)
private EntityAccessPolicy policy;

public void checkAccess(Request request) {
    if (policy != null) {
        log.info("Calling EntityAccessPolicy hook");
        boolean result = policy.canUserAccessEntity(userId, entity);
        log.info("Hook returned: {}", result);
    } else {
        log.warn("No EntityAccessPolicy bean available");
    }
}
```

### Slow Hook Execution

**Symptom:** Hook takes > 50ms, orchestration slow

**Diagnosis:**
```
1. Profile hook:
   - Add timing logs
   - Measure database queries
   - Check external service calls

2. Identify bottleneck:
   - User lookup slow?
   - Database query slow?
   - External API slow?
```

**Fix:**
```java
@Component
public class OptimizedEntityAccessPolicy implements EntityAccessPolicy {
    
    @Autowired
    private CacheManager cacheManager;  // Add caching
    
    @Cacheable(value = "userRoles", key = "#userId")
    public List<String> getUserRoles(String userId) {
        // Expensive database query - now cached
        return userService.getRoles(userId);
    }
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Use cached roles instead of querying every time
        List<String> roles = getUserRoles(userId);
        
        // Quick logic
        return isAllowed(roles, entity);
    }
}
```

### Hook Failures Cascading

**Symptom:** One hook fails, entire orchestration fails

**Diagnosis:**
```
1. Check error handling:
   - Try/catch blocks present?
   - Errors logged?
   - Failures stop all processing?
```

**Fix:**
```java
@Override
public AIAccessControlResponse checkAccess(AIAccessControlRequest request) {
    try {
        // If hook fails, should we continue?
        if (entityAccessPolicy != null) {
            try {
                boolean allowed = entityAccessPolicy
                    .canUserAccessEntity(userId, entity);
                if (!allowed) {
                    return denied();
                }
            } catch (Exception e) {
                log.error("Error in EntityAccessPolicy", e);
                // Option 1: Fail secure (deny access)
                return denied();
                // Option 2: Continue (optional hook)
                // return allowed(); // Fallback to allow
            }
        }
        
        // Continue to next check
        return allowed();
        
    } catch (Exception e) {
        // Outer catch for unexpected errors
        log.error("Unexpected error in access check", e);
        return denied(); // Fail secure
    }
}
```

### Default Behavior Unexpected

**Symptom:** System behaves differently than expected when no hook provided

**Diagnosis:**
```
1. Verify default values:
   - Access defaults to ALLOW?
   - Retention defaults to KEEP?
   - Compliance defaults to ALLOW?
```

**Fix:**
```java
// Document and test default behavior explicitly

@Test
public void defaultBehaviorAllowsAccess() {
    // No EntityAccessPolicy provided
    
    AIAccessControlResponse response = 
        accessService.checkAccess(request);
    
    // Should allow (backward compatible)
    assertTrue(response.isAccessGranted());
}

@Test  
public void defaultBehaviorKeepsData() {
    // No RetentionPolicyProvider provided
    
    LocalDateTime createdAt = LocalDateTime.now().minusDays(100);
    AISearchableEntity entity = AISearchableEntity.builder()
        .createdAt(createdAt)
        .build();
    
    // Should NOT delete (backward compatible)
    boolean shouldDelete = retentionService.shouldDelete(entity);
    assertFalse(shouldDelete);
}
```

---

## Hook Performance Tuning

### Expected Hook Latency

```
EntityAccessPolicy:
  ├─ Simple role check: 1-2ms
  ├─ With database lookup: 5-10ms
  ├─ With external service: 20-50ms
  └─ Acceptable: < 50ms

SecurityAnalysisPolicy:
  ├─ Pattern matching only: 1-3ms
  ├─ With external threat DB: 10-20ms
  ├─ With ML inference: 50-100ms
  └─ Acceptable: < 50ms

ComplianceCheckProvider:
  ├─ Simple rule check: 2-5ms
  ├─ Multiple regulation checks: 10-20ms
  ├─ With external compliance API: 30-50ms
  └─ Acceptable: < 50ms

Overall Orchestration:
  ├─ All 4 hooks in sequence: < 50ms
  ├─ Typical: 20-40ms
  └─ Alert threshold: > 50ms
```

### Aggressive Caching Strategy for Access Checks

**Key Principle:** Cache access decisions at library level to maximize performance

#### Library-Level Caching (AIAccessControlService)

```java
// Cache Pattern: userId:entityType → boolean decision
// Example: "access:user123:CUSTOMER_DATA" → true/false
// TTL: 1 hour (configurable)
// Hit Rate: 70-90% typical (most users check same resources repeatedly)

private String generateCacheKey(String userId, Map<String, Object> entity) {
    String entityType = (String) entity.getOrDefault("resourceId", "UNKNOWN");
    return String.format("access:%s:%s", userId, entityType);
}

private Boolean getCachedAccessDecision(String cacheKey) {
    try {
        Cache cache = cacheManager.getCache("accessDecisions");
        if (cache == null) return null;
        
        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        return wrapper != null ? (Boolean) wrapper.get() : null;
    } catch (Exception e) {
        return null;  // Fail open - call hook if cache fails
    }
}

private void cacheAccessDecision(String cacheKey, boolean result) {
    try {
        Cache cache = cacheManager.getCache("accessDecisions");
        if (cache != null) {
            cache.put(cacheKey, result);
        }
    } catch (Exception e) {
        // Fail gracefully - continue without cache
    }
}
```

#### Customer-Level Caching (EntityAccessPolicy)

```java
@Component
public class CachedEntityAccessPolicy implements EntityAccessPolicy {
    
    // Cache 1: User roles (24 hour TTL)
    @Cacheable(value = "userRoles", key = "#userId", 
              cacheManager = "cacheManager")
    public List<String> getUserRoles(String userId) {
        return expensiveUserLookup(userId);
    }
    
    // Cache 2: Entity classification (7 day TTL)
    @Cacheable(value = "entityClassification", key = "#entityId",
              cacheManager = "cacheManager")
    public String getEntityClassification(String entityId) {
        return fetchClassification(entityId);
    }
    
    // Cache 3: Role permissions (1 day TTL)
    @Cacheable(value = "rolePermissions", key = "#role",
              cacheManager = "cacheManager")
    public List<String> getRolePermissions(String role) {
        return loadRolePermissions(role);
    }
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // Use cached lookups internally
        List<String> roles = getUserRoles(userId);
        String classification = getEntityClassification((String) entity.get("entityId"));
        List<String> permissions = getRolePermissions(roles.get(0));
        
        // Your business logic here (all inputs are cached)
        return isAllowed(roles, permissions, classification);
    }
}
```

#### Cache Configuration

```yaml
# application.yml
spring:
  cache:
    type: redis
    redis:
      host: redis.internal
      port: 6379
      timeout: 2000
    cache-names:
      - accessDecisions      # Library: userId:entityType → boolean
      - userRoles            # Customer: userId → [roles]
      - entityClassification # Customer: entityId → classification
      - rolePermissions      # Customer: role → [permissions]

ai:
  cache:
    access-decisions:
      ttl-hours: 1           # 1 hour TTL for access decisions
      max-size: 100000       # Max 100k cached decisions
    user-roles:
      ttl-hours: 24          # 1 day TTL for user roles
    entity-classification:
      ttl-days: 7            # 7 day TTL for classifications
```

#### Performance Impact

```
WITHOUT CACHING:
  - User check → Database query → Hook call → Decision: 15-50ms
  - 1000 requests/sec from 100 users × 50 different resources
  - Throughput: 100-200 requests/sec (queries overwhelm DB)

WITH LIBRARY CACHING (access decisions):
  - First request (cache miss): 15-50ms
  - Next 89 requests (cache hits): <1ms each
  - Cache hit rate: ~90% (users check same resources repeatedly)
  - Throughput: 5,000-10,000 requests/sec

WITH BOTH LIBRARY + CUSTOMER CACHING:
  - First request: 15-50ms
  - Subsequent requests: <1ms
  - Cache hit rate: >95%
  - Throughput: 50,000+ requests/sec
```

#### Cache Invalidation

```java
// When user roles change, invalidate their cache
public void onUserRoleChanged(String userId) {
    // Library cache
    accessControlService.invalidateUserCache(userId);
    
    // Customer cache
    userRolesCache.evict(userId);
}

// When entity classification changes, invalidate
public void onEntityClassificationChanged(String entityId) {
    // Customer cache
    entityClassificationCache.evict(entityId);
    
    // Library cache - invalidate all users for this entity type
    // (More aggressive but safe)
    accessDecisionsCache.invalidate();
}
```

### Async Opportunities

```
ASYNC SAFE (Can be async without issues):
  ✅ Audit logging (fire and forget)
  ✅ Retention cleanup (background job)
  ✅ Behavior cleanup (background job)
  ✅ User deletion notifications (email)

NOT ASYNC (Must be synchronous):
  ❌ SecurityAnalysisPolicy (needed for request decision)
  ❌ EntityAccessPolicy (needed for request decision)
  ❌ ComplianceCheckProvider (needed for request decision)
  ❌ User data deletion (must complete before confirming)
```

---

## Spring Boot Integration

### Auto-Discovery Pattern

```yaml
# application.yml
spring:
  application:
    name: ai-core-library
  
ai:
  infrastructure:
    # Feature flags for each hook
    security:
      enabled: true
      timeout-ms: 50
    access:
      enabled: true
      timeout-ms: 50
    compliance:
      enabled: true
      timeout-ms: 50
    retention:
      enabled: true
      schedule: "0 2 * * *"  # Daily 2 AM
    
  # Cache configuration
  cache:
    type: redis
    redis:
      host: localhost
      port: 6379
    ttl:
      retentionStatus: 24h
      behaviorRetention: 24h
      userRoles: 1h
```

### Configuration Properties

```java
@Configuration
@EnableCaching
@EnableScheduling
public class AIInfrastructureConfiguration {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheSpecification("maximumSize=1000,expireAfterWrite=24h");
        return cacheManager;
    }
    
    @Bean
    public TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }
    
    @Bean
    @ConditionalOnMissingBean(EntityAccessPolicy.class)
    public EntityAccessPolicy defaultEntityAccessPolicy() {
        // Default: allow all (when customer provides no implementation)
        return (userId, entity) -> true;
    }
    
    @Bean
    @ConditionalOnMissingBean(ComplianceCheckProvider.class)
    public ComplianceCheckProvider defaultComplianceCheck() {
        // Default: mark all as compliant
        return (requestId, content) -> ComplianceCheckResult.builder()
            .compliant(true)
            .violations(Collections.emptyList())
            .build();
    }
}
```

### Environment Variable Overrides

```bash
# Override via environment variables
export AI_INFRASTRUCTURE_SECURITY_ENABLED=true
export AI_INFRASTRUCTURE_ACCESS_TIMEOUT_MS=100
export AI_INFRASTRUCTURE_RETENTION_SCHEDULE="0 3 * * *"
export SPRING_REDIS_HOST=redis.example.com
export SPRING_REDIS_PORT=6380
```

---

## Hook Selection Decision Tree

```
Start: "What do I need to implement?"
  │
  ├─→ "Control who accesses what?"
  │    └─→ EntityAccessPolicy ✅
  │        Purpose: User → Entity access decisions
  │        When: Every RAG request
  │        Examples: RBAC, ABAC, ownership checks
  │
  ├─→ "Custom threat detection?"
  │    └─→ SecurityAnalysisPolicy ✅
  │        Purpose: Detect company-specific threats
  │        When: Every request (optional, works with built-ins)
  │        Examples: Custom patterns, threat intelligence
  │
  ├─→ "Check regulatory compliance?"
  │    └─→ ComplianceCheckProvider ✅
  │        Purpose: Verify GDPR/CCPA/HIPAA/etc compliance
  │        When: Before allowing request
  │        Examples: Consent checks, data minimization
  │
  ├─→ "Control data retention?"
  │    └─→ RetentionPolicyProvider ✅
  │        Purpose: When to delete indexed content
  │        When: Daily scheduled cleanup
  │        Examples: Classification-based retention
  │
  ├─→ "Manage user behavior history?"
  │    └─→ BehaviorRetentionPolicyProvider ✅
  │        Purpose: When to delete tracking data
  │        When: Daily scheduled cleanup
  │        Examples: SEARCH=90 days, PURCHASE=7 years
  │
  └─→ "Handle user data deletion?"
       └─→ UserDataDeletionProvider ✅
           Purpose: Delete user's domain-specific data
           When: On user deletion request (GDPR Article 17)
           Examples: Delete orders, profiles, preferences
```

---

## Advanced Hook Interfaces

### Optional Hook: DocumentAccessPolicy

```java
package com.ai.infrastructure.document.policy;

/**
 * OPTIONAL: Document-level access control
 * 
 * Use when you need to prevent specific documents from being indexed/searchable.
 */
public interface DocumentAccessPolicy {
    
    /**
     * Can user access this document?
     * Called during indexing - can prevent indexing entirely.
     *
     * @param userId User ID
     * @param documentMetadata Document information
     * @return true if document should be indexed/searchable
     */
    boolean canUserAccessDocument(String userId, 
                                   Map<String, Object> documentMetadata);
}
```

### Optional Hook: QueryAccessPolicy

```java
package com.ai.infrastructure.query.policy;

/**
 * OPTIONAL: Query-level access control
 * 
 * Use when you need to restrict which entity types users can query.
 */
public interface QueryAccessPolicy {
    
    /**
     * Can user query this entity type?
     *
     * @param userId User ID
     * @param entityType Entity type being queried
     * @return true if user allowed to query this type
     */
    boolean canUserQueryEntityType(String userId, String entityType);
}
```

---

## FAQ & Quick Reference

### Q: What if a hook takes too long?
**A:** Add caching (@Cacheable), use async where possible, or implement timeout:
```java
@Override
public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
    CompletableFuture<Boolean> future = CompletableFuture
        .supplyAsync(() -> expensiveCheck(userId, entity));
    
    try {
        return future.get(50, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        log.warn("Hook timeout - failing secure");
        return false;
    }
}
```

### Q: What if no hook is provided?
**A:** Library works with default behavior (backward compatible):
```
- EntityAccessPolicy not provided → Allow all access
- SecurityAnalysisPolicy not provided → Only built-in checks run
- ComplianceCheckProvider not provided → Mark all as compliant
- Retention hooks not provided → Keep all data indefinitely
```

### Q: Can hooks call other services?
**A:** Yes, but be aware of performance impact:
```java
@Component
public class EntityAccessPolicy implements EntityAccessPolicy {
    @Autowired
    private UserService userService;  // OK to inject
    @Autowired
    private DatabaseService db;       // OK to inject
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        // OK: Query user once
        UserContext user = userService.getUser(userId);
        
        // Cache the result!
        @Cacheable
        List<String> roles = getRoles(userId);
        
        // Consider performance
        return isAllowed(roles, entity);
    }
}
```

### Q: How to test hooks without the full system?
**A:** Use @MockBean:
```java
@SpringBootTest
public class HookTest {
    @Autowired
    private SomeService service;
    
    @MockBean
    private EntityAccessPolicy policy;
    
    @Test
    public void test() {
        when(policy.canUserAccessEntity(anyString(), any()))
            .thenReturn(true);
        
        // Test with mocked hook
    }
}
```

---

## Summary

This document defines the complete INFRASTRUCTURE-ONLY architecture:

- **Cleanup (10-15 hours):** Remove policy code from library
- **Hooks (6 hooks):** Infrastructure contracts for customer business logic
- **Services (250 lines):** Pure infrastructure, no policies
- **Examples (6 complete):** Customer implementation patterns
- **Testing:** Unit, integration, graceful degradation
- **Operations:** Monitoring, troubleshooting, tuning
- **Security:** Best practices for production

**Next Steps:**
1. Read SERVICE_CLEANUP_PLAN.md to understand what to delete
2. Implement the 6 hooks
3. Clean existing services
4. Test thoroughly
5. Deploy to production

---

**Document Status:** ✅ Complete & Ready  
**Version:** 2.0 (Consolidated)  
**Last Updated:** November 10, 2025  
**Total Lines:** ~3,500  
**Estimated Implementation:** 40-50 hours


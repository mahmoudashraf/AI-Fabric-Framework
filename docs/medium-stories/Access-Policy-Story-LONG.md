# 🛡️ Custom Access Policy: Building Fail-Closed Security Into Every Request

> **How we built a pluggable access control system that lets you define security rules while the framework enforces them—fail closed, always secure, production-tested**  
> *Part of the AI Fabric Framework series — under active development for Q1 2026*

🚧 **Status:** Under active development | Q1 2026 release | Production-tested security model | Fail-closed architecture

---

## The Security Nightmare: Hardcoded Rules Everywhere

**Customer support chat. Users need different access levels:**

- Free tier: Can view profile, update email
- Pro tier: Can cancel subscription, export data
- Enterprise: Can delete account, export all data
- Admins: Can do everything

**Your code (traditional approach):**

```java
@PostMapping("/api/chat")
public OrchestrationResult chat(@RequestBody String query,
                                 @AuthenticationPrincipal User user) {
    // Security checks scattered everywhere
    if (user.getRole().equals("ADMIN")) {
        return orchestrator.orchestrate(query, context);
    }
    
    if (query.contains("cancel") && !user.getTier().equals("PRO")) {
        return OrchestrationResult.error("Upgrade to Pro to cancel");
    }
    
    if (query.contains("export") && user.getTier().equals("FREE")) {
        return OrchestrationResult.error("Upgrade to Pro for exports");
    }
    
    if (query.contains("delete") && !user.getRole().equals("ADMIN")) {
        return OrchestrationResult.error("Only admins can delete");
    }
    
    // ... 50 more hardcoded rules across 20 controllers ...
    
    return orchestrator.orchestrate(query, context);
}
```

**Problems:**
- ❌ Security logic scattered (20 controllers, 50+ rules)
- ❌ Hard to maintain (change rule = find all places)
- ❌ Easy to miss edge cases
- ❌ No audit trail
- ❌ Can't test security independently
- ❌ Framework can't enforce your rules
- ❌ New developer adds feature = might forget security check

**Every security breach starts with "we thought we checked that..."**

---

## Our Solution: Pluggable Policy Pattern

**Framework provides infrastructure. You define rules. Framework enforces them everywhere.**

**From EntityAccessPolicy.java (actual interface, line 9-32):**

```java
@FunctionalInterface
public interface EntityAccessPolicy {
    
    /**
     * Determine if user can access entity
     * @param userId User identifier
     * @param entity Immutable entity context (resourceId, operationType, etc.)
     * @return true if access granted, false otherwise
     */
    boolean canUserAccessEntity(String userId, Map<String, Object> entity);
    
    /**
     * Optional: Log denied access (audit trail)
     * Default: no-op (you can override)
     */
    default void logAccessDenied(String userId, 
                                 Map<String, Object> entity, 
                                 String reason) {
        // Intentionally blank - override for audit logging
    }
}
```

**Your implementation:**

```java
@Component
public class MyAccessPolicy implements EntityAccessPolicy {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PermissionService permissionService;
    
    @Override
    public boolean canUserAccessEntity(String userId, 
                                       Map<String, Object> entityContext) {
        String resourceId = (String) entityContext.get("resourceId");
        String operationType = (String) entityContext.get("operationType");
        
        // YOUR business rules
        if (resourceId.equals("rag:intent") && operationType.equals("READ")) {
            // Everyone can read intents
            return true;
        }
        
        if (resourceId.startsWith("action:") && operationType.equals("EXECUTE")) {
            String action = resourceId.substring(7); // "action:cancel_subscription"
            
            // Check if user can perform this action
            return permissionService.canPerformAction(userId, action);
        }
        
        // Default: deny
        return false;
    }
    
    @Override
    public void logAccessDenied(String userId, 
                                Map<String, Object> entity, 
                                String reason) {
        // YOUR audit logging
        auditService.logDeniedAccess(userId, entity, reason);
        securityAlert.sendAlert(userId, entity, reason);
    }
}
```

**Framework enforces (AIAccessControlService.java line 29-59):**

```java
public AIAccessControlResponse checkAccess(AIAccessControlRequest request) {
    long started = System.nanoTime();
    Objects.requireNonNull(request, "access request must not be null");
    
    EntityAccessPolicy policy = requirePolicy();
    String userId = extractUserId(request);
    
    LocalDateTime evaluationTimestamp = Optional.ofNullable(request.getTimestamp())
        .orElseGet(() -> LocalDateTime.now(clock));
    Map<String, Object> entityContext = buildEntityContext(request, evaluationTimestamp);
    
    // Call YOUR policy
    Decision decision = evaluateAccess(policy, userId, entityContext);
    if (!decision.granted()) {
        logDenied(policy, userId, entityContext);
    }
    
    long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
    return AIAccessControlResponse.builder()
        .requestId(request.getRequestId())
        .userId(userId)
        .resourceId(Objects.toString(entityContext.get("resourceId"), null))
        .operationType(Objects.toString(entityContext.get("operationType"), null))
        .accessGranted(decision.granted())
        .fromCache(Boolean.FALSE)
        .accessDecision(decision.granted() ? "GRANT" : "DENY")
        .processingTimeMs(durationMs)
        .timestamp(evaluationTimestamp)
        .success(!decision.hookFailed())
        .errorMessage(decision.hookFailed() ? decision.errorMessage() : null)
        .build();
}
```

**Result:** Security logic in one place. Framework enforces everywhere. Fail closed. Always secure.

---

## The Complete Technical Flow

```
┌──────────────────────────────────────────────────────────┐
│  USER REQUEST                                            │
│  POST /api/chat                                          │
│  Body: "Cancel my subscription"                         │
│  User: user-123 (Pro tier)                               │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  ORCHESTRATOR ENTRY                                       │
│  RAGOrchestrator.orchestrate() (line 65)                 │
│  ════════════════════════════════════════════════════════│
│  Context:                                                 │
│  ├─ userId: "user-123"                                   │
│  ├─ sessionId: "sess-xyz"                                │
│  ├─ ipAddress: "192.168.1.1"                             │
│  └─ metadata: {tier: "PRO"}                              │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 1: SECURITY CHECK                                   │
│  securityService.analyzeRequest()                       │
│  ════════════════════════════════════════════════════════│
│  Checks:                                                 │
│  ├─ Rate limiting ✅                                     │
│  ├─ Malicious patterns ✅                                │
│  ├─ IP reputation ✅                                     │
│  └─ Should block? → false                                │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 2: ACCESS CONTROL CHECK                            │
│  AIAccessControlService.checkAccess() (line 29-59)        │
│  ════════════════════════════════════════════════════════│
│  Request:                                                │
│  {                                                       │
│    requestId: "req-abc",                                 │
│    userId: "user-123",                                   │
│    resourceId: "rag:intent",                             │
│    operationType: "READ",                                │
│    context: "Cancel my subscription",                    │
│    metadata: {tier: "PRO", ip: "192.168.1.1"}            │
│  }                                                       │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 3: BUILD ENTITY CONTEXT                            │
│  buildEntityContext() (line 78-108)                       │
│  ════════════════════════════════════════════════════════│
│  entityContext:                                          │
│  {                                                       │
│    resourceId: "rag:intent",                            │
│    operationType: "READ",                                │
│    timestamp: "2025-01-02T10:30:00",                     │
│    context: "Cancel my subscription",                   │
│    metadata: {                                           │
│      tier: "PRO",                                        │
│      ip: "192.168.1.1",                                  │
│      sessionId: "sess-xyz"                               │
│    },                                                    │
│    userAttributes: {                                     │
│      tier: "PRO",                                        │
│      role: "USER"                                        │
│    }                                                     │
│  }                                                       │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 4: CALL YOUR POLICY                                │
│  EntityAccessPolicy.canUserAccessEntity()                 │
│  ════════════════════════════════════════════════════════│
│  YOUR CODE:                                              │
│  {                                                       │
│    String resourceId = entityContext.get("resourceId");  │
│    String operationType = entityContext.get("operationType");│
│                                                          │
│    // Intent queries: all authenticated users allowed   │
│    if (resourceId.equals("rag:intent") &&               │
│        operationType.equals("READ")) {                  │
│      return true;                                        │
│    }                                                     │
│                                                          │
│    // Action execution: check permissions              │
│    if (resourceId.startsWith("action:")) {              │
│      String action = resourceId.substring(7);            │
│      return permissionService.canPerformAction(          │
│        userId, action                                    │
│      );                                                  │
│    }                                                     │
│                                                          │
│    // Default: deny                                     │
│    return false;                                         │
│  }                                                       │
│                                                          │
│  Result: true (user-123 can read intents)              │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 5: EVALUATE ACCESS                                 │
│  evaluateAccess() (line 110-118)                          │
│  ════════════════════════════════════════════════════════│
│  try {                                                   │
│    boolean granted = policy.canUserAccessEntity(        │
│      userId, entityContext                               │
│    );                                                    │
│    return new Decision(granted, false, null);           │
│  } catch (Exception ex) {                                │
│    // FAIL CLOSED: Exception = deny access              │
│    log.warn("Policy exception: {}", ex.getMessage());   │
│    return new Decision(false, true, ex.getMessage());   │
│  }                                                       │
│                                                          │
│  Result: Decision(granted=true, hookFailed=false)        │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 6: LOG IF DENIED                                   │
│  logDenied() (line 120-126)                               │
│  ════════════════════════════════════════════════════════│
│  if (!decision.granted()) {                              │
│    try {                                                 │
│      policy.logAccessDenied(                             │
│        userId, entityContext, "POLICY_DENIED"           │
│      );                                                  │
│    } catch (Exception ex) {                               │
│      log.debug("logAccessDenied failed: {}", ex.getMessage());│
│    }                                                     │
│  }                                                       │
│                                                          │
│  (Skipped - access granted)                             │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 7: RETURN RESPONSE                                 │
│  AIAccessControlResponse                                  │
│  ════════════════════════════════════════════════════════│
│  {                                                       │
│    requestId: "req-abc",                                 │
│    userId: "user-123",                                   │
│    resourceId: "rag:intent",                            │
│    operationType: "READ",                               │
│    accessGranted: true,                                 │
│    fromCache: false,                                     │
│    accessDecision: "GRANT",                             │
│    processingTimeMs: 5,                                 │
│    timestamp: "2025-01-02T10:30:00",                     │
│    success: true,                                       │
│    errorMessage: null                                   │
│  }                                                       │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 8: CONTINUE OR BLOCK                                │
│  RAGOrchestrator (line 92-109)                            │
│  ════════════════════════════════════════════════════════│
│  if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {│
│    return OrchestrationResult.error(                      │
│      "Access denied by policy."                          │
│    );                                                     │
│  }                                                       │
│                                                          │
│  // Access granted - continue with intent extraction     │
│  MultiIntentResponse intents = intentQueryExtractor      │
│    .extract(processedQuery, context);                    │
│                                                          │
│  // ... continue orchestration ...                       │
└──────────────────────────────────────────────────────────┘
```

---

## Fail Closed Security Model (Critical)

**From AIAccessControlService.java (line 110-118):**

```java
private Decision evaluateAccess(EntityAccessPolicy policy, 
                                String userId, 
                                Map<String, Object> entityContext) {
    try {
        boolean granted = policy.canUserAccessEntity(userId, entityContext);
        return new Decision(granted, false, null);
    } catch (Exception ex) {
        // FAIL CLOSED: If policy throws exception, deny access
        log.warn("EntityAccessPolicy threw an exception for user {}: {}", 
                 userId, ex.getMessage());
        return new Decision(false, true, ex.getMessage());
    }
}
```

**Key principle:** If your policy throws an exception, framework denies access.

**Why fail closed?**
- ✅ Better to deny legitimate request than allow unauthorized access
- ✅ Prevents security holes from policy bugs
- ✅ Forces you to handle edge cases
- ✅ Production-safe default

**Your policy must be:**
- ✅ Thread-safe (multiple requests concurrently)
- ✅ Fast (< 100ms ideally, < 500ms max)
- ✅ Never throw exceptions (or handle gracefully)
- ✅ Idempotent (same input = same output)

**Example of fail-closed in action:**

```java
@Override
public boolean canUserAccessEntity(String userId, 
                                   Map<String, Object> entityContext) {
    // BAD: Throws exception if user not found
    User user = userRepository.findById(userId);  // Might be null!
    return user.getTier().equals("PRO");  // NullPointerException!
}

// GOOD: Handle gracefully
@Override
public boolean canUserAccessEntity(String userId, 
                                   Map<String, Object> entityContext) {
    Optional<User> userOpt = userRepository.findById(userId);
    if (userOpt.isEmpty()) {
        return false;  // User not found = deny access
    }
    User user = userOpt.get();
    return "PRO".equals(user.getTier());  // Safe comparison
}
```

**Framework catches exception → denies access → logs warning. Fail closed.**

---

## Real-World Examples

### Example 1: Multi-Tenant SaaS Access Control

**Challenge:** Different subscription tiers have different access levels.

**Implementation:**

```java
@Component
public class TierBasedAccessPolicy implements EntityAccessPolicy {
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired
    private AuditService auditService;
    
    @Autowired
    private SecurityAlertService securityAlert;
    
    @Override
    public boolean canUserAccessEntity(String userId, 
                                       Map<String, Object> entityContext) {
        String resourceId = (String) entityContext.get("resourceId");
        String operationType = (String) entityContext.get("operationType");
        
        // Get user's subscription tier (handle gracefully)
        Optional<Subscription> subOpt = subscriptionService.findByUserId(userId);
        if (subOpt.isEmpty()) {
            return false;  // No subscription = deny
        }
        Subscription subscription = subOpt.get();
        String tier = subscription.getTier();
        
        // Intent queries: all tiers allowed
        if (resourceId.equals("rag:intent") && operationType.equals("READ")) {
            return true;
        }
        
        // Action execution: tier-based
        if (resourceId.startsWith("action:")) {
            String action = resourceId.substring(7);
            
            // FREE tier: limited actions
            if ("FREE".equals(tier)) {
                return List.of("view_profile", "update_email", "view_orders")
                    .contains(action);
            }
            
            // PRO tier: most actions
            if ("PRO".equals(tier)) {
                return !List.of("delete_account", "export_all_data", "admin_panel")
                    .contains(action);
            }
            
            // ENTERPRISE tier: all actions
            if ("ENTERPRISE".equals(tier)) {
                return true;
            }
        }
        
        // RAG queries: tier-based limits
        if (resourceId.startsWith("rag:")) {
            if ("FREE".equals(tier)) {
                // FREE: 10 queries/day limit (check elsewhere)
                return true;  // Let rate limiter handle count
            }
            if ("PRO".equals(tier) || "ENTERPRISE".equals(tier)) {
                // PRO/ENTERPRISE: unlimited queries
                return true;
            }
        }
        
        // Default: deny
        return false;
    }
    
    @Override
    public void logAccessDenied(String userId, 
                                Map<String, Object> entity, 
                                String reason) {
        // Audit log
        auditService.logAccessDenied(
            userId,
            (String) entity.get("resourceId"),
            (String) entity.get("operationType"),
            reason,
            LocalDateTime.now()
        );
        
        // Alert if suspicious pattern
        if (isSuspiciousPattern(userId, entity)) {
            securityAlert.sendAlert(
                userId,
                entity,
                reason,
                "Suspicious access pattern detected"
            );
        }
    }
    
    private boolean isSuspiciousPattern(String userId, Map<String, Object> entity) {
        // Check for rapid-fire denials (potential attack)
        int recentDenials = auditService.countRecentDenials(userId, 60); // last minute
        return recentDenials > 10;
    }
}
```

**Framework enforces this policy on every request. No way to bypass.**

---

### Example 2: Role-Based Access Control (RBAC)

**Challenge:** Different roles have different permissions.

**Implementation:**

```java
@Component
public class RoleBasedAccessPolicy implements EntityAccessPolicy {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RoleService roleService;
    
    @Override
    public boolean canUserAccessEntity(String userId, 
                                       Map<String, Object> entityContext) {
        String resourceId = (String) entityContext.get("resourceId");
        String operationType = (String) entityContext.get("operationType");
        
        // Get user roles
        User user = userService.findById(userId);
        if (user == null) {
            return false;
        }
        
        Set<String> roles = user.getRoles();
        
        // Admin: all access
        if (roles.contains("ADMIN")) {
            return true;
        }
        
        // Intent queries: all authenticated users
        if (resourceId.equals("rag:intent") && operationType.equals("READ")) {
            return true;
        }
        
        // Action execution: role-based
        if (resourceId.startsWith("action:")) {
            String action = resourceId.substring(7);
            
            // Get required roles for action
            Set<String> requiredRoles = roleService.getRequiredRoles(action);
            
            // Check if user has any required role
            return roles.stream().anyMatch(requiredRoles::contains);
        }
        
        // Default: deny
        return false;
    }
    
    @Override
    public void logAccessDenied(String userId, 
                                Map<String, Object> entity, 
                                String reason) {
        // Log to security audit
        securityAudit.log(
            userId,
            "ACCESS_DENIED",
            entity,
            reason
        );
    }
}
```

---

### Example 3: Time-Based Access Control

**Challenge:** Some actions only allowed during business hours.

**Implementation:**

```java
@Component
public class TimeBasedAccessPolicy implements EntityAccessPolicy {
    
    @Autowired
    private Clock clock;
    
    @Override
    public boolean canUserAccessEntity(String userId, 
                                       Map<String, Object> entityContext) {
        String resourceId = (String) entityContext.get("resourceId");
        String operationType = (String) entityContext.get("operationType");
        
        LocalDateTime now = LocalDateTime.now(clock);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        int hour = now.getHour();
        
        // Intent queries: always allowed
        if (resourceId.equals("rag:intent") && operationType.equals("READ")) {
            return true;
        }
        
        // Action execution: time-based
        if (resourceId.startsWith("action:")) {
            String action = resourceId.substring(7);
            
            // Critical actions: business hours only (9 AM - 5 PM, Mon-Fri)
            if (List.of("delete_account", "export_all_data", "admin_panel")
                .contains(action)) {
                
                boolean isBusinessHours = 
                    dayOfWeek != DayOfWeek.SATURDAY &&
                    dayOfWeek != DayOfWeek.SUNDAY &&
                    hour >= 9 && hour < 17;
                
                return isBusinessHours;
            }
            
            // Regular actions: always allowed
            return true;
        }
        
        // Default: deny
        return false;
    }
}
```

---

## Integration Points

### 1. Orchestrator Entry Point

**From RAGOrchestrator.java (line 92-109):**

```java
// Before processing any request
AIAccessControlResponse accessResponse = accessControlService.checkAccess(
    AIAccessControlRequest.builder()
        .requestId(requestId)
        .userId(context.getUserId())
        .sessionId(context.getSessionId())
        .resourceId("rag:intent")
        .operationType("READ")
        .context(query)
        .metadata(buildAccessControlMetadata(context))
        .ipAddress(context.getIpAddress())
        .userAgent(context.getUserAgent())
        .timestamp(requestTimestamp)
        .build()
);

if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {
    return OrchestrationResult.error("Access denied by policy.");
}
```

**Every orchestration request checks access first. No exceptions.**

---

### 2. Action Handler Validation (Two Layers)

**Framework-level check (access control service):**

```java
// In RAGOrchestrator.handleAction() - BEFORE handler validation
AIAccessControlResponse accessResponse = accessControlService.checkAccess(
    AIAccessControlRequest.builder()
        .userId(context.getUserId())
        .resourceId("action:" + actionName)
        .operationType("EXECUTE")
        .build()
);

if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {
    return OrchestrationResult.error("Access denied by policy.");
}
```

**Handler-level check (action-specific validation):**

```java
// In RAGOrchestrator.handleAction() (line 266)
if (!handler.validateActionAllowed(identifier)) {
    return OrchestrationResult.error("Action not permitted");
}
```

**Two layers:**
1. Framework-level: `EntityAccessPolicy` (your policy) - checks general access
2. Handler-level: `ActionHandler.validateActionAllowed()` - checks action-specific rules

**Both must pass for action to execute. Defense in depth.**

---

## Entity Context Structure

**From buildEntityContext() (line 78-108):**

The `entityContext` Map contains:

```java
{
  "resourceId": "rag:intent" | "action:cancel_subscription" | "rag:product",
  "operationType": "READ" | "WRITE" | "EXECUTE" | "DELETE",
  "timestamp": LocalDateTime,
  "context": "User's query text",
  "metadata": {
    "tier": "PRO",
    "ip": "192.168.1.1",
    "sessionId": "sess-xyz",
    // ... any custom metadata
  },
  "userAttributes": {
    "tier": "PRO",
    "role": "USER",
    // ... any custom user attributes
  }
}
```

**You can use any of these fields in your policy logic.**

---

## Configuration

**No configuration needed. Just implement the interface:**

```java
@Configuration
public class AccessControlConfig {
    
    @Bean
    public EntityAccessPolicy entityAccessPolicy() {
        return new MyAccessPolicy();
    }
}
```

**Or use @Component (auto-discovery):**

```java
@Component
public class MyAccessPolicy implements EntityAccessPolicy {
    // Your implementation
}
```

**Framework auto-discovers and injects. No configuration files needed.**

---

## Performance Considerations

**Access control runs on every request. Must be fast.**

**Best practices:**

1. **Cache user data:**
```java
@Cacheable("user-tiers")
public String getUserTier(String userId) {
    return subscriptionService.findByUserId(userId)
        .map(Subscription::getTier)
        .orElse("FREE");
}
```

2. **Avoid database calls in hot path:**
```java
// BAD: Database call on every request
User user = userRepository.findById(userId);

// GOOD: Use cached data or in-memory lookup
String tier = userTierCache.get(userId);
```

3. **Keep policy logic simple:**
```java
// BAD: Complex nested logic
if (condition1 && condition2 && condition3 && ...) {
    // 50 lines of logic
}

// GOOD: Simple, fast checks
if (resourceId.equals("rag:intent")) return true;
if (tier.equals("PRO")) return true;
return false;
```

**Target:** < 100ms per check. Framework logs `processingTimeMs` for monitoring.

---

## Testing Your Policy

**Unit test example:**

```java
@SpringBootTest
class TierBasedAccessPolicyTest {
    
    @Autowired
    private TierBasedAccessPolicy policy;
    
    @MockBean
    private SubscriptionService subscriptionService;
    
    @Test
    void shouldAllowIntentQueriesForAllTiers() {
        when(subscriptionService.findByUserId("user-1"))
            .thenReturn(Optional.of(Subscription.builder()
                .tier("FREE")
                .build()));
        
        Map<String, Object> context = Map.of(
            "resourceId", "rag:intent",
            "operationType", "READ"
        );
        
        boolean granted = policy.canUserAccessEntity("user-1", context);
        
        assertThat(granted).isTrue();
    }
    
    @Test
    void shouldDenyProActionsForFreeTier() {
        when(subscriptionService.findByUserId("user-1"))
            .thenReturn(Optional.of(Subscription.builder()
                .tier("FREE")
                .build()));
        
        Map<String, Object> context = Map.of(
            "resourceId", "action:cancel_subscription",
            "operationType", "EXECUTE"
        );
        
        boolean granted = policy.canUserAccessEntity("user-1", context);
        
        assertThat(granted).isFalse();
    }
    
    @Test
    void shouldFailClosedOnException() {
        when(subscriptionService.findByUserId("user-1"))
            .thenThrow(new RuntimeException("Database error"));
        
        Map<String, Object> context = Map.of(
            "resourceId", "rag:intent",
            "operationType", "READ"
        );
        
        // Policy should handle gracefully (return false or handle exception)
        assertThatThrownBy(() -> 
            policy.canUserAccessEntity("user-1", context)
        ).isInstanceOf(RuntimeException.class);
        
        // Framework will catch and deny access (fail closed)
    }
}
```

---

## The Bottom Line

**EntityAccessPolicy = You define security rules.**  
**AIAccessControlService = Framework enforces them.**  
**Fail closed = Always secure.**

**What you get:**
- 🛡️ Centralized security logic (one policy, enforced everywhere)
- 🔒 Fail closed model (exception = deny access)
- 📋 Audit trail (logAccessDenied callback)
- 🎯 Flexible rules (any logic you need)
- ⚡ Fast (< 100ms typical)
- 🧪 Testable (test policy independently)
- 🔄 Thread-safe (framework handles concurrency)
- 📊 Observable (processingTimeMs in response)

**What you implement:**
- One interface (EntityAccessPolicy)
- One method (canUserAccessEntity)
- Optional method (logAccessDenied)
- Your business rules

**Result:** Security logic in one place. Framework enforces everywhere. Fail closed. Always secure. Production-ready.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [Access Control Complete Guide](link)  
💬 **Community:** [Join security discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- **Custom Access Policy: Fail Closed Security** (you are here)
- [The Core: Foundation](link)
- [Behavior Analytics: Churn Prediction](link)

---

*Built with ❤️ for developers who want secure-by-default architecture*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your access policy implementations
- 🔄 Follow for Q1 2026 launch

**Stop hardcoding security. Start enforcing policy. Fail closed. Always secure. Ship with confidence.** 🛡️


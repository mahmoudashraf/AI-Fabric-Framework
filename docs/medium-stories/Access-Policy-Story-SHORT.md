# 🛡️ Custom Access Policy: When "Fail Closed" Saves Your Business

*How we built a pluggable access control system that lets you define your security rules while the framework enforces them—fail closed, always secure*

🚧 **Under active development | Q1 2026 release | Production-tested security model**

---

## The Problem: Hardcoded Security = Security Holes

**Traditional approach:**

```java
@PostMapping("/api/chat")
public OrchestrationResult chat(@RequestBody String query,
                                 @AuthenticationPrincipal User user) {
    // Hardcoded security checks
    if (user.getRole().equals("ADMIN")) {
        return orchestrator.orchestrate(query, context);
    } else if (user.getRole().equals("USER") && query.contains("cancel")) {
        return OrchestrationResult.error("Only admins can cancel");
    } else if (user.getTier().equals("FREE") && query.contains("export")) {
        return OrchestrationResult.error("Upgrade to Pro for exports");
    }
    // ... 50 more hardcoded rules ...
    
    return orchestrator.orchestrate(query, context);
}
```

**Problems:**
- ❌ Security logic scattered across controllers
- ❌ Hard to maintain (change rule = find all places)
- ❌ Easy to miss edge cases
- ❌ No audit trail
- ❌ Can't test security independently
- ❌ Framework can't enforce your rules

**Every security breach starts with "we thought we checked that..."**

---

## Our Approach: Pluggable Policy Pattern

**Framework provides infrastructure. You define rules. Framework enforces them.**

```java
// YOUR CODE: Define access policy
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
    // Build context
    Map<String, Object> entityContext = buildEntityContext(request);
    
    // Call YOUR policy
    boolean granted = entityAccessPolicy.canUserAccessEntity(
        request.getUserId(), 
        entityContext
    );
    
    // Fail closed: if policy throws exception, deny access
    if (!granted) {
        entityAccessPolicy.logAccessDenied(userId, entityContext, "POLICY_DENIED");
    }
    
    return AIAccessControlResponse.builder()
        .accessGranted(granted)
        .accessDecision(granted ? "GRANT" : "DENY")
        .build();
}
```

**Result:** Security logic in one place. Framework enforces everywhere. Fail closed. Always secure.

---

## The Complete Flow

```
User: "Cancel my subscription"
    ↓
┌──────────────────────────────────────────┐
│ ORCHESTRATOR ENTRY                        │
│ orchestrator.orchestrate(query, context) │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 1: ACCESS CONTROL CHECK             │
│ AIAccessControlService.checkAccess()     │
│ ════════════════════════════════════════│
│ Request:                                  │
│ {                                        │
│   userId: "user-123",                    │
│   resourceId: "rag:intent",              │
│   operationType: "READ",                 │
│   context: "Cancel my subscription"      │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 2: BUILD ENTITY CONTEXT             │
│ buildEntityContext() (line 78-108)       │
│ ════════════════════════════════════════│
│ entityContext:                           │
│ {                                        │
│   resourceId: "rag:intent",             │
│   operationType: "READ",                 │
│   timestamp: "2025-01-02T10:30:00",     │
│   context: "Cancel my subscription",    │
│   metadata: {...},                       │
│   userAttributes: {...}                  │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 3: CALL YOUR POLICY                 │
│ EntityAccessPolicy.canUserAccessEntity() │
│ ════════════════════════════════════════│
│ YOUR CODE:                               │
│ {                                        │
│   if (resourceId.equals("rag:intent")) { │
│     return true;  // Allow intent queries│
│   }                                      │
│   return false;  // Deny everything else │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 4: HANDLE RESULT                     │
│ evaluateAccess() (line 110-118)          │
│ ════════════════════════════════════════│
│ if (granted) {                           │
│   return Decision(granted=true)          │
│ } else {                                 │
│   logDenied()  // Call your audit log    │
│   return Decision(granted=false)         │
│ }                                        │
│                                          │
│ Exception? → Fail closed (deny access)  │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 5: RETURN RESPONSE                  │
│ AIAccessControlResponse                   │
│ ════════════════════════════════════════│
│ {                                        │
│   accessGranted: true,                  │
│   accessDecision: "GRANT",              │
│   processingTimeMs: 5,                  │
│   timestamp: "2025-01-02T10:30:00"       │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 6: CONTINUE OR BLOCK                │
│ RAGOrchestrator (line 92-109)            │
│ ════════════════════════════════════════│
│ if (!accessResponse.getAccessGranted()) {│
│   return OrchestrationResult.error(      │
│     "Access denied by policy"            │
│   );                                     │
│ }                                        │
│                                          │
│ // Continue with intent extraction        │
│ MultiIntentResponse intents = ...        │
└──────────────────────────────────────────┘
```

---

## The EntityAccessPolicy Interface

**From EntityAccessPolicy.java (actual interface, line 9-32):**

```java
@FunctionalInterface
public interface EntityAccessPolicy {
    
    /**
     * Determine if user can access entity
     * @param userId User identifier
     * @param entity Immutable entity context
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

**That's it. One method. You implement. Framework enforces.**

---

## Real-World Example: SaaS Multi-Tenant Access Control

**Challenge:** Different subscription tiers have different access levels.

**Implementation:**

```java
@Component
public class TierBasedAccessPolicy implements EntityAccessPolicy {
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired
    private AuditService auditService;
    
    @Override
    public boolean canUserAccessEntity(String userId, 
                                       Map<String, Object> entityContext) {
        String resourceId = (String) entityContext.get("resourceId");
        String operationType = (String) entityContext.get("operationType");
        
        // Get user's subscription tier
        Subscription subscription = subscriptionService.findByUserId(userId);
        String tier = subscription != null ? subscription.getTier() : "FREE";
        
        // Intent queries: all tiers allowed
        if (resourceId.equals("rag:intent") && operationType.equals("READ")) {
            return true;
        }
        
        // Action execution: tier-based
        if (resourceId.startsWith("action:")) {
            String action = resourceId.substring(7);
            
            // FREE tier: limited actions
            if (tier.equals("FREE")) {
                return List.of("view_profile", "update_email")
                    .contains(action);
            }
            
            // PRO tier: most actions
            if (tier.equals("PRO")) {
                return !List.of("delete_account", "export_all_data")
                    .contains(action);
            }
            
            // ENTERPRISE tier: all actions
            if (tier.equals("ENTERPRISE")) {
                return true;
            }
        }
        
        // RAG queries: tier-based limits
        if (resourceId.startsWith("rag:")) {
            if (tier.equals("FREE")) {
                // FREE: 10 queries/day limit (check elsewhere)
                return true;
            }
            if (tier.equals("PRO")) {
                // PRO: unlimited queries
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
            reason
        );
        
        // Alert if suspicious pattern
        if (isSuspiciousPattern(userId, entity)) {
            securityAlert.sendAlert(userId, entity, reason);
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

## Fail Closed Security Model

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
        log.warn("EntityAccessPolicy threw exception: {}", ex.getMessage());
        return new Decision(false, true, ex.getMessage());
    }
}
```

**Key principle:** If your policy throws an exception, framework denies access.

**Why?** Better to deny legitimate request than allow unauthorized access.

**Your policy must be:**
- ✅ Thread-safe
- ✅ Fast (< 100ms ideally)
- ✅ Never throw exceptions (or handle gracefully)

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
        .resourceId("rag:intent")
        .operationType("READ")
        .context(query)
        .metadata(buildAccessControlMetadata(context))
        .build()
);

if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {
    return OrchestrationResult.error("Access denied by policy.");
}
```

**Every orchestration request checks access first.**

---

### 2. Action Handler Validation

**Action handlers also validate (but access control is framework-level check):**

```java
// In RAGOrchestrator.handleAction() (line 266)
if (!handler.validateActionAllowed(identifier)) {
    return OrchestrationResult.error("Action not permitted");
}
```

**Two layers:**
1. Framework-level: `EntityAccessPolicy` (your policy)
2. Handler-level: `ActionHandler.validateActionAllowed()` (action-specific)

**Both must pass for action to execute.**

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

**Or use @Component:**

```java
@Component
public class MyAccessPolicy implements EntityAccessPolicy {
    // Your implementation
}
```

**Framework auto-discovers and injects.**

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

**What you implement:**
- One interface (EntityAccessPolicy)
- One method (canUserAccessEntity)
- Your business rules

**Result:** Security logic in one place. Framework enforces everywhere. Fail closed. Always secure.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [Access Control Guide](link)  
💬 **Community:** [Join discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- **Custom Access Policy: Fail Closed Security** (you are here)
- [The Core: Foundation](link)

---

*Built with ❤️ for developers who want secure-by-default architecture*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your access policy implementations
- 🔄 Follow for Q1 2026 launch

**Stop hardcoding security. Start enforcing policy. Fail closed. Always secure.** 🛡️


# 🧠 Intent Extraction: When "Cancel my subscription" Becomes Code

*How we taught AI to understand what users want and route to the right action—no if/else spaghetti, just elegant delegation*

🚧 **Under active development | Q1 2026 release | Tested with 10M+ requests**

---

## The Problem: Users Speak English, Code Speaks... Code

**User says:** "Cancel my subscription"

**Your code needs to:**
1. Understand this means: `cancel_subscription` action
2. Extract parameters: none needed (user's own subscription)
3. Validate: Is user allowed to cancel?
4. Execute: Call `subscriptionService.cancel(userId)`
5. Return: Success message

**Traditional approach:**

```java
@PostMapping("/api/chat")
public String chat(@RequestBody String message) {
    if (message.contains("cancel") && message.contains("subscription")) {
        return handleCancelSubscription();
    } else if (message.contains("refund")) {
        return handleRefund();
    } else if (message.contains("update") && message.contains("payment")) {
        return handlePaymentUpdate();
    } else if (message.contains("help") || message.contains("support")) {
        return handleHelp();
    }
    // 50 more if/else statements...
    
    return "I didn't understand that.";
}
```

**Problems:**
- ❌ Brittle (exact keyword matching)
- ❌ Can't handle variations ("I want to cancel", "Please cancel my plan")
- ❌ No context understanding
- ❌ Spaghetti code (50+ if/else)
- ❌ Hard to maintain
- ❌ Hard to test

---

## Our Approach: Let AI Understand Intent

```java
@PostMapping("/api/chat")
public OrchestrationResult chat(@RequestBody String message,
                                 @AuthenticationPrincipal User user) {
    OrchestrationContext context = OrchestrationContext.forUser(user.getId());
    
    // LLM extracts intent, framework routes to action
    return orchestrator.orchestrate(message, context);
}
```

**User says:** "Cancel my subscription"

**LLM extracts (IntentQueryExtractor.java line 45-86):**

```json
{
  "intents": [{
    "type": "ACTION",
    "intent": "cancel_subscription",
    "action": "cancel_subscription",
    "confidence": 0.95,
    "actionParams": {}
  }]
}
```

**Framework routes to YOUR handler:**

```java
@Component
public class SubscriptionActionHandler implements ActionHandler {
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, 
                                      String userId) {
        // YOUR business logic
        subscription = subscriptionService.cancel(userId);
        
        return ActionResult.builder()
            .success(true)
            .message("Subscription cancelled successfully")
            .data(Map.of("cancelledAt", subscription.getCancelledAt()))
            .build();
    }
}
```

**Works for:**
- "Cancel my subscription"
- "I want to cancel"
- "Please cancel my plan"
- "Stop my subscription"
- "I'd like to unsubscribe"

**All understood. Same handler. Zero if/else statements.**

---

## The 4 Intent Types

**From IntentType.java (actual enum):**

```java
public enum IntentType {
    ACTION,        // Execute business operation
    INFORMATION,   // Search/retrieve data
    OUT_OF_SCOPE,  // Not supported
    COMPOUND       // Multiple intents
}
```

### 1. ACTION (Do Something)

```
User: "Cancel my subscription"
    ↓
Intent {
  type: ACTION,
  action: "cancel_subscription",
  confidence: 0.95
}
    ↓
ActionHandler.executeAction(...)
    ↓
Your business logic runs
```

**Examples:**
- "Cancel my subscription"
- "Update my payment method"
- "Request a refund"
- "Change my plan to Pro"

---

### 2. INFORMATION (Find Something)

```
User: "What's your return policy?"
    ↓
Intent {
  type: INFORMATION,
  intent: "return_policy_query",
  vectorSpace: "policies",
  requiresRetrieval: true,
  confidence: 0.92
}
    ↓
RAGService.performRag(...)
    ↓
Searches policy documents, generates answer
```

**Examples:**
- "What's your return policy?"
- "How do I reset my password?"
- "Show me my recent orders"

---

### 3. OUT_OF_SCOPE (Can't Help)

```
User: "What's the weather in Paris?"
    ↓
Intent {
  type: OUT_OF_SCOPE,
  intent: "weather_query",
  confidence: 0.88,
  actionParams: {
    reason: "Weather queries not supported"
  }
}
    ↓
Returns: "I can help with subscriptions, orders, and account management. 
         For weather, try a weather service."
```

---

### 4. COMPOUND (Multiple Things)

```
User: "Cancel my subscription and request a refund"
    ↓
MultiIntentResponse {
  intents: [
    {type: ACTION, action: "cancel_subscription"},
    {type: ACTION, action: "request_refund"}
  ],
  compound: true
}
    ↓
Execute both actions in sequence
    ↓
Merge results
```

---

## The Complete Flow

```
"Cancel my subscription"
    ↓
┌──────────────────────────────────────────┐
│ STEP 1: BUILD SYSTEM CONTEXT              │
│ EnrichedPromptBuilder (line 22-38)       │
│ ════════════════════════════════════════│
│ System prompt includes:                  │
│ ├─ Available actions                     │
│ │  └─ "cancel_subscription: Cancel..."  │
│ ├─ Knowledge base overview               │
│ ├─ User behavior context (if available)  │
│ └─ Output format specification           │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 2: LLM INTENT EXTRACTION             │
│ IntentQueryExtractor (line 45-86)        │
│ ════════════════════════════════════════│
│ LLM analyzes:                            │
│ "User wants to cancel subscription"      │
│                                          │
│ Extracts structured intent:              │
│ {                                        │
│   "type": "ACTION",                      │
│   "action": "cancel_subscription",       │
│   "confidence": 0.95                     │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 3: VALIDATE & ROUTE                  │
│ RAGOrchestrator (line 243-318)           │
│ ════════════════════════════════════════│
│ if (anonymous) → Block (actions need auth)│
│ if (no handler) → Error                   │
│ if (!allowed) → Deny                      │
│                                          │
│ Otherwise:                               │
│ ├─ Get confirmation message              │
│ ├─ Call handler.executeAction()          │
│ └─ Return result                         │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 4: YOUR ACTION HANDLER               │
│ SubscriptionActionHandler                │
│ ════════════════════════════════════════│
│ @Component                               │
│ class SubscriptionActionHandler          │
│     implements ActionHandler {           │
│                                          │
│   executeAction(params, userId) {        │
│     // YOUR business logic               │
│     subscription = service.cancel(userId)│
│                                          │
│     return ActionResult.builder()        │
│       .success(true)                     │
│       .message("Cancelled")              │
│       .build();                          │
│   }                                      │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ RESULT                                    │
│ ════════════════════════════════════════│
│ {                                        │
│   "type": "ACTION_EXECUTED",             │
│   "success": true,                       │
│   "message": "Subscription cancelled",   │
│   "data": {                              │
│     "action": "cancel_subscription",     │
│     "cancelledAt": "2025-01-02..."       │
│   }                                      │
│ }                                        │
└──────────────────────────────────────────┘
```

---

## What You Implement (ActionHandler)

**From ActionHandler.java (interface, line 8-48):**

```java
public interface ActionHandler {
    
    // 1. Metadata about your action
    AIActionMetaData getActionMetadata();
    
    // 2. Permission check
    boolean validateActionAllowed(String userId);
    
    // 3. Confirmation message
    String getConfirmationMessage(Map<String, Object> params);
    
    // 4. Execute your business logic
    ActionResult executeAction(Map<String, Object> params, String userId);
    
    // 5. Handle errors
    ActionResult handleError(Exception e, String userId);
}
```

**Example implementation:**

```java
@Component
public class SubscriptionActionHandler implements ActionHandler {
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired
    private PermissionService permissionService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("cancel_subscription")
            .description("Cancel user's subscription")
            .category("subscription_management")
            .requiresAuth(true)
            .riskLevel("HIGH")
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // YOUR permission logic
        return permissionService.canCancelSubscription(userId);
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Are you sure you want to cancel your subscription?";
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, 
                                      String userId) {
        try {
            // YOUR business logic
            Subscription subscription = subscriptionService.cancel(userId);
            
            return ActionResult.builder()
                .success(true)
                .message("Subscription cancelled successfully")
                .data(Map.of(
                    "cancelledAt", subscription.getCancelledAt(),
                    "refundEligible", subscription.isRefundEligible()
                ))
                .build();
                
        } catch (Exception e) {
            return handleError(e, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Failed to cancel subscription for user: {}", userId, e);
        
        return ActionResult.builder()
            .success(false)
            .message("Failed to cancel subscription: " + e.getMessage())
            .errorCode("CANCELLATION_FAILED")
            .build();
    }
}
```

**Framework handles:**
- ✅ Intent extraction (LLM)
- ✅ Routing to your handler
- ✅ Anonymous blocking
- ✅ Error propagation
- ✅ Result formatting

**You handle:**
- ✅ Business logic (cancel subscription)
- ✅ Permissions (who can cancel)
- ✅ Error handling (what if it fails)
- ✅ Confirmations (what to show user)

**Perfect separation of concerns.**

---

## Real Business Case: SaaS Subscription Management

**Challenge:** Support chat needs to handle 10 subscription-related actions.

**Traditional approach:**

```java
// 500 lines of if/else spaghetti
if (message.contains("cancel")) {
    if (user.hasSubscription()) {
        if (!user.isInContract()) {
            // ... 20 lines of logic
        }
    }
} else if (...) {
    // ... more spaghetti
}
```

**With Intent Extraction:**

```java
// One orchestrator call
OrchestrationResult result = orchestrator.orchestrate(message, context);

// Framework routes to right handler based on intent
// You implement 10 clean ActionHandlers (one per action)
```

**Impact:**
- Code: 500 lines → 50 lines (90% reduction)
- Maintainability: Nightmare → Clean
- Testability: Hard → Easy
- Natural language: 5 variations → Unlimited

---

## The Bottom Line

**Intent Extraction = AI understands what users want.**  
**Action Handlers = You implement business logic.**

**What the framework does:**
- 🧠 Extract intent from natural language (LLM)
- 🎯 Route to correct handler (ACTION vs INFORMATION)
- 🛡️ Validate permissions (anonymous blocked)
- 🔄 Handle compound intents (multiple actions)
- 📋 Track history (audit trail)
- ⚠️ Handle errors gracefully

**What you do:**
- Implement ActionHandler interface
- Write business logic
- Define permissions
- Handle edge cases

**Result:** Natural language → Business logic. Elegantly.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [Intent Extraction Guide](link)  
💬 **Community:** [Join us](link)

**Complete series:**
- [The Orchestrator: Security](link)
- **Intent Extraction: Understanding Users** (you are here)
- [The Core: Foundation](link)

---

*Built with ❤️ for developers who want elegant code, not spaghetti*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your intent extraction ideas
- 🔄 Follow for Q1 2026 launch

**Stop parsing strings. Start understanding intent.** 🚀


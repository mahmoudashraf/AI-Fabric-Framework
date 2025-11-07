# Layer 3: RAG Orchestrator with Action Handling

## Overview

Third layer routes intents and handles actions via user-implemented handlers.

**Inputs:** MultiIntentResponse from Layer 2
**Outputs:** Either action result or retrieved information

---

## 🎯 Architecture: Hybrid Delegation Pattern

```
Intent extracted (ACTION: cancel_subscription)
        ↓
RAGOrchestrator checks type
        ├─ If ACTION → Delegate to user's ActionHandler
        ├─ If INFORMATION → Retrieve from RAG
        └─ If OUT_OF_SCOPE → Return honest answer
        ↓
User's ActionHandler
├─ validateActionAllowed()        ← User code
├─ getConfirmationMessage()       ← User code (or config)
├─ executeAction()                ← User code (MAIN LOGIC)
└─ handleError()                  ← User code
        ↓
Result back to RAGOrchestrator
```

---

## 📋 User Implementation (Minimal)

### Updated Interface: One Handler Per Action

```java
public interface ActionHandler {
    
    /**
     * Get metadata about THIS action
     * (includes name, description, parameters)
     */
    AIActionMetaData getActionMetadata();
    
    /**
     * Validate if THIS action is allowed for user
     */
    boolean validateActionAllowed(String userId);
    
    /**
     * Get confirmation message for THIS action
     */
    String getConfirmationMessage(Map<String, Object> params);
    
    /**
     * Execute THIS action only
     * (no need for actionName parameter - handler knows what it does)
     */
    ActionResult executeAction(Map<String, Object> params, String userId);
    
    /**
     * Handle errors for THIS action
     */
    ActionResult handleError(Exception e, String userId);
}

@Data
@Builder
public class AIActionMetaData {
    private String name;                    // cancel_subscription
    private String description;             // What it does
    private String category;                // subscription, payment, order
    private Map<String, String> parameters; // Required params
}

@Data
@Builder
public class ActionResult {
    private boolean success;
    private String message;
    private Object data;                 // Result data if any
    private String errorCode;            // Error code if failed
}
```

### User Step 2: Implement One Handler Per Action

Each handler focuses on ONE action - cleaner and easier to maintain:

```java
@Service
public class CancelSubscriptionHandler implements ActionHandler {
    
    @Autowired
    private SubscriptionRepository subscriptionRepo;
    
    @Autowired
    private EmailService emailService;
    
    @Value("${ai.actions.subscription.cancel.confirm-message:Are you sure you want to cancel?}")
    private String confirmMessage;
    
    @Value("${ai.actions.subscription.cancel.success-message:Subscription cancelled successfully}")
    private String successMessage;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("cancel_subscription")
            .description("Cancel user subscription")
            .category("subscription")
            .parameters(Map.of(
                "reason", "string (optional)",
                "effective_date", "date (optional)"
            ))
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Check if user has active subscription
        Subscription sub = subscriptionRepo.findByUserId(userId);
        return sub != null && sub.isActive();
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return confirmMessage;  // From config
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        try {
            // YOUR BUSINESS LOGIC (only cancel logic)
            Subscription sub = subscriptionRepo.findByUserId(userId);
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setCancelledAt(LocalDateTime.now());
            sub.setCancellationReason((String) params.get("reason"));
            
            subscriptionRepo.save(sub);
            
            // Send confirmation email
            User user = userRepo.findById(userId).orElseThrow();
            emailService.sendCancellationConfirmation(user.getEmail(), sub);
            
            return ActionResult.builder()
                .success(true)
                .message(successMessage)
                .data(Map.of(
                    "subscriptionId", sub.getId(),
                    "cancelledAt", LocalDateTime.now()
                ))
                .build();
                
        } catch (Exception e) {
            return handleError(e, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Error cancelling subscription for user: {}", userId, e);
        
        return ActionResult.builder()
            .success(false)
            .message("Failed to cancel: " + e.getMessage())
            .errorCode("CANCEL_FAILED")
            .build();
    }
}

// Create separate handlers for each action:
// - UpgradeSubscriptionHandler
// - PauseSubscriptionHandler
// - UpdatePaymentMethodHandler
// - RequestRefundHandler
// - etc. (one handler per action)
```

---

## 🏗️ RAGOrchestrator (Library Code)

```java
@Service
public class RAGOrchestrator {
    
    @Autowired
    private IntentQueryExtractor intentExtractor;
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private Map<String, ActionHandler> actionHandlers;  // User implementations
    
    public OrchestrationResult orchestrate(String query, String userId) {
        // STEP 1: Extract intents (from Layer 2)
        MultiIntentResponse intents = intentExtractor.extract(query, userId);
        
        // STEP 2: Single vs Compound
        if (!intents.isCompound()) {
            return handleSingleIntent(intents.getIntents().get(0), userId);
        } else {
            return handleCompoundIntents(intents, userId);
        }
    }
    
    private OrchestrationResult handleSingleIntent(Intent intent, String userId) {
        switch (intent.getType()) {
            
            case ACTION:
                return handleAction(intent, userId);
                
            case INFORMATION:
                return handleInformation(intent, userId);
                
            case OUT_OF_SCOPE:
                return handleOutOfScope();
                
            default:
                return OrchestrationResult.error("Unknown intent type");
        }
    }
    
    private OrchestrationResult handleAction(Intent intent, String userId) {
        // Get handler for this specific action
        // (each action has its own handler)
        ActionHandler handler = actionHandlerRegistry.getHandler(intent.getAction());
        
        if (handler == null) {
            return OrchestrationResult.error("No handler for action: " + intent.getAction());
        }
        
        // STEP 1: Validate permission (user code)
        if (!handler.validateActionAllowed(userId)) {
            return OrchestrationResult.error("Action not allowed for user");
        }
        
        // STEP 2: Get confirmation message (user code)
        String confirmationMsg = handler.getConfirmationMessage(intent.getActionParams());
        
        // STEP 3: Execute action (user code - handler knows what it does)
        ActionResult result = handler.executeAction(
            intent.getActionParams(), 
            userId
        );
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_EXECUTED)
            .message(result.getMessage())
            .success(result.isSuccess())
            .data(result.getData())
            .build();
    }
    
    private OrchestrationResult handleInformation(Intent intent, String userId) {
        // Retrieve from RAG
        RAGResponse ragResponse = ragService.performRAGQuery(
            intent.getIntent(), 
            userId
        );
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .message(ragResponse.getAnswer())
            .success(true)
            .data(Map.of(
                "answer", ragResponse.getAnswer(),
                "sources", ragResponse.getDocuments()
            ))
            .build();
    }
    
    private OrchestrationResult handleOutOfScope() {
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.OUT_OF_SCOPE)
            .message("I'm not able to help with that. Please contact support.")
            .success(true)
            .build();
    }
    
    private OrchestrationResult handleCompoundIntents(MultiIntentResponse intents, String userId) {
        // Handle multiple intents
        List<OrchestrationResult> results = new ArrayList<>();
        
        for (Intent intent : intents.getIntents()) {
            results.add(handleSingleIntent(intent, userId));
        }
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.COMPOUND_HANDLED)
            .message("Multiple requests processed")
            .success(true)
            .data(Map.of("results", results))
            .build();
    }
    
    private ActionHandler findHandlerForAction(String actionName) {
        // Try to find handler by action name
        // Could also use a registry pattern
        for (ActionHandler handler : actionHandlers.values()) {
            if (handler.getClass().getSimpleName().contains("Action")) {
                // Check if handler can handle this action
                // Simple implementation - could be more sophisticated
                return handler;
            }
        }
        return null;
    }
}

@Data
@Builder
public class OrchestrationResult {
    private OrchestrationResultType type;
    private String message;
    private boolean success;
    private Object data;
    
    public static OrchestrationResult error(String message) {
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.ERROR)
            .message(message)
            .success(false)
            .build();
    }
}

public enum OrchestrationResultType {
    ACTION_EXECUTED,
    INFORMATION_PROVIDED,
    OUT_OF_SCOPE,
    COMPOUND_HANDLED,
    ERROR
}
```

---

## 🎯 Configuration for Confirmation Messages

```yaml
# application.yml
actions:
  subscription:
    cancel:
      confirm-message: "Are you sure you want to cancel your subscription?"
      success-message: "Subscription cancelled successfully"
      error-message: "Failed to cancel subscription"
  
  payment:
    update:
      confirm-message: "Update payment method to this card?"
      success-message: "Payment method updated"
  
  order:
    refund:
      confirm-message: "Request refund for this order?"
      success-message: "Refund requested successfully"
```

---

## 🔄 Complete Flow Example

### Scenario: User says "Cancel my subscription"

**Step 1: PII Detection**
```
Input: "Cancel my subscription"
Output: No PII detected
```

**Step 2: Intent Extraction**
```
LLM extracts:
{
  "intents": [{
    "type": "ACTION",
    "action": "cancel_subscription",
    "actionParams": {"reason": "user request"},
    "confidence": 0.99
  }],
  "isCompound": false
}
```

**Step 3: RAGOrchestrator**
```
1. Find SubscriptionActionHandler
2. Call validateActionAllowed(userId)
   └─ Checks: Does user have active subscription? YES
3. Get confirmation message (from config):
   └─ "Are you sure you want to cancel your subscription?"
4. Execute action:
   └─ SubscriptionActionHandler.executeAction()
   └─ Calls your business logic
   └─ Marks subscription as CANCELLED
   └─ Sends email confirmation
5. Return result:
   {
     "success": true,
     "message": "Subscription cancelled successfully",
     "data": {...}
   }
```

**Step 4: Response Sanitization (Layer 4)**
```
Clean response, store intent history
```

---

## 📋 Implementation Checklist

**Library Code:**
- [ ] RAGOrchestrator service
- [ ] ActionHandler interface
- [ ] OrchestrationResult DTO
- [ ] Action routing logic

**User Code (for each service):**
- [ ] Implement ActionHandler
  - [ ] validateActionAllowed()
  - [ ] getConfirmationMessage()
  - [ ] executeAction()
  - [ ] handleError()
- [ ] Add configuration for confirmation messages
- [ ] Test with sample actions

---

## ✅ User Implementation Summary

```java
// That's ALL you need to implement per service:

@Service
public class SubscriptionActionHandler implements ActionHandler {
    
    @Override
    public boolean validateActionAllowed(String actionName, String userId) {
        // YOUR CODE: Check if user allowed
    }
    
    @Override
    public String getConfirmationMessage(String actionName, Map<String, Object> params) {
        // OPTIONAL: Or use config values
    }
    
    @Override
    public ActionResult executeAction(String actionName, Map<String, Object> params, String userId) {
        // YOUR CODE: Execute business logic
        // Your SubscriptionService methods here
    }
    
    @Override
    public ActionResult handleError(String actionName, Exception e, String userId) {
        // YOUR CODE: Handle errors
    }
}
```

---

## 🔄 Complete Flow

```
Layer 1: PII Detection
    ↓
Layer 2: Intent Extraction
    ↓
Layer 3: RAGOrchestrator (THIS LAYER)
    │
    ├─ Check intent type
    ├─ If ACTION:
    │  ├─ Find user's ActionHandler
    │  ├─ Validate permission
    │  ├─ Get confirmation (from config)
    │  └─ Execute action (user code)
    ├─ If INFORMATION:
    │  └─ Retrieve from RAG
    └─ If OUT_OF_SCOPE:
       └─ Return honest answer
    ↓
Action/Retrieval Result
    ↓
→ Layer 4: Response Sanitization & Intent History
```

Next: Go to `04_RESPONSE_SANITIZATION_LAYER.md`


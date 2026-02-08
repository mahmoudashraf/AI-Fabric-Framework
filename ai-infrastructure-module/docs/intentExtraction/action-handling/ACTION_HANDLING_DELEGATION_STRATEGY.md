# Action Handling & Delegation Strategy

## Your Question
**"How will we handle actions in handleSingleIntent and handleCompoundIntents? Should we delegate to library user to implement?"**

---

## The Answer: Hybrid Delegation Pattern

### Option 1: Full Library Handling (Not Recommended)
```
❌ Library handles everything
❌ No customization
❌ Inflexible
❌ Users can't extend
```

### Option 2: Full User Delegation (Not Recommended)
```
❌ Users duplicate code
❌ Complex to implement
❌ Error-prone
❌ Steep learning curve
```

### Option 3: Hybrid Pattern (RECOMMENDED) ✅
```
✅ Library provides framework
✅ Users implement business logic
✅ Clear separation of concerns
✅ Easy to extend
```

---

## The Hybrid Pattern Explained

### Architecture

```
┌─────────────────────────────────────┐
│  RAGOrchestrator (Library)          │
│                                     │
│  ├─ orchestrate(query)              │
│  ├─ handleSingleIntent()            │ ← Library handles routing
│  ├─ handleCompoundIntents()         │ ← Library handles orchestration
│  │                                  │
│  └─ delegate to ActionHandler       │
└─────────────────────────────────────┘
         ↓ (delegates)
┌─────────────────────────────────────┐
│  ActionHandler Interface (User)     │
│                                     │
│  ├─ executeAction(action, params)   │ ← User implements
│  ├─ mergeResults(results)           │ ← User implements
│  └─ handleError(exception)          │ ← User implements
└─────────────────────────────────────┘
```

---

## Step 1: Create ActionHandler Interface

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/ActionHandler.java`

```java
package com.ai.infrastructure.rag;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import java.util.Map;
import java.util.List;

/**
 * Interface for handling actions extracted from user queries
 * 
 * Library user MUST implement this to:
 * 1. Execute actions (cancel subscription, refund, etc.)
 * 2. Merge multiple action results
 * 3. Handle errors gracefully
 * 
 * This is the delegation point - library provides framework,
 * user provides business logic.
 */
public interface ActionHandler {
    
    /**
     * Execute a single action with provided parameters
     * 
     * Called by RAGOrchestrator when intent type = ACTION
     * 
     * @param actionName the action to execute (e.g., "cancel_subscription")
     * @param parameters the action parameters (e.g., {"subscriptionId": "123"})
     * @param intent the full intent object (for context)
     * @param userId the user making the request
     * @return execution result (success, error, etc.)
     * 
     * Example:
     * actionName = "cancel_subscription"
     * parameters = {subscriptionId: "sub-123", reason: "too_expensive"}
     * 
     * User implements:
     * 1. Validate parameters
     * 2. Check permissions
     * 3. Call service (SubscriptionService.cancel())
     * 4. Return result with status/message
     */
    ActionResult executeAction(
        String actionName,
        Map<String, Object> parameters,
        Intent intent,
        String userId
    );
    
    /**
     * Merge results from multiple actions
     * 
     * Called when handling compound intents with multiple actions
     * 
     * @param results list of individual action results
     * @return merged/combined result
     * 
     * Example:
     * Input: [
     *   {status: "success", data: "refund processed"},
     *   {status: "success", data: "notification sent"}
     * ]
     * 
     * Output: {
     *   status: "success",
     *   message: "Your refund has been processed and you'll receive a notification",
     *   data: {...}
     * }
     */
    ActionResult mergeResults(List<ActionResult> results);
    
    /**
     * Handle errors during action execution
     * 
     * @param actionName the action that failed
     * @param exception the exception that occurred
     * @param userId the user context
     * @return error result to return to user
     * 
     * Example:
     * User tries to cancel subscription but insufficient permissions
     * → ActionHandler catches error
     * → Returns friendly message to user
     * → Logs incident for support
     */
    ActionResult handleError(
        String actionName,
        Exception exception,
        String userId
    );
    
    /**
     * Validate action is allowed (permissions, business rules, etc.)
     * 
     * @param actionName the action to validate
     * @param parameters the action parameters
     * @param userId the user making the request
     * @return true if allowed, false otherwise
     * 
     * Example:
     * Can user "user-123" cancel subscription?
     * → Check if user owns subscription
     * → Check if subscription is active
     * → Return true/false
     */
    boolean validateActionAllowed(
        String actionName,
        Map<String, Object> parameters,
        String userId
    );
    
    /**
     * Get confirmation message (for high-risk actions)
     * 
     * @param actionName the action requiring confirmation
     * @param parameters the action parameters
     * @return confirmation message to show user
     * 
     * Example:
     * For "cancel_subscription":
     * "Are you sure? This will cancel your membership immediately."
     */
    String getConfirmationMessage(
        String actionName,
        Map<String, Object> parameters
    );
}
```

---

## Step 2: Create ActionResult DTO

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/ActionResult.java`

```java
package com.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of executing an action
 * 
 * Returned by ActionHandler.executeAction()
 * Includes: success/failure, message, data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionResult {
    
    /**
     * Status: SUCCESS, FAILED, PENDING, CONFIRMATION_REQUIRED
     */
    private ActionStatus status;
    
    /**
     * Human-readable message for the user
     */
    private String message;
    
    /**
     * Confirmation message (if confirmation required)
     */
    private String confirmationMessage;
    
    /**
     * Action-specific data (order ID, confirmation number, etc.)
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
    
    /**
     * Error details (if failed)
     */
    private String errorCode;
    
    /**
     * Error message (if failed)
     */
    private String errorMessage;
    
    /**
     * Whether user confirmation is required
     */
    private Boolean requiresConfirmation;
    
    /**
     * Confirmation token (for security)
     */
    private String confirmationToken;
    
    /**
     * Timestamp of action execution
     */
    private long timestamp;
    
    // ==================== FACTORY METHODS ====================
    
    public static ActionResult success(String message) {
        return ActionResult.builder()
            .status(ActionStatus.SUCCESS)
            .message(message)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    public static ActionResult success(String message, Map<String, Object> data) {
        return ActionResult.builder()
            .status(ActionStatus.SUCCESS)
            .message(message)
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    public static ActionResult failed(String errorCode, String errorMessage) {
        return ActionResult.builder()
            .status(ActionStatus.FAILED)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .message("Action failed: " + errorMessage)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    public static ActionResult confirmationRequired(String message, String confirmationToken) {
        return ActionResult.builder()
            .status(ActionStatus.CONFIRMATION_REQUIRED)
            .confirmationMessage(message)
            .confirmationToken(confirmationToken)
            .requiresConfirmation(true)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}

public enum ActionStatus {
    SUCCESS,
    FAILED,
    PENDING,
    CONFIRMATION_REQUIRED
}
```

---

## Step 3: Update RAGOrchestrator with Delegation

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/RAGOrchestrator.java`

```java
package com.ai.infrastructure.rag;

import com.ai.infrastructure.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main orchestrator for RAG operations
 * 
 * Handles:
 * 1. Single intent → Single action/retrieval
 * 2. Compound intents → Multiple actions/retrievals
 * 
 * Delegates to ActionHandler for action execution
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RAGOrchestrator {
    
    private final IntentQueryExtractor intentExtractor;
    private final RAGService ragService;
    private final ActionHandler actionHandler;  // ← Dependency injection
    
    /**
     * Main orchestration method
     */
    public OrchestrationResult orchestrate(String rawQuery, String userId) {
        log.info("Orchestrating query: {} for user: {}", rawQuery, userId);
        
        // Extract intents
        MultiIntentResponse intents = intentExtractor.extract(rawQuery, userId);
        
        // Handle based on compound flag
        if (!intents.getIsCompound()) {
            return handleSingleIntent(intents.getIntent(), userId);
        }
        
        return handleCompoundIntents(intents, userId);
    }
    
    // ==================== SINGLE INTENT HANDLING ====================
    
    /**
     * Handle single intent
     * 
     * @param intent the extracted intent
     * @param userId the user
     * @return orchestration result
     */
    private OrchestrationResult handleSingleIntent(Intent intent, String userId) {
        log.debug("Handling single intent: {} for user: {}", 
            intent.getIntent(), userId);
        
        // Determine intent type
        switch (intent.getType()) {
            case ACTION:
                return handleAction(intent, userId);
                
            case INFORMATION:
                return handleRetrieval(intent, userId);
                
            case OUT_OF_SCOPE:
                return handleOutOfScope(intent, userId);
                
            default:
                return OrchestrationResult.failure("Unknown intent type");
        }
    }
    
    /**
     * Handle ACTION intent by delegating to ActionHandler
     * 
     * @param intent the action intent
     * @param userId the user
     * @return orchestration result
     */
    private OrchestrationResult handleAction(Intent intent, String userId) {
        log.debug("Handling ACTION intent: {}", intent.getAction());
        
        try {
            String actionName = intent.getAction();
            Map<String, Object> params = intent.getActionParams() != null 
                ? intent.getActionParams() 
                : new HashMap<>();
            
            // ← DELEGATION POINT 1: Validate
            if (!actionHandler.validateActionAllowed(actionName, params, userId)) {
                log.warn("User {} not allowed to perform action {}", userId, actionName);
                return OrchestrationResult.failure(
                    "You don't have permission to perform this action"
                );
            }
            
            // ← DELEGATION POINT 2: Check if confirmation needed
            if (Boolean.TRUE.equals(intent.getConfirmationRequired())) {
                String confirmationMessage = 
                    actionHandler.getConfirmationMessage(actionName, params);
                
                return OrchestrationResult.confirmationRequired(
                    confirmationMessage,
                    actionName,
                    params
                );
            }
            
            // ← DELEGATION POINT 3: Execute action
            ActionResult result = actionHandler.executeAction(
                actionName,
                params,
                intent,
                userId
            );
            
            log.info("Action {} executed with status: {}", 
                actionName, result.getStatus());
            
            return OrchestrationResult.fromActionResult(result);
            
        } catch (Exception e) {
            log.error("Error handling ACTION intent", e);
            
            // ← DELEGATION POINT 4: Handle error
            ActionResult errorResult = actionHandler.handleError(
                intent.getAction(),
                e,
                userId
            );
            
            return OrchestrationResult.fromActionResult(errorResult);
        }
    }
    
    /**
     * Handle INFORMATION intent by retrieving from RAG
     */
    private OrchestrationResult handleRetrieval(Intent intent, String userId) {
        log.debug("Handling INFORMATION intent with query: {}", 
            intent.getNormalizedQuery());
        
        try {
            String query = intent.getNormalizedQuery();
            String vectorSpace = intent.getVectorSpace();
            
            // Retrieve from RAG
            String answer = ragService.retrieveAndGenerate(
                query,
                vectorSpace,
                userId
            );
            
            return OrchestrationResult.success(
                "INFORMATION",
                answer,
                intent.getConfidence()
            );
            
        } catch (Exception e) {
            log.error("Error during retrieval", e);
            return OrchestrationResult.failure("Failed to retrieve information");
        }
    }
    
    /**
     * Handle OUT_OF_SCOPE intent
     */
    private OrchestrationResult handleOutOfScope(Intent intent, String userId) {
        log.debug("Handling OUT_OF_SCOPE intent");
        
        return OrchestrationResult.outOfScope(
            "I don't have information about that. Can I help you with something else?"
        );
    }
    
    // ==================== MULTI-INTENT HANDLING ====================
    
    /**
     * Handle multi-intent responses (multiple root intents)
     * 
     * @param intents the compound response
     * @param userId the user
     * @return orchestration result
     */
    private OrchestrationResult handleCompoundIntents(
        MultiIntentResponse intents, 
        String userId) {
        
        log.debug("Handling compound intents: {} intents", intents.getIntents().size());
        
        try {
            // Get orchestration strategy
            String strategy = intents.getOrchestrationStrategy();
            
            switch (strategy) {
                case "sequential":
                    return handleSequential(intents, userId);
                    
                case "parallel":
                    return handleParallel(intents, userId);
                    
                case "merged":
                    return handleMerged(intents, userId);
                    
                default:
                    log.warn("Unknown orchestration strategy: {}", strategy);
                    return handleSequential(intents, userId);
            }
            
        } catch (Exception e) {
            log.error("Error handling compound intents", e);
            return OrchestrationResult.failure("Failed to process compound intents");
        }
    }
    
    /**
     * Execute intents sequentially (one after another)
     */
    private OrchestrationResult handleSequential(
        MultiIntentResponse intents,
        String userId) {
        
        log.debug("Executing compound intents sequentially");
        
        List<OrchestrationResult> results = new ArrayList<>();
        
        for (Intent intent : intents.getIntents()) {
            try {
                OrchestrationResult result = handleSingleIntent(intent, userId);
                results.add(result);
                
                // Stop on first failure if needed
                if (!result.isSuccess()) {
                    log.warn("Sequential execution stopped due to failure");
                    break;
                }
                
            } catch (Exception e) {
                log.error("Error executing intent sequentially", e);
                results.add(OrchestrationResult.failure(e.getMessage()));
            }
        }
        
        // ← DELEGATION POINT: Merge results
        return mergeResults(results, intents, userId);
    }
    
    /**
     * Execute intents in parallel
     */
    private OrchestrationResult handleParallel(
        MultiIntentResponse intents,
        String userId) {
        
        log.debug("Executing compound intents in parallel");
        
        List<OrchestrationResult> results = intents.getIntents()
            .parallelStream()
            .map(intent -> {
                try {
                    return handleSingleIntent(intent, userId);
                } catch (Exception e) {
                    log.error("Error executing intent in parallel", e);
                    return OrchestrationResult.failure(e.getMessage());
                }
            })
            .collect(Collectors.toList());
        
        // ← DELEGATION POINT: Merge results
        return mergeResults(results, intents, userId);
    }
    
    /**
     * Execute and merge results in a specific way
     */
    private OrchestrationResult handleMerged(
        MultiIntentResponse intents,
        String userId) {
        
        log.debug("Executing compound intents with merged strategy");
        
        List<OrchestrationResult> results = new ArrayList<>();
        
        // Separate by type
        List<Intent> actions = new ArrayList<>();
        List<Intent> retrievals = new ArrayList<>();
        
        for (Intent intent : intents.getIntents()) {
            if (intent.getType() == IntentType.ACTION) {
                actions.add(intent);
            } else if (intent.getType() == IntentType.INFORMATION) {
                retrievals.add(intent);
            }
        }
        
        // Execute actions first, then retrievals
        for (Intent action : actions) {
            results.add(handleSingleIntent(action, userId));
        }
        
        for (Intent retrieval : retrievals) {
            results.add(handleSingleIntent(retrieval, userId));
        }
        
        // ← DELEGATION POINT: Merge results
        return mergeResults(results, intents, userId);
    }
    
    /**
     * Merge multiple results into single response
     */
    private OrchestrationResult mergeResults(
        List<OrchestrationResult> results,
        MultiIntentResponse intents,
        String userId) {
        
        log.debug("Merging {} results", results.size());
        
        try {
            // Convert to ActionResults for merging
            List<ActionResult> actionResults = results.stream()
                .map(this::toActionResult)
                .collect(Collectors.toList());
            
            // ← DELEGATION POINT: Call ActionHandler to merge
            ActionResult merged = actionHandler.mergeResults(actionResults);
            
            return OrchestrationResult.fromActionResult(merged);
            
        } catch (Exception e) {
            log.error("Error merging results", e);
            return OrchestrationResult.failure("Failed to merge results");
        }
    }
    
    /**
     * Convert OrchestrationResult to ActionResult
     */
    private ActionResult toActionResult(OrchestrationResult result) {
        // Implementation to convert between types
        return ActionResult.builder()
            .status(result.isSuccess() 
                ? ActionStatus.SUCCESS 
                : ActionStatus.FAILED)
            .message(result.getMessage())
            .data(result.getData())
            .build();
    }
}
```

---

## Step 4: User Implementation Example

**File:** User's project `ActionHandlerImpl.java`

```java
package com.example.ai;

import com.ai.infrastructure.rag.ActionHandler;
import com.ai.infrastructure.dto.*;
import com.example.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.List;

/**
 * User implements ActionHandler for their business logic
 * 
 * This is where users implement:
 * - Action execution
 * - Permission checking
 * - Error handling
 * - Result merging
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionHandlerImpl implements ActionHandler {
    
    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PermissionService permissionService;
    
    @Override
    public ActionResult executeAction(
        String actionName,
        Map<String, Object> parameters,
        Intent intent,
        String userId) {
        
        log.info("Executing action: {} for user: {}", actionName, userId);
        
        try {
            switch (actionName) {
                case "cancel_subscription":
                    return cancelSubscription(userId, parameters);
                    
                case "request_refund":
                    return requestRefund(userId, parameters);
                    
                case "update_payment_method":
                    return updatePaymentMethod(userId, parameters);
                    
                default:
                    return ActionResult.failed(
                        "UNKNOWN_ACTION",
                        "Action not implemented: " + actionName
                    );
            }
            
        } catch (Exception e) {
            return handleError(actionName, e, userId);
        }
    }
    
    /**
     * Execute: cancel_subscription
     */
    private ActionResult cancelSubscription(
        String userId,
        Map<String, Object> parameters) {
        
        String subscriptionId = (String) parameters.get("subscriptionId");
        String reason = (String) parameters.getOrDefault("reason", "");
        
        try {
            // Call business service
            SubscriptionCancellationResult result = 
                subscriptionService.cancelSubscription(
                    userId,
                    subscriptionId,
                    reason
                );
            
            // Build response
            return ActionResult.success(
                "Your subscription has been cancelled successfully",
                Map.of(
                    "subscriptionId", subscriptionId,
                    "cancelledAt", result.getCancelledAt(),
                    "refundAmount", result.getRefundAmount()
                )
            );
            
        } catch (SubscriptionNotFoundException e) {
            return ActionResult.failed("SUBSCRIPTION_NOT_FOUND", 
                "Subscription not found");
            
        } catch (PermissionDeniedException e) {
            return ActionResult.failed("PERMISSION_DENIED",
                "You don't have permission to cancel this subscription");
        }
    }
    
    /**
     * Execute: request_refund
     */
    private ActionResult requestRefund(
        String userId,
        Map<String, Object> parameters) {
        
        String orderId = (String) parameters.get("orderId");
        String reason = (String) parameters.get("reason");
        
        try {
            RefundResult result = orderService.requestRefund(
                userId,
                orderId,
                reason
            );
            
            return ActionResult.success(
                "Refund request submitted. You'll receive it within 5-7 business days",
                Map.of("refundId", result.getRefundId())
            );
            
        } catch (OrderNotFoundException e) {
            return ActionResult.failed("ORDER_NOT_FOUND", "Order not found");
        }
    }
    
    /**
     * Execute: update_payment_method
     */
    private ActionResult updatePaymentMethod(
        String userId,
        Map<String, Object> parameters) {
        
        String paymentType = (String) parameters.get("paymentType");
        String cardNumber = (String) parameters.get("cardNumber");
        
        try {
            paymentService.updatePayment(userId, paymentType, cardNumber);
            
            return ActionResult.success(
                "Payment method updated successfully",
                Map.of("paymentType", paymentType)
            );
            
        } catch (PaymentException e) {
            return ActionResult.failed("PAYMENT_ERROR", e.getMessage());
        }
    }
    
    @Override
    public ActionResult mergeResults(List<ActionResult> results) {
        log.debug("Merging {} results", results.size());
        
        // Check if all successful
        boolean allSuccess = results.stream()
            .allMatch(r -> r.getStatus() == ActionStatus.SUCCESS);
        
        if (!allSuccess) {
            // Find first failure
            ActionResult failure = results.stream()
                .filter(r -> r.getStatus() == ActionStatus.FAILED)
                .findFirst()
                .orElse(results.get(0));
            
            return failure;
        }
        
        // Merge all successful results
        StringBuilder message = new StringBuilder();
        Map<String, Object> mergedData = new HashMap<>();
        
        for (ActionResult result : results) {
            message.append(result.getMessage()).append(" ");
            if (result.getData() != null) {
                mergedData.putAll(result.getData());
            }
        }
        
        return ActionResult.success(message.toString().trim(), mergedData);
    }
    
    @Override
    public ActionResult handleError(
        String actionName,
        Exception exception,
        String userId) {
        
        log.error("Error handling action {} for user {}: {}", 
            actionName, userId, exception.getMessage(), exception);
        
        // Log for support team
        logActionError(actionName, userId, exception);
        
        // Return user-friendly error
        return ActionResult.failed(
            "ACTION_FAILED",
            "We encountered an issue processing your request. " +
            "Our support team has been notified."
        );
    }
    
    @Override
    public boolean validateActionAllowed(
        String actionName,
        Map<String, Object> parameters,
        String userId) {
        
        log.debug("Validating action {} for user {}", actionName, userId);
        
        // Check user permissions
        if (!permissionService.hasPermission(userId, "action:" + actionName)) {
            return false;
        }
        
        // Action-specific validation
        switch (actionName) {
            case "cancel_subscription":
                return validateCancelSubscription(userId, parameters);
                
            case "request_refund":
                return validateRequestRefund(userId, parameters);
                
            default:
                return true;
        }
    }
    
    private boolean validateCancelSubscription(
        String userId,
        Map<String, Object> parameters) {
        
        String subscriptionId = (String) parameters.get("subscriptionId");
        
        // Check if subscription belongs to user
        return subscriptionService.belongsToUser(userId, subscriptionId);
    }
    
    private boolean validateRequestRefund(
        String userId,
        Map<String, Object> parameters) {
        
        String orderId = (String) parameters.get("orderId");
        
        // Check if order belongs to user and is refundable
        return orderService.isRefundable(userId, orderId);
    }
    
    @Override
    public String getConfirmationMessage(
        String actionName,
        Map<String, Object> parameters) {
        
        switch (actionName) {
            case "cancel_subscription":
                return "Are you sure you want to cancel your subscription? " +
                       "This action is permanent and cannot be undone.";
                       
            case "request_refund":
                return "Are you sure you want to request a refund? " +
                       "A refund usually takes 5-7 business days.";
                       
            default:
                return "Are you sure you want to proceed?";
        }
    }
    
    private void logActionError(String action, String userId, Exception e) {
        // Log to your monitoring/logging system
        // auditLog.log(action, userId, "ERROR", e.getMessage());
    }
}
```

---

## Step 5: Configuration for Dependency Injection

**File:** `application.yml`

```yaml
spring:
  application:
    name: my-ai-app
  
ai:
  orchestration:
    # Action handling settings
    action:
      enabled: true
      timeout-seconds: 30
      require-confirmation-for-high-risk: true
      high-risk-actions:
        - cancel_subscription
        - request_refund
        - update_payment_method
    
    # Compound intent handling
    compound:
      strategy: sequential  # sequential, parallel, merged
      max-parallel: 4
      timeout-seconds: 60
```

---

## How Delegation Works

### Flow Diagram

```
User Query
    ↓
RAGOrchestrator.orchestrate()
    ├─ Extract intents
    │
    ├─ Single Intent?
    │   └─ handleSingleIntent()
    │       └─ ACTION?
    │           ├─ actionHandler.validateActionAllowed()  ← USER CODE
    │           ├─ actionHandler.getConfirmationMessage()  ← USER CODE
    │           ├─ actionHandler.executeAction()           ← USER CODE (MAIN)
    │           └─ actionHandler.handleError()             ← USER CODE
    │
    └─ Compound Intent?
        └─ handleCompoundIntents()
            ├─ Sequential/Parallel/Merged execution
            │   └─ For each intent: handleSingleIntent()
            │
            └─ actionHandler.mergeResults()  ← USER CODE
```

---

## Responsibilities Split

### Library (ai-infrastructure-core)
```
✅ Intent extraction
✅ Orchestration logic
✅ Sequential/parallel/merged strategies
✅ Routing to appropriate handlers
✅ Error propagation
✅ Type definitions
```

### User (implements ActionHandler)
```
✅ Action execution (cancel, refund, etc.)
✅ Permission validation
✅ Business logic
✅ Error handling
✅ Result merging
✅ Confirmation messages
✅ Logging/auditing
```

---

## Benefits of This Pattern

### For Library Users
✅ **Simple to understand** - Clear interface to implement
✅ **Extensible** - Easy to add new actions
✅ **Flexible** - Can customize error handling, confirmations, etc.
✅ **Testable** - Mock ActionHandler for testing
✅ **Type-safe** - Use existing DTOs

### For Library Developers
✅ **Separation of concerns** - Library handles orchestration, user handles business logic
✅ **No coupling** - Library doesn't depend on user's services
✅ **Easy to evolve** - Can add new methods to interface
✅ **Maintainable** - Clear responsibility boundaries
✅ **Reusable** - Same orchestration for all users

---

## Example: Adding New Action

### Step 1: User adds to ActionHandlerImpl

```java
@Override
public ActionResult executeAction(...) {
    switch (actionName) {
        // Existing cases...
        
        case "update_shipping_address":  // ← NEW
            return updateShippingAddress(userId, parameters);
    }
}

private ActionResult updateShippingAddress(String userId, Map<String, Object> params) {
    // User implements
    return ActionResult.success("Address updated");
}
```

### Step 2: User declares action in AvailableActionsRegistry

```java
ActionInfo.builder()
    .action("update_shipping_address")
    .description("Update shipping address")
    .parameters(...)
    .build()
```

### Step 3: Done!
- Library automatically recognizes it
- LLM includes it in available actions
- Orchestration flow handles it
- User's executeAction() is called

---

## Testing Strategy

### Unit Test ActionHandler

```java
@SpringBootTest
public class ActionHandlerTest {
    
    @Autowired
    private ActionHandler actionHandler;
    
    @Test
    void shouldExecuteCancelSubscription() {
        ActionResult result = actionHandler.executeAction(
            "cancel_subscription",
            Map.of("subscriptionId", "sub-123"),
            intent,
            "user-123"
        );
        
        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
    }
}
```

### Integration Test Orchestrator

```java
@SpringBootTest
public class RAGOrchestratorTest {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @Test
    void shouldHandleActionIntent() {
        OrchestrationResult result = orchestrator.orchestrate(
            "Cancel my subscription",
            "user-123"
        );
        
        assertThat(result.isSuccess()).isTrue();
    }
}
```

---

## Summary

### Delegation Pattern

| Component | Responsibility |
|-----------|---|
| **Library** | Orchestration, routing, intent extraction |
| **User** | Action execution, validation, error handling |

### Implementation Steps

1. ✅ Create ActionHandler interface
2. ✅ Create ActionResult DTO
3. ✅ Update RAGOrchestrator with delegation
4. ✅ User implements ActionHandlerImpl
5. ✅ Register in Spring context
6. ✅ Works automatically!

### Code Flow

```
RAGOrchestrator.orchestrate()
    ↓
Detects ACTION intent
    ↓
Calls actionHandler.executeAction()  ← DELEGATION
    ↓
User's implementation executes
    ↓
Returns ActionResult
    ↓
RAGOrchestrator builds response
```

---

## Best Practices

✅ **Keep interface small** - Only essential methods
✅ **Use DTOs** - Avoid tight coupling
✅ **Provide defaults** - Have sensible defaults
✅ **Document examples** - Show how to implement
✅ **Version interface** - Plan for future additions
✅ **Test thoroughly** - User's code is critical

---

## Conclusion

**Hybrid Delegation Pattern:**
- Library provides framework and orchestration
- User implements business logic and actions
- Clear separation of concerns
- Easy to extend and test
- Professional and maintainable

**This is enterprise-grade architecture!** 🚀

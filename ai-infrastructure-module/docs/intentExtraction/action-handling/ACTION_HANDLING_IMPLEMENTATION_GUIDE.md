# Action Handling - Quick Implementation Guide (3 hours)

## Overview

You'll create a **delegation framework** where:
- **Library** handles orchestration
- **User** handles action execution

---

## Step 1: Create ActionHandler Interface (20 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/ActionHandler.java`

```java
package com.ai.infrastructure.rag;

import com.ai.infrastructure.dto.Intent;
import java.util.Map;
import java.util.List;

/**
 * Users implement this to handle actions
 * 
 * Library calls these methods, user implements business logic
 */
public interface ActionHandler {
    
    /**
     * Execute an action (main method)
     */
    ActionResult executeAction(
        String actionName,
        Map<String, Object> parameters,
        Intent intent,
        String userId
    );
    
    /**
     * Merge multiple action results
     */
    ActionResult mergeResults(List<ActionResult> results);
    
    /**
     * Handle errors gracefully
     */
    ActionResult handleError(
        String actionName,
        Exception exception,
        String userId
    );
    
    /**
     * Validate if action is allowed
     */
    boolean validateActionAllowed(
        String actionName,
        Map<String, Object> parameters,
        String userId
    );
    
    /**
     * Get confirmation message for high-risk actions
     */
    String getConfirmationMessage(
        String actionName,
        Map<String, Object> parameters
    );
}
```

---

## Step 2: Create ActionResult DTO (20 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/ActionResult.java`

```java
package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {
    
    private ActionStatus status;
    private String message;
    private String confirmationMessage;
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
    private String errorCode;
    private String errorMessage;
    private Boolean requiresConfirmation;
    private String confirmationToken;
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
    
    public static ActionResult confirmationRequired(
        String message, 
        String confirmationToken) {
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

## Step 3: Create OrchestrationResult DTO (20 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/OrchestrationResult.java`

```java
package com.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrchestrationResult {
    
    private String type;  // ACTION, INFORMATION, OUT_OF_SCOPE, COMPOUND
    private String message;
    private boolean success;
    private Double confidence;
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
    
    private String errorCode;
    private String errorMessage;
    
    private Boolean requiresConfirmation;
    private String confirmationToken;
    private String confirmationMessage;
    private String actionName;
    private Map<String, Object> actionParams;
    
    // ==================== FACTORY METHODS ====================
    
    public static OrchestrationResult success(String type, String message, Double confidence) {
        return OrchestrationResult.builder()
            .type(type)
            .message(message)
            .success(true)
            .confidence(confidence)
            .build();
    }
    
    public static OrchestrationResult failure(String message) {
        return OrchestrationResult.builder()
            .message(message)
            .success(false)
            .build();
    }
    
    public static OrchestrationResult outOfScope(String message) {
        return OrchestrationResult.builder()
            .type("OUT_OF_SCOPE")
            .message(message)
            .success(true)
            .build();
    }
    
    public static OrchestrationResult confirmationRequired(
        String message,
        String actionName,
        Map<String, Object> params) {
        return OrchestrationResult.builder()
            .requiresConfirmation(true)
            .confirmationMessage(message)
            .actionName(actionName)
            .actionParams(params)
            .success(true)
            .build();
    }
    
    public static OrchestrationResult fromActionResult(ActionResult result) {
        return OrchestrationResult.builder()
            .type("ACTION")
            .message(result.getMessage())
            .success(result.getStatus() == ActionStatus.SUCCESS)
            .data(result.getData())
            .errorCode(result.getErrorCode())
            .errorMessage(result.getErrorMessage())
            .requiresConfirmation(result.getRequiresConfirmation())
            .build();
    }
}
```

---

## Step 4: Update RAGOrchestrator (60 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/RAGOrchestrator.java`

Replace with this complete version (see previous document for full code):

Key changes:
- ✅ Inject ActionHandler
- ✅ Update handleSingleIntent() to delegate
- ✅ Update handleCompoundIntents() to delegate
- ✅ Add validation, confirmation, error handling

---

## Step 5: User Implements ActionHandler (60 min)

**File:** User's app `ActionHandlerImpl.java`

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
import java.util.HashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionHandlerImpl implements ActionHandler {
    
    private final SubscriptionService subscriptionService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    
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
                    return executeCancel(userId, parameters);
                    
                case "request_refund":
                    return executeRefund(userId, parameters);
                    
                case "update_payment_method":
                    return executeUpdatePayment(userId, parameters);
                    
                default:
                    return ActionResult.failed("UNKNOWN_ACTION", 
                        "Action not implemented: " + actionName);
            }
            
        } catch (Exception e) {
            return handleError(actionName, e, userId);
        }
    }
    
    private ActionResult executeCancel(String userId, Map<String, Object> params) {
        String subscriptionId = (String) params.get("subscriptionId");
        String reason = (String) params.getOrDefault("reason", "");
        
        try {
            SubscriptionCancellationResult result = 
                subscriptionService.cancelSubscription(userId, subscriptionId, reason);
            
            return ActionResult.success(
                "Your subscription has been cancelled",
                Map.of("cancelledAt", result.getCancelledAt())
            );
        } catch (Exception e) {
            return ActionResult.failed("CANCEL_FAILED", e.getMessage());
        }
    }
    
    private ActionResult executeRefund(String userId, Map<String, Object> params) {
        String orderId = (String) params.get("orderId");
        String reason = (String) params.get("reason");
        
        try {
            RefundResult result = orderService.requestRefund(userId, orderId, reason);
            
            return ActionResult.success(
                "Refund request submitted",
                Map.of("refundId", result.getRefundId())
            );
        } catch (Exception e) {
            return ActionResult.failed("REFUND_FAILED", e.getMessage());
        }
    }
    
    private ActionResult executeUpdatePayment(String userId, Map<String, Object> params) {
        String paymentType = (String) params.get("paymentType");
        
        try {
            paymentService.updatePayment(userId, paymentType);
            
            return ActionResult.success(
                "Payment method updated successfully",
                Map.of("paymentType", paymentType)
            );
        } catch (Exception e) {
            return ActionResult.failed("PAYMENT_FAILED", e.getMessage());
        }
    }
    
    @Override
    public ActionResult mergeResults(List<ActionResult> results) {
        log.debug("Merging {} results", results.size());
        
        // If any failed, return first failure
        for (ActionResult result : results) {
            if (result.getStatus() != ActionStatus.SUCCESS) {
                return result;
            }
        }
        
        // Merge all success messages
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
    public ActionResult handleError(String actionName, Exception exception, String userId) {
        log.error("Error handling action {} for user {}", actionName, userId, exception);
        
        return ActionResult.failed(
            "ACTION_FAILED",
            "We encountered an issue. Support has been notified."
        );
    }
    
    @Override
    public boolean validateActionAllowed(
        String actionName,
        Map<String, Object> parameters,
        String userId) {
        
        log.debug("Validating action {} for user {}", actionName, userId);
        
        // Check permissions
        switch (actionName) {
            case "cancel_subscription":
                String subId = (String) parameters.get("subscriptionId");
                return subscriptionService.belongsToUser(userId, subId);
                
            case "request_refund":
                String orderId = (String) parameters.get("orderId");
                return orderService.isRefundable(userId, orderId);
                
            default:
                return true;
        }
    }
    
    @Override
    public String getConfirmationMessage(String actionName, Map<String, Object> parameters) {
        switch (actionName) {
            case "cancel_subscription":
                return "Are you sure? Cancelling is permanent.";
                
            case "request_refund":
                return "Are you sure? Refunds take 5-7 business days.";
                
            default:
                return "Are you sure you want to proceed?";
        }
    }
}
```

---

## Step 6: Register in Spring (10 min)

**File:** `application.yml`

```yaml
spring:
  application:
    name: my-ai-app

ai:
  orchestration:
    enabled: true
    action:
      timeout-seconds: 30
      require-confirmation-for-high-risk: true
      high-risk-actions:
        - cancel_subscription
        - request_refund
    compound:
      strategy: sequential
```

---

## Step 7: Write Tests (30 min)

**File:** Test your ActionHandlerImpl

```java
@SpringBootTest
class ActionHandlerImplTest {
    
    @Autowired
    private ActionHandler actionHandler;
    
    @Test
    void shouldExecuteCancelSubscription() {
        ActionResult result = actionHandler.executeAction(
            "cancel_subscription",
            Map.of("subscriptionId", "sub-123"),
            null,
            "user-123"
        );
        
        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(result.getMessage()).contains("cancelled");
    }
    
    @Test
    void shouldValidatePermissions() {
        boolean allowed = actionHandler.validateActionAllowed(
            "cancel_subscription",
            Map.of("subscriptionId", "sub-123"),
            "user-123"
        );
        
        assertThat(allowed).isTrue();
    }
    
    @Test
    void shouldGetConfirmationMessage() {
        String message = actionHandler.getConfirmationMessage(
            "cancel_subscription",
            Map.of("subscriptionId", "sub-123")
        );
        
        assertThat(message).isNotEmpty();
    }
}
```

---

## Step 8: Test End-to-End (30 min)

**File:** Integration test

```java
@SpringBootTest
class RAGOrchestratorTest {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @Test
    void shouldHandleActionIntent() {
        OrchestrationResult result = orchestrator.orchestrate(
            "Cancel my subscription",
            "user-123"
        );
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getType()).isEqualTo("ACTION");
    }
    
    @Test
    void shouldHandleCompoundIntents() {
        OrchestrationResult result = orchestrator.orchestrate(
            "What's your return policy and process my refund",
            "user-123"
        );
        
        assertThat(result.isSuccess()).isTrue();
    }
}
```

---

## Total Time: 3 Hours

- Step 1 (ActionHandler interface): 20 min
- Step 2 (ActionResult DTO): 20 min
- Step 3 (OrchestrationResult DTO): 20 min
- Step 4 (Update RAGOrchestrator): 60 min
- Step 5 (User implements ActionHandlerImpl): 60 min
- Step 6 (Configuration): 10 min
- Step 7 (Unit tests): 30 min
- Step 8 (Integration tests): 30 min

**Total: 3 hours to production-ready implementation**

---

## Verification Checklist

- [ ] ActionHandler interface created
- [ ] ActionResult DTO created
- [ ] OrchestrationResult DTO created
- [ ] RAGOrchestrator updated with delegation
- [ ] User creates ActionHandlerImpl
- [ ] Configuration in application.yml
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing successful
- [ ] Deploy to production

---

## Success Indicators

After implementation:

✅ User query triggers orchestrator
✅ Orchestrator detects action intent
✅ Calls actionHandler.validateActionAllowed()
✅ Calls actionHandler.executeAction()
✅ Returns ActionResult
✅ Orchestrator builds response
✅ Response sent to user

---

## Common Issues & Solutions

**Issue:** ActionHandler not found
**Solution:** Check @Component annotation is present

**Issue:** Wrong method signature
**Solution:** Match interface exactly

**Issue:** NullPointerException in executeAction
**Solution:** Check parameter casting

**Issue:** Confirmation not appearing
**Solution:** Check confirmationRequired flag in Intent

---

## Next Steps

1. ✅ Create interfaces and DTOs
2. ✅ Update orchestrator
3. ✅ User implements ActionHandler
4. ✅ Test thoroughly
5. ✅ Deploy to production
6. ✅ Monitor execution

**You're done! 🚀**


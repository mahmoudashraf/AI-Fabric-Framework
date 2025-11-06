# AvailableActions - Quick Start Implementation (30 min)

## TL;DR

Use **Dynamic Registry Pattern** - let each service self-register its actions via `AIActionProvider` interface.

---

## Step 1: Create ActionInfo DTOs (5 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/ActionInfo.java`

```java
package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionInfo {
    
    private String action;                              // "cancel_subscription"
    private String description;                         // "Cancel user subscription"
    @Builder.Default
    private List<String> requiredParams = new ArrayList<>();  // ["userId", "orderId"]
    private Boolean confirmationRequired;               // true/false
    @Builder.Default
    private List<String> examples = new ArrayList<>();  // ["Cancel my subscription"]
    @Builder.Default
    private List<ActionParameterInfo> parameters = new ArrayList<>();
    private String category;                            // "subscription", "payment", etc
    private String riskLevel;                           // "low", "medium", "high"
}
```

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/ActionParameterInfo.java`

```java
package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionParameterInfo {
    
    private String name;           // "userId"
    private String type;           // "String", "UUID", "Integer"
    private String description;    // "The unique user ID"
    private Boolean required;      // true/false
    private String defaultValue;   // optional
    private String[] enumValues;   // for restricted options
}
```

---

## Step 2: Create AIActionProvider Interface (2 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/action/AIActionProvider.java`

```java
package com.ai.infrastructure.action;

import com.ai.infrastructure.dto.ActionInfo;
import java.util.List;

/**
 * Interface for services that provide AI-executable actions
 * 
 * Any service that can be called by the LLM should implement this
 * and register its available actions
 */
public interface AIActionProvider {
    
    /**
     * Return list of actions this service can perform
     * 
     * @return list of available actions
     */
    List<ActionInfo> getAvailableActions();
}
```

---

## Step 3: Create Central Registry (5 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/action/AvailableActionsRegistry.java`

```java
package com.ai.infrastructure.action;

import com.ai.infrastructure.dto.ActionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailableActionsRegistry {
    
    private final List<AIActionProvider> providers;  // Spring auto-injects all AIActionProvider beans
    
    /**
     * Get all available actions from all registered providers
     * 
     * @return list of all available actions
     */
    public List<ActionInfo> getAllAvailableActions() {
        log.debug("Building list of all available actions");
        
        List<ActionInfo> allActions = providers.stream()
            .flatMap(provider -> {
                log.debug("Getting actions from provider: {}", provider.getClass().getSimpleName());
                return provider.getAvailableActions().stream();
            })
            .collect(Collectors.toList());
        
        log.info("Total available actions: {}", allActions.size());
        return allActions;
    }
    
    /**
     * Get actions by category (e.g., "subscription", "payment")
     * 
     * @param category the category to filter by
     * @return filtered list of actions
     */
    public List<ActionInfo> getActionsByCategory(String category) {
        return getAllAvailableActions().stream()
            .filter(a -> category.equals(a.getCategory()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get action by name
     * 
     * @param actionName the action name
     * @return the action info or null if not found
     */
    public ActionInfo getActionByName(String actionName) {
        return getAllAvailableActions().stream()
            .filter(a -> actionName.equals(a.getAction()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if action exists
     * 
     * @param actionName the action name
     * @return true if action exists
     */
    public boolean actionExists(String actionName) {
        return getActionByName(actionName) != null;
    }
}
```

---

## Step 4: Example Service Implementation (10 min)

**Update existing service to implement AIActionProvider:**

**File:** `backend/src/main/java/com/easyluxury/service/SubscriptionService.java`

```java
package com.easyluxury.service;

import com.ai.infrastructure.action.AIActionProvider;
import com.ai.infrastructure.dto.ActionInfo;
import com.ai.infrastructure.dto.ActionParameterInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService implements AIActionProvider {
    
    // ... existing code ...
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            // Action 1: Cancel Subscription
            ActionInfo.builder()
                .action("cancel_subscription")
                .description("Cancel user's active subscription")
                .category("subscription")
                .riskLevel("high")
                .confirmationRequired(true)
                .requiredParams(List.of("subscriptionId"))
                .examples(List.of(
                    "Cancel my subscription",
                    "Stop my membership",
                    "Unsubscribe me",
                    "I want to cancel my account"
                ))
                .parameters(List.of(
                    ActionParameterInfo.builder()
                        .name("subscriptionId")
                        .type("String")
                        .description("The unique subscription ID (UUID)")
                        .required(true)
                        .build(),
                    ActionParameterInfo.builder()
                        .name("reason")
                        .type("String")
                        .description("Reason for cancellation (optional)")
                        .required(false)
                        .enumValues(new String[]{"too_expensive", "not_using", "switching", "other"})
                        .build()
                ))
                .build(),
            
            // Action 2: Upgrade Subscription
            ActionInfo.builder()
                .action("upgrade_subscription")
                .description("Upgrade subscription to a higher tier")
                .category("subscription")
                .riskLevel("medium")
                .confirmationRequired(true)
                .requiredParams(List.of("subscriptionId", "newPlan"))
                .examples(List.of(
                    "Upgrade my subscription",
                    "Switch to premium plan",
                    "I want a better subscription"
                ))
                .parameters(List.of(
                    ActionParameterInfo.builder()
                        .name("subscriptionId")
                        .type("String")
                        .description("Current subscription ID")
                        .required(true)
                        .build(),
                    ActionParameterInfo.builder()
                        .name("newPlan")
                        .type("String")
                        .description("New plan name")
                        .required(true)
                        .enumValues(new String[]{"basic", "premium", "enterprise"})
                        .build()
                ))
                .build(),
            
            // Action 3: Pause Subscription
            ActionInfo.builder()
                .action("pause_subscription")
                .description("Pause subscription temporarily")
                .category("subscription")
                .riskLevel("low")
                .confirmationRequired(false)
                .requiredParams(List.of("subscriptionId"))
                .examples(List.of(
                    "Pause my subscription",
                    "Temporarily stop my subscription",
                    "Freeze my account"
                ))
                .parameters(List.of(
                    ActionParameterInfo.builder()
                        .name("subscriptionId")
                        .type("String")
                        .description("Subscription ID to pause")
                        .required(true)
                        .build()
                ))
                .build()
        );
    }
}
```

**Similarly for PaymentService:**

```java
@Service
@RequiredArgsConstructor
public class PaymentService implements AIActionProvider {
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            ActionInfo.builder()
                .action("update_payment_method")
                .description("Update user's payment method")
                .category("payment")
                .riskLevel("high")
                .confirmationRequired(true)
                .requiredParams(List.of("userId", "paymentType"))
                .examples(List.of(
                    "Update my payment method",
                    "Change my credit card",
                    "Switch to PayPal"
                ))
                .parameters(List.of(
                    ActionParameterInfo.builder()
                        .name("userId")
                        .type("String")
                        .description("User ID")
                        .required(true)
                        .build(),
                    ActionParameterInfo.builder()
                        .name("paymentType")
                        .type("String")
                        .description("Payment method")
                        .required(true)
                        .enumValues(new String[]{"credit_card", "debit_card", "paypal"})
                        .build()
                ))
                .build()
        );
    }
}
```

---

## Step 5: Update SystemContextBuilder (5 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/context/SystemContextBuilder.java`

Add this to existing builder:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemContextBuilder {
    
    private final AvailableActionsRegistry actionsRegistry;
    // ... other dependencies ...
    
    public SystemContext buildContext(String userId) {
        return SystemContext.builder()
            .entityTypesSchema(buildEntityTypesSchema())
            .knowledgeBaseOverview(buildKnowledgeBaseOverview())
            .availableActions(actionsRegistry.getAllAvailableActions())  // ← NEW: Get all actions
            .userBehaviorContext(buildUserBehaviorContext(userId))
            .indexStatistics(buildIndexStatistics())
            .systemCapabilities(buildSystemCapabilities())
            .build();
    }
}
```

---

## Step 6: Test It (3 min)

**File:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/action/AvailableActionsRegistryTest.java`

```java
@SpringBootTest
class AvailableActionsRegistryTest {
    
    @Autowired
    private AvailableActionsRegistry registry;
    
    @Test
    void shouldGetAllAvailableActions() {
        List<ActionInfo> actions = registry.getAllAvailableActions();
        
        assertThat(actions).isNotEmpty();
        assertThat(actions).anyMatch(a -> "cancel_subscription".equals(a.getAction()));
        assertThat(actions).anyMatch(a -> "update_payment_method".equals(a.getAction()));
    }
    
    @Test
    void shouldGetActionByName() {
        ActionInfo action = registry.getActionByName("cancel_subscription");
        
        assertThat(action).isNotNull();
        assertThat(action.getDescription()).isNotEmpty();
        assertThat(action.getExamples()).isNotEmpty();
    }
    
    @Test
    void shouldCheckActionExists() {
        assertThat(registry.actionExists("cancel_subscription")).isTrue();
        assertThat(registry.actionExists("nonexistent_action")).isFalse();
    }
}
```

---

## Step 7: Use in IntentQueryExtractor (Update)

**In SystemContextBuilder, pass actions to LLM prompt:**

```java
private String buildSystemPrompt() {
    String availableActionsJson = buildAvailableActionsJson();
    
    return """
        You are an expert at understanding user intent.
        
        AVAILABLE ACTIONS THE USER CAN TRIGGER:
        """ + availableActionsJson + """
        
        If the user's query matches one of these actions:
        - Set intent.type = "ACTION"
        - Set intent.action = the action name
        - Set intent.actionParams = map of required parameters
        
        If no action matches:
        - Set intent.type = "INFORMATION" (retrieve from docs)
        - Set intent.type = "OUT_OF_SCOPE" (if not in docs or actions)
        
        Respond with JSON: {...}
        """;
}

private String buildAvailableActionsJson() {
    return actionsRegistry.getAllAvailableActions().stream()
        .map(action -> String.format(
            "{\"name\": \"%s\", \"description\": \"%s\", \"examples\": %s}",
            action.getAction(),
            action.getDescription(),
            action.getExamples()
        ))
        .collect(Collectors.joining(",\n"));
}
```

---

## Checklist ✅

- [ ] Create `ActionInfo` DTO
- [ ] Create `ActionParameterInfo` DTO
- [ ] Create `AIActionProvider` interface
- [ ] Create `AvailableActionsRegistry` service
- [ ] Update `SubscriptionService` to implement `AIActionProvider`
- [ ] Update `PaymentService` to implement `AIActionProvider`
- [ ] Update other services (OrderService, UserService, etc.)
- [ ] Update `SystemContextBuilder` to use registry
- [ ] Update `IntentQueryExtractor` to include actions in prompt
- [ ] Write tests
- [ ] Deploy and test with LLM

---

## Result

Now the LLM knows:
✅ What actions can be performed
✅ What parameters they need
✅ When to call them
✅ Examples of user queries

Example LLM response:
```json
{
  "type": "ACTION",
  "action": "cancel_subscription",
  "actionParams": {
    "subscriptionId": "user-subscription-123",
    "reason": "too_expensive"
  },
  "confirmationRequired": true
}
```

Done! 🎉


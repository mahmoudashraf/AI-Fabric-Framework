# AvailableActions - Complete Implementation Guide

## Overview
This guide will take you from zero to fully implemented AvailableActions in **~3 hours**.

---

## Prerequisites
- Spring Boot knowledge ✓
- Access to your codebase ✓
- Java 11+ ✓
- 3 hours of focused time ⏱️

---

## Step-by-Step Implementation

### STEP 1: Create ActionInfo DTO (10 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/ActionInfo.java`

```java
package com.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an action that can be executed by the AI system
 * 
 * Example:
 * {
 *   "action": "cancel_subscription",
 *   "description": "Cancel user's subscription",
 *   "category": "subscription",
 *   "riskLevel": "high",
 *   "confirmationRequired": true,
 *   "requiredParams": ["subscriptionId"],
 *   "examples": ["Cancel my subscription", "Stop my membership"],
 *   "parameters": [...]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionInfo {
    
    /** Unique action identifier (e.g., "cancel_subscription") */
    private String action;
    
    /** Human-readable description */
    private String description;
    
    /** Business category (e.g., "subscription", "payment", "order") */
    private String category;
    
    /** Risk level for this action: "low", "medium", "high" */
    private String riskLevel;
    
    /** Whether user confirmation is required before execution */
    private Boolean confirmationRequired;
    
    /** List of required parameter names */
    @Builder.Default
    private List<String> requiredParams = new ArrayList<>();
    
    /** Example user queries that trigger this action */
    @Builder.Default
    private List<String> examples = new ArrayList<>();
    
    /** Detailed parameter definitions */
    @Builder.Default
    private List<ActionParameterInfo> parameters = new ArrayList<>();
    
    /** Service that handles this action (populated by registry) */
    private String handlerService;
    
    /** Method name that executes this action (populated by registry) */
    private String handlerMethod;
}
```

---

### STEP 2: Create ActionParameterInfo DTO (10 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/ActionParameterInfo.java`

```java
package com.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Represents a parameter for an action
 * 
 * Example:
 * {
 *   "name": "subscriptionId",
 *   "type": "String",
 *   "description": "The subscription ID",
 *   "required": true,
 *   "enumValues": null
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionParameterInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** Parameter name */
    private String name;
    
    /** Parameter type (String, Integer, Boolean, List, etc.) */
    private String type;
    
    /** Parameter description */
    private String description;
    
    /** Whether this parameter is required */
    private Boolean required;
    
    /** Default value if not provided */
    private String defaultValue;
    
    /** Allowed values for enum-type parameters */
    private String[] enumValues;
    
    /** Example value */
    private String exampleValue;
}
```

---

### STEP 3: Create AIActionProvider Interface (5 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/action/AIActionProvider.java`

```java
package com.ai.infrastructure.action;

import com.ai.infrastructure.dto.ActionInfo;
import java.util.List;

/**
 * Interface that services implement to provide AI-executable actions
 * 
 * Services that can be called by the AI (via function calling) should implement this
 * and register their available actions.
 * 
 * Example usage:
 * 
 * @Service
 * public class SubscriptionService implements AIActionProvider {
 *     @Override
 *     public List<ActionInfo> getAvailableActions() {
 *         return List.of(
 *             ActionInfo.builder()
 *                 .action("cancel_subscription")
 *                 .description("Cancel subscription")
 *                 .build()
 *         );
 *     }
 * }
 * 
 * Spring automatically discovers all implementations and registers them in the registry.
 */
public interface AIActionProvider {
    
    /**
     * Return the list of actions this service provides
     * 
     * @return list of available actions
     */
    List<ActionInfo> getAvailableActions();
}
```

---

### STEP 4: Create AvailableActionsRegistry Service (15 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/action/AvailableActionsRegistry.java`

```java
package com.ai.infrastructure.action;

import com.ai.infrastructure.dto.ActionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry for all available AI actions
 * 
 * Collects actions from all AIActionProvider implementations and provides:
 * - List of all available actions
 * - Actions filtered by category
 * - Lookup by action name
 * - Validation that action exists
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvailableActionsRegistry {
    
    private final List<AIActionProvider> providers;
    private volatile List<ActionInfo> cachedActions;
    
    /**
     * Get all available actions from all registered providers
     * 
     * @return list of all available actions (cached)
     */
    public List<ActionInfo> getAllAvailableActions() {
        // Return cached if available
        if (cachedActions != null) {
            return new ArrayList<>(cachedActions);
        }
        
        log.debug("Building list of all available actions from {} providers", 
            providers.size());
        
        List<ActionInfo> allActions = providers.stream()
            .flatMap(provider -> {
                String providerName = provider.getClass().getSimpleName();
                try {
                    List<ActionInfo> actions = provider.getAvailableActions();
                    log.debug("Provider {} offers {} actions", providerName, 
                        actions.size());
                    actions.forEach(a -> a.setHandlerService(providerName));
                    return actions.stream();
                } catch (Exception e) {
                    log.error("Error getting actions from provider {}", 
                        providerName, e);
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());
        
        // Cache result
        this.cachedActions = allActions;
        
        log.info("Registry initialized with {} total available actions", 
            allActions.size());
        
        return new ArrayList<>(allActions);
    }
    
    /**
     * Get actions by category
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
     * @return the action info or empty if not found
     */
    public Optional<ActionInfo> getActionByName(String actionName) {
        return getAllAvailableActions().stream()
            .filter(a -> actionName.equals(a.getAction()))
            .findFirst();
    }
    
    /**
     * Check if action exists
     * 
     * @param actionName the action name
     * @return true if action exists
     */
    public boolean actionExists(String actionName) {
        return getActionByName(actionName).isPresent();
    }
    
    /**
     * Get categories of all actions
     * 
     * @return list of unique categories
     */
    public List<String> getCategories() {
        return getAllAvailableActions().stream()
            .map(ActionInfo::getCategory)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }
    
    /**
     * Get count of actions by category
     * 
     * @return map of category to count
     */
    public Map<String, Integer> getActionCountByCategory() {
        return getAllAvailableActions().stream()
            .collect(Collectors.groupingBy(
                ActionInfo::getCategory,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    List::size
                )
            ));
    }
    
    /**
     * Format actions for LLM prompt
     * 
     * @return formatted string describing all actions
     */
    public String formatForLLMPrompt() {
        return getAllAvailableActions().stream()
            .map(this::formatActionForPrompt)
            .collect(Collectors.joining("\n\n"));
    }
    
    /**
     * Format single action for LLM prompt
     */
    private String formatActionForPrompt(ActionInfo action) {
        StringBuilder sb = new StringBuilder();
        sb.append("ACTION: ").append(action.getAction()).append("\n");
        sb.append("Description: ").append(action.getDescription()).append("\n");
        
        if (action.getCategory() != null) {
            sb.append("Category: ").append(action.getCategory()).append("\n");
        }
        
        if (action.getRiskLevel() != null) {
            sb.append("Risk Level: ").append(action.getRiskLevel()).append("\n");
        }
        
        if (action.getConfirmationRequired() != null) {
            sb.append("Requires Confirmation: ")
                .append(action.getConfirmationRequired()).append("\n");
        }
        
        if (!action.getRequiredParams().isEmpty()) {
            sb.append("Required Parameters: ")
                .append(String.join(", ", action.getRequiredParams())).append("\n");
        }
        
        if (!action.getExamples().isEmpty()) {
            sb.append("Examples: ")
                .append(String.join(" | ", action.getExamples()));
        }
        
        return sb.toString();
    }
}
```

---

### STEP 5: Update Your First Service (20 min)

**File:** Update `backend/src/main/java/com/easyluxury/service/SubscriptionService.java`

Add this to your existing SubscriptionService:

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
public class SubscriptionService implements AIActionProvider {  // ← ADD THIS
    
    // ... your existing code ...
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            buildCancelSubscriptionAction(),
            buildUpgradeSubscriptionAction(),
            buildPauseSubscriptionAction()
        );
    }
    
    private ActionInfo buildCancelSubscriptionAction() {
        return ActionInfo.builder()
            .action("cancel_subscription")
            .description("Permanently cancel user's subscription")
            .category("subscription")
            .riskLevel("high")
            .confirmationRequired(true)
            .requiredParams(List.of("subscriptionId"))
            .examples(List.of(
                "Cancel my subscription",
                "Stop my membership",
                "Unsubscribe me",
                "I want to cancel"
            ))
            .parameters(List.of(
                ActionParameterInfo.builder()
                    .name("subscriptionId")
                    .type("String")
                    .description("The unique subscription ID")
                    .required(true)
                    .build(),
                ActionParameterInfo.builder()
                    .name("reason")
                    .type("String")
                    .description("Reason for cancellation (optional)")
                    .required(false)
                    .enumValues(new String[]{
                        "too_expensive", 
                        "not_using", 
                        "switching_provider", 
                        "poor_service"
                    })
                    .build()
            ))
            .build();
    }
    
    private ActionInfo buildUpgradeSubscriptionAction() {
        return ActionInfo.builder()
            .action("upgrade_subscription")
            .description("Upgrade subscription to higher tier")
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
                    .description("New plan tier")
                    .required(true)
                    .enumValues(new String[]{"basic", "premium", "professional"})
                    .build()
            ))
            .build();
    }
    
    private ActionInfo buildPauseSubscriptionAction() {
        return ActionInfo.builder()
            .action("pause_subscription")
            .description("Temporarily pause subscription")
            .category("subscription")
            .riskLevel("low")
            .confirmationRequired(false)
            .requiredParams(List.of("subscriptionId"))
            .examples(List.of(
                "Pause my subscription",
                "Freeze my account",
                "Can I pause for a month?"
            ))
            .parameters(List.of(
                ActionParameterInfo.builder()
                    .name("subscriptionId")
                    .type("String")
                    .description("Subscription ID to pause")
                    .required(true)
                    .build()
            ))
            .build();
    }
}
```

---

### STEP 6: Update SystemContextBuilder (15 min)

**File:** Update your `SystemContextBuilder` or create new one:

```java
package com.ai.infrastructure.context;

import com.ai.infrastructure.action.AvailableActionsRegistry;
import com.ai.infrastructure.dto.ActionInfo;
import com.ai.infrastructure.dto.SystemContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemContextBuilder {
    
    private final AvailableActionsRegistry actionsRegistry;
    // ... other dependencies ...
    
    public SystemContext buildContext(String userId) {
        log.debug("Building system context for user: {}", userId);
        
        // Get all available actions from registry
        List<ActionInfo> availableActions = actionsRegistry.getAllAvailableActions();
        
        return SystemContext.builder()
            .userId(userId)
            .availableActions(availableActions)
            .actionCount(availableActions.size())
            .actionsByCategory(actionsRegistry.getActionCountByCategory())
            .knowledgeBaseOverview(buildKnowledgeBaseOverview())
            .entityTypesSchema(buildEntityTypesSchema())
            .userBehaviorContext(buildUserBehaviorContext(userId))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private Object buildKnowledgeBaseOverview() {
        // Your existing implementation
        return null;
    }
    
    private Object buildEntityTypesSchema() {
        // Your existing implementation
        return null;
    }
    
    private Object buildUserBehaviorContext(String userId) {
        // Your existing implementation
        return null;
    }
}
```

---

### STEP 7: Update IntentQueryExtractor to Use Actions (20 min)

**File:** Update your `IntentQueryExtractor`:

```java
package com.ai.infrastructure.service;

import com.ai.infrastructure.action.AvailableActionsRegistry;
import com.ai.infrastructure.context.SystemContextBuilder;
import com.ai.infrastructure.dto.MultiIntentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentQueryExtractor {
    
    private final AICoreService aiCoreService;
    private final AvailableActionsRegistry actionsRegistry;  // ← ADD THIS
    private final SystemContextBuilder contextBuilder;      // ← ADD THIS
    
    public MultiIntentResponse extract(String rawQuery, String userId) {
        try {
            // Build enriched system prompt with actions
            String systemPrompt = buildEnrichedSystemPrompt(userId);
            
            String userPrompt = String.format(
                "Extract intents from this user query: \"%s\"",
                rawQuery
            );
            
            log.debug("Extracting intents for query: {}", rawQuery);
            
            String jsonResponse = aiCoreService.generateText(
                systemPrompt,
                userPrompt
            );
            
            MultiIntentResponse response = parseResponse(jsonResponse, rawQuery);
            
            log.debug("Successfully extracted intents: {}", response.getIntents());
            
            return response;
            
        } catch (Exception e) {
            log.error("Intent extraction failed for query: {}", rawQuery, e);
            return buildFallbackResponse(rawQuery);
        }
    }
    
    private String buildEnrichedSystemPrompt(String userId) {
        // Get all available actions
        String actionsSection = actionsRegistry.formatForLLMPrompt();
        
        // Build base prompt
        String prompt = """
            You are an expert at understanding user intent and query structure.
            
            ==== AVAILABLE ACTIONS ====
            These are the actions you can trigger:
            
            """ + actionsSection + """
            
            ==== RULES ====
            1. Analyze user query against available actions
            2. If query matches an action:
               - Set intent.type = "ACTION"
               - Set intent.action = the action name
               - Set intent.actionParams = required parameters
            3. If query is informational (not an action):
               - Set intent.type = "INFORMATION"
               - Extract what to retrieve
            4. If query is out of scope:
               - Set intent.type = "OUT_OF_SCOPE"
            5. If multiple questions: set isCompound = true
            
            ==== RESPONSE FORMAT ====
            Respond ONLY with valid JSON (no markdown, no extra text):
            {
              "rawQuery": "original query",
              "primaryIntent": "intent name",
              "intents": [{
                "type": "ACTION|INFORMATION|OUT_OF_SCOPE|COMPOUND",
                "intent": "specific intent",
                "confidence": 0.0-1.0,
                "action": "action name if type=ACTION",
                "actionParams": {optional params},
                "normalizedQuery": "cleaned query for retrieval",
                "requiresRetrieval": true|false
              }],
              "isCompound": true|false,
              "requiresOrchestration": true|false
            }
            """;
        
        return prompt;
    }
    
    private MultiIntentResponse parseResponse(String json, String rawQuery) {
        // Your existing JSON parsing implementation
        // Should now handle ACTION types based on available actions
        return null;
    }
    
    private MultiIntentResponse buildFallbackResponse(String rawQuery) {
        // Your existing fallback implementation
        return null;
    }
}
```

---

### STEP 8: Write Tests (30 min)

**File:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/action/AvailableActionsRegistryTest.java`

```java
package com.ai.infrastructure.action;

import com.ai.infrastructure.dto.ActionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AvailableActionsRegistryTest {
    
    @Autowired
    private AvailableActionsRegistry registry;
    
    @Test
    void shouldGetAllAvailableActions() {
        List<ActionInfo> actions = registry.getAllAvailableActions();
        
        assertThat(actions)
            .isNotEmpty()
            .hasSizeGreaterThan(0);
        
        log.info("Found {} actions", actions.size());
    }
    
    @Test
    void shouldFindCancelSubscriptionAction() {
        Optional<ActionInfo> action = registry.getActionByName("cancel_subscription");
        
        assertThat(action)
            .isPresent()
            .get()
            .satisfies(a -> {
                assertThat(a.getDescription()).isNotEmpty();
                assertThat(a.getExamples()).isNotEmpty();
                assertThat(a.getCategory()).isEqualTo("subscription");
                assertThat(a.getRiskLevel()).isEqualTo("high");
                assertThat(a.getConfirmationRequired()).isTrue();
            });
    }
    
    @Test
    void shouldReturnEmptyForNonexistentAction() {
        Optional<ActionInfo> action = registry.getActionByName("nonexistent_action");
        assertThat(action).isEmpty();
    }
    
    @Test
    void shouldCheckActionExists() {
        assertThat(registry.actionExists("cancel_subscription")).isTrue();
        assertThat(registry.actionExists("nonexistent")).isFalse();
    }
    
    @Test
    void shouldGetActionsByCategory() {
        List<ActionInfo> subscriptionActions = registry.getActionsByCategory("subscription");
        
        assertThat(subscriptionActions)
            .isNotEmpty()
            .allMatch(a -> "subscription".equals(a.getCategory()));
    }
    
    @Test
    void shouldGetCategories() {
        List<String> categories = registry.getCategories();
        
        assertThat(categories)
            .isNotEmpty()
            .contains("subscription", "payment", "order");
    }
    
    @Test
    void shouldFormatForLLMPrompt() {
        String prompt = registry.formatForLLMPrompt();
        
        assertThat(prompt)
            .isNotEmpty()
            .contains("cancel_subscription")
            .contains("ACTION:");
    }
}
```

---

### STEP 9: Deploy and Monitor

**Checklist:**

- [ ] Run tests locally: `mvn test`
- [ ] Build application: `mvn clean package`
- [ ] Deploy to staging environment
- [ ] Test with real queries
- [ ] Monitor logs for action recognition
- [ ] Deploy to production
- [ ] Monitor metrics:
  - [ ] Action recognition accuracy
  - [ ] LLM choosing correct intents
  - [ ] Action execution success rate
  - [ ] No hallucinations for actions

---

## Verification Steps

After implementation, verify:

1. **Registry is initialized:**
```bash
# Check logs
grep "Registry initialized with" app.log
# Should show: "Registry initialized with XX total available actions"
```

2. **Actions are discoverable:**
```bash
# Test endpoint (if you expose it)
curl http://localhost:8080/api/ai/actions
# Should return: [{"action":"cancel_subscription", ...}, ...]
```

3. **LLM sees actions:**
```bash
# Test intent extraction
POST /api/intent-extract
{"query": "Cancel my subscription"}

# Should return:
{
  "type": "ACTION",
  "action": "cancel_subscription",
  "actionParams": {"subscriptionId": "..."}
}
```

4. **System context includes actions:**
```bash
# Check system context
POST /api/system-context
{"userId": "user-123"}

# Should include: "availableActions": [...]
```

---

## Troubleshooting

### Issue: Registry is empty
**Solution:**
- Check that services implement `AIActionProvider`
- Check that services are `@Service` annotated
- Check package scanning is enabled
- Restart application

### Issue: Actions not in LLM prompt
**Solution:**
- Check `IntentQueryExtractor` calls registry
- Check `formatForLLMPrompt()` is working
- Check prompt is being passed to LLM

### Issue: LLM doesn't recognize actions
**Solution:**
- Check examples in action definition
- Test with simpler queries first
- Check LLM is getting full prompt
- Consider fine-tuning examples

---

## Performance Optimization

For better performance:

```java
// Add caching (already done in registry with volatile)
// Cache is populated on first call and reused

// Add refresh endpoint (optional)
@PostMapping("/refresh-actions")
public void refreshActions() {
    registry.clearCache();
    registry.getAllAvailableActions();
}

// Add metrics (optional)
@GetMapping("/actions/metrics")
public Map<String, Integer> getMetrics() {
    return registry.getActionCountByCategory();
}
```

---

## What's Next?

After implementation:

1. ✅ Update more services (PaymentService, OrderService, etc.)
2. ✅ Add action execution logic in RAGOrchestrator
3. ✅ Add user confirmation flow
4. ✅ Add monitoring and analytics
5. ✅ Add new actions as business grows

---

## Summary

You now have:
- ✅ ActionInfo and ActionParameterInfo DTOs
- ✅ AIActionProvider interface
- ✅ AvailableActionsRegistry service
- ✅ Integrated with SystemContextBuilder
- ✅ Updated IntentQueryExtractor to use actions
- ✅ Unit tests
- ✅ Everything wired together

**Total time:** ~3 hours
**Result:** Professional, scalable, enterprise-ready action management

**You're done!** 🎉


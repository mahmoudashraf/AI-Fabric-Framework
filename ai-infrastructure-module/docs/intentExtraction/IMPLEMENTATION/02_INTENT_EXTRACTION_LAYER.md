# Layer 2: Intent Extraction with System Context

## Overview

Second layer extracts structured intents from (optionally redacted) user query using system context.

**Inputs:** Redacted query from Layer 1
**Outputs:** MultiIntentResponse with structured intents

---

## 🎯 Minimal Implementation

### Step 1: Build System Context (Library Code)

```java
@Service
public class SystemContextBuilder {
    
    @Autowired
    private AvailableActionsRegistry actionsRegistry;
    
    @Autowired
    private KnowledgeBaseOverviewService kbOverview;
    
    public SystemContext buildContext(String userId) {
        // Gather available actions
        List<ActionInfo> availableActions = actionsRegistry.getAllAvailableActions();
        
        // Gather knowledge base overview
        KnowledgeBaseOverview kbInfo = kbOverview.getOverview();
        
        return SystemContext.builder()
            .availableActions(availableActions)
            .knowledgeBaseOverview(kbInfo)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
    }
}

@Data
@Builder
public class SystemContext {
    private List<ActionInfo> availableActions;
    private KnowledgeBaseOverview knowledgeBaseOverview;
    private String userId;
    private LocalDateTime timestamp;
}
```

### Step 2: Build Enriched Prompt (Library Code)

```java
@Service
public class EnrichedPromptBuilder {
    
    @Autowired
    private SystemContextBuilder contextBuilder;
    
    public String buildSystemPrompt(String userId) {
        SystemContext context = contextBuilder.buildContext(userId);
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an intelligent RAG orchestrator.\n\n");
        
        // Add available actions
        prompt.append("AVAILABLE ACTIONS:\n");
        for (ActionInfo action : context.getAvailableActions()) {
            prompt.append(String.format(
                "- %s: %s (params: %s)\n",
                action.getName(),
                action.getDescription(),
                action.getParameters()
            ));
        }
        
        // Add KB overview
        prompt.append("\nKNOWLEDGE BASE:\n");
        prompt.append(String.format(
            "- Total documents: %d\n",
            context.getKnowledgeBaseOverview().getTotalIndexedDocuments()
        ));
        prompt.append(String.format(
            "- Document types: %s\n",
            context.getKnowledgeBaseOverview().getDocumentsByType()
        ));
        prompt.append(String.format(
            "- Last update: %s\n",
            context.getKnowledgeBaseOverview().getLastIndexUpdateTime()
        ));
        
        // Add extraction rules
        prompt.append("\nEXTRACTION RULES:\n");
        prompt.append("1. If user wants to perform an action → Return type: ACTION\n");
        prompt.append("2. If user wants information → Return type: INFORMATION\n");
        prompt.append("3. If query is outside scope → Return type: OUT_OF_SCOPE\n");
        prompt.append("4. If multiple intents → Return type: COMPOUND\n");
        
        // Add output format
        prompt.append("\nOUTPUT FORMAT (JSON):\n");
        prompt.append("""
            {
              "intents": [
                {
                  "type": "ACTION|INFORMATION|OUT_OF_SCOPE",
                  "intent": "specific_intent_name",
                  "confidence": 0.95,
                  "action": "action_name (if ACTION)",
                  "actionParams": {"key": "value"},
                  "vectorSpace": "which_vector_space_to_search",
                  "requiresRetrieval": true|false
                }
              ],
              "isCompound": false,
              "orchestrationStrategy": "DIRECT_ACTION|RETRIEVE_AND_GENERATE|ADMIT_UNKNOWN"
            }
            """);
        
        return prompt.toString();
    }
}
```

### Step 3: Intent Extraction Service (Library Code)

```java
@Service
public class IntentQueryExtractor {
    
    @Autowired
    private AICoreService aiCoreService;
    
    @Autowired
    private EnrichedPromptBuilder promptBuilder;
    
    public MultiIntentResponse extract(String query, String userId) {
        // Build enriched prompt with system context
        String systemPrompt = promptBuilder.buildSystemPrompt(userId);
        
        // Call LLM
        AITextGenerationRequest request = AITextGenerationRequest.builder()
            .systemMessage(systemPrompt)
            .userMessage(query)
            .model("gpt-4o-mini")
            .responseFormat(ResponseFormat.JSON)  // Structured output
            .build();
        
        String response = aiCoreService.generateText(request);
        
        // Parse JSON response
        MultiIntentResponse intents = parseResponse(response);
        
        return intents;
    }
    
    private MultiIntentResponse parseResponse(String response) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(response, MultiIntentResponse.class);
    }
}

@Data
@Builder
public class MultiIntentResponse {
    private List<Intent> intents;
    private boolean isCompound;
    private String orchestrationStrategy;  // DIRECT_ACTION, RETRIEVE_AND_GENERATE, ADMIT_UNKNOWN
}

@Data
@Builder
public class Intent {
    private IntentType type;                      // ACTION, INFORMATION, OUT_OF_SCOPE, COMPOUND
    private String intent;                        // Specific intent name
    private Double confidence;                    // 0.0 - 1.0
    private String action;                        // If type = ACTION
    private Map<String, Object> actionParams;     // If type = ACTION
    private String vectorSpace;                   // Which docs to search
    private Boolean requiresRetrieval;            // Should we retrieve?
    
    // NEW: Intelligent next-step recommendation (Layer 4)
    private NextStepRecommendation nextStepRecommended;
}

@Data
@Builder
public class NextStepRecommendation {
    private String intent;                        // show_refund_process
    private String query;                         // "What is the refund process?"
    private String rationale;                     // Why THIS user gets THIS recommendation
    private Double confidence;                    // 0.85 - how confident (0.0-1.0)
}

public enum IntentType {
    ACTION,              // Execute an action
    INFORMATION,         // Retrieve from documents
    OUT_OF_SCOPE,        // Can't help
    COMPOUND             // Multiple intents
}
```

---

## 📊 User Implementation: One Handler Per Action

### User Step 1: Create Handler for Each Action

Each action gets its own handler:

```java
// Handler 1: Cancel Subscription
@Service
public class CancelSubscriptionHandler implements ActionHandler {
    
    @Autowired
    private SubscriptionRepository subscriptionRepo;
    
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
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        Subscription sub = subscriptionRepo.findByUserId(userId);
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setCancelledAt(LocalDateTime.now());
        subscriptionRepo.save(sub);
        
        return ActionResult.builder()
            .success(true)
            .message("Subscription cancelled")
            .build();
    }
}

// Handler 2: Upgrade Subscription
@Service
public class UpgradeSubscriptionHandler implements ActionHandler {
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("upgrade_subscription")
            .description("Upgrade to higher plan")
            .category("subscription")
            .parameters(Map.of(
                "plan_id", "string (required)",
                "billing_cycle", "MONTHLY|YEARLY"
            ))
            .build();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // YOUR upgrade logic
    }
}

// Handler 3: Pause Subscription
@Service
public class PauseSubscriptionHandler implements ActionHandler {
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("pause_subscription")
            .description("Pause subscription temporarily")
            .parameters(Map.of("duration_months", "int (required)"))
            .build();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // YOUR pause logic
    }
}

// Handler 4: Update Payment Method
@Service
public class UpdatePaymentMethodHandler implements ActionHandler {
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("update_payment_method")
            .description("Update payment method")
            .category("payment")
            .parameters(Map.of("payment_method_id", "string (required)"))
            .build();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // YOUR payment update logic
    }
}

// Continue for each action...
```

### User Step 2: Registry Auto-Discovery (Library Code)

```java
@Service
public class AvailableActionsRegistry {
    
    @Autowired
    private List<AIActionProvider> providers;  // Spring auto-wires all implementations
    
    public List<ActionInfo> getAllAvailableActions() {
        List<ActionInfo> actions = new ArrayList<>();
        
        for (AIActionProvider provider : providers) {
            actions.addAll(provider.getAvailableActions());
        }
        
        return actions;
    }
}

@Data
@Builder
public class ActionInfo {
    private String name;                           // cancel_subscription
    private String description;                    // What it does
    private Map<String, String> parameters;        // Required params
}
```

---

## 🎯 Flow Example

### Input:
```
User Query (after PII redaction): 
"Cancel my subscription with reason: too expensive"

System Context:
- Available actions: cancel_subscription, upgrade_subscription, ...
- KB has: refund_policy, cancellation_process, ...
```

### LLM Processing:
```
System prompt includes:
- All available actions
- KB overview stats
- Extraction rules

User query processed by GPT-4o-mini
```

### Output:
```json
{
  "intents": [
    {
      "type": "ACTION",
      "intent": "cancel_subscription",
      "confidence": 0.98,
      "action": "cancel_subscription",
      "actionParams": {
        "reason": "too expensive",
        "effective_date": "2024-11-08"
      },
      "vectorSpace": "policies",
      "requiresRetrieval": false
    }
  ],
  "isCompound": false,
  "orchestrationStrategy": "DIRECT_ACTION"
}
```

---

## 📋 Implementation Checklist

**Library Code (Nothing for user):**
- [ ] SystemContextBuilder
- [ ] EnrichedPromptBuilder
- [ ] IntentQueryExtractor
- [ ] MultiIntentResponse DTO
- [ ] Intent DTO
- [ ] AvailableActionsRegistry

**User Code:**
- [ ] Implement AIActionProvider in SubscriptionService
- [ ] Implement AIActionProvider in PaymentService
- [ ] Implement AIActionProvider in OrderService
- [ ] Implement AIActionProvider in UserService
- [ ] Test with sample queries

---

## ✅ User Minimum Effort

**For each service (e.g., SubscriptionService):**

```java
@Service
public class SubscriptionService implements AIActionProvider {
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        // Just return your 3-5 actions per service
        return List.of(
            ActionInfo.builder()
                .name("cancel_subscription")
                .description("Cancel subscription")
                .parameters(Map.of("reason", "string"))
                .build()
            // ... 2-3 more actions
        );
    }
}
```

That's it! Registry auto-discovers them.

---

## 🔄 Complete Flow

```
Layer 1: PII Detection
    ↓
Redacted Query
    ↓
Layer 2: Intent Extraction (THIS LAYER)
    │
    ├─ Build SystemContext (available actions + KB overview)
    ├─ Build EnrichedPrompt with context
    ├─ Call LLM
    └─ Parse structured response
    ↓
MultiIntentResponse (structured intents)
    ↓
→ Layer 3: RAGOrchestrator
```

Next: Go to `03_RAG_ORCHESTRATOR_LAYER.md`


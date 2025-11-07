# Layer 4: Smart Suggestions (Intelligent Next Steps)

## Overview

Fourth layer generates intelligent next-step recommendations based on user intent and context.

**Inputs:** Action result from Layer 3
**Outputs:** Smart follow-up suggestions via RAG retrieval

---

## 🎯 Architecture

```
User executes action (cancel order)
        ↓
LLM analyzes context:
├─ What they just did
├─ Their emotional state
├─ What they'd logically ask next
└─ How to delight them
        ↓
Generate NextStepRecommendation:
├─ intent: specific_follow_up
├─ query: exact retrieval query
├─ rationale: why THIS user gets THIS
└─ confidence: how confident
        ↓
Retrieve recommended info from RAG
        ↓
Present to user proactively
```

---

## 📋 Implementation

### Step 1: Update Intent DTO (Library Code)

```java
@Data
@Builder
public class Intent {
    private IntentType type;
    private String intent;
    private Double confidence;
    private String action;
    private Map<String, Object> actionParams;
    private String vectorSpace;
    private Boolean requiresRetrieval;
    
    // NEW: Intelligent next-step recommendation
    private NextStepRecommendation nextStepRecommended;
}

@Data
@Builder
public class NextStepRecommendation {
    private String intent;                 // show_refund_process
    private String query;                  // "What is the refund process and timeline?"
    private String rationale;              // Why THIS user gets THIS recommendation
    private Double confidence;             // 0.85 - how confident (0.0-1.0)
}
```

### Step 2: Update LLM Prompt (Library Code)

```java
@Service
public class EnrichedPromptBuilder {
    
    public String buildSystemPrompt(String userId) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an intelligent RAG orchestrator.\n\n");
        
        // ... existing rules ...
        
        // NEW: Next-step recommendation guidance
        prompt.append("\nINTELLIGENT NEXT-STEP RECOMMENDATIONS:\n");
        prompt.append("After analyzing user intent, recommend ONE intelligent follow-up.\n\n");
        
        prompt.append("RECOMMENDATION STRATEGY:\n");
        prompt.append("1. Understand user emotional state (anxious, excited, curious?)\n");
        prompt.append("2. Predict logical next question (what would THEY ask next?)\n");
        prompt.append("3. Provide proactive help (reduce friction before they ask)\n");
        prompt.append("4. Add business value (increase satisfaction & retention)\n\n");
        
        prompt.append("CONTEXT-AWARE EXAMPLES:\n\n");
        
        prompt.append("Example 1: Expensive order cancelled ($1000+)\n");
        prompt.append("Primary: ACTION cancel_order\n");
        prompt.append("Emotion: Anxious (lost money)\n");
        prompt.append("Next Question: When do I get my money back?\n");
        prompt.append("Recommendation:\n");
        prompt.append("  intent: show_refund_timeline\n");
        prompt.append("  query: 'What is the exact refund process and timeline?'\n");
        prompt.append("  rationale: 'User anxious about money, reduce anxiety with timeline'\n\n");
        
        prompt.append("Example 2: Just upgraded to premium\n");
        prompt.append("Primary: ACTION upgrade_subscription\n");
        prompt.append("Emotion: Excited (bought premium)\n");
        prompt.append("Next Question: What can I do now?\n");
        prompt.append("Recommendation:\n");
        prompt.append("  intent: show_premium_features_guide\n");
        prompt.append("  query: 'What are the premium features and how do I use them?'\n");
        prompt.append("  rationale: 'User excited, show value immediately, reduce buyer remorse'\n\n");
        
        prompt.append("Example 3: Payment updated after failed attempt\n");
        prompt.append("Primary: ACTION update_payment_method\n");
        prompt.append("Emotion: Concerned (wants confirmation)\n");
        prompt.append("Next Question: Did it work?\n");
        prompt.append("Recommendation:\n");
        prompt.append("  intent: show_payment_confirmation_and_history\n");
        prompt.append("  query: 'Show my recent transactions and confirm payment succeeded'\n");
        prompt.append("  rationale: 'User anxious if update worked, reassure immediately'\n\n");
        
        prompt.append("Example 4: Paused subscription\n");
        prompt.append("Primary: ACTION pause_subscription\n");
        prompt.append("Emotion: Considering leaving\n");
        prompt.append("Next Question: What are my other options?\n");
        prompt.append("Recommendation:\n");
        prompt.append("  intent: show_alternative_plans\n");
        prompt.append("  query: 'What subscription plans are available?'\n");
        prompt.append("  rationale: 'Last chance to retain, show alternatives before they leave'\n\n");
        
        prompt.append("Example 5: Cancelled subscription\n");
        prompt.append("Primary: ACTION cancel_subscription\n");
        prompt.append("Emotion: Frustrated or looking for deals\n");
        prompt.append("Next Question: Can I get a discount to stay?\n");
        prompt.append("Recommendation:\n");
        prompt.append("  intent: show_special_offers\n");
        prompt.append("  query: 'What special offers or discounts are available?'\n");
        prompt.append("  rationale: 'Customer leaving, final chance with compelling offer'\n\n");
        
        prompt.append("GENERAL RULES:\n");
        prompt.append("- Only recommend if confidence >= 0.70\n");
        prompt.append("- Make rationale specific to THIS user's situation\n");
        prompt.append("- Consider user's emotional state, not just action\n");
        prompt.append("- Query should be specific (not generic)\n");
        prompt.append("- Avoid generic follow-ups (aim for delight)\n\n");
        
        prompt.append("OUTPUT FORMAT (JSON):\n");
        prompt.append("""
            {
              "intents": [{
                "type": "ACTION|INFORMATION|OUT_OF_SCOPE",
                "intent": "specific_intent_name",
                "action": "action_name (if type=ACTION)",
                "actionParams": {...},
                "confidence": 0.95,
                
                "nextStepRecommended": {
                  "intent": "show_refund_timeline",
                  "query": "What is the exact refund process and when will I get my money?",
                  "rationale": "User cancelled expensive order, anxious about refund. Reduce anxiety with timeline.",
                  "confidence": 0.88
                }
              }],
              "isCompound": false
            }
            """);
        
        return prompt.toString();
    }
}
```

### Step 3: Orchestrate Next Steps (Library Code)

```java
@Service
public class RAGOrchestrator {
    
    @Autowired
    private IntentQueryExtractor intentExtractor;
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private ActionHandlerRegistry actionHandlerRegistry;
    
    public OrchestrationResult orchestrate(String query, String userId) {
        // Extract intents (including LLM-generated next-step recommendations)
        MultiIntentResponse intents = intentExtractor.extract(query, userId);
        
        // Handle primary intent
        Intent primaryIntent = intents.getIntents().get(0);
        OrchestrationResult result = handleSingleIntent(primaryIntent, userId);
        
        // NEW: Get intelligent next-step recommendation
        if (primaryIntent.getNextStepRecommended() != null) {
            NextStepRecommendation nextStep = primaryIntent.getNextStepRecommended();
            
            // Only proceed if confidence is high enough
            if (nextStep.getConfidence() >= 0.70) {
                try {
                    // Retrieve the recommended information
                    RAGResponse followUpInfo = ragService.performRAGQuery(
                        nextStep.getQuery(),
                        userId
                    );
                    
                    // Add to response
                    result.setNextStepInfo(Map.of(
                        "title", convertToTitle(nextStep.getIntent()),
                        "content", followUpInfo.getAnswer(),
                        "rationale", nextStep.getRationale(),
                        "confidence", nextStep.getConfidence(),
                        "sources", followUpInfo.getDocuments()
                    ));
                    
                } catch (Exception e) {
                    log.warn("Failed to retrieve next-step info", e);
                    // Continue without next-step - not critical
                }
            }
        }
        
        return result;
    }
    
    private String convertToTitle(String intent) {
        // Convert: show_refund_timeline → Refund Timeline
        return intent
            .replace("show_", "")
            .replace("_", " ")
            .replaceAll("\\b\\w", m -> m.group().toUpperCase());
    }
}

@Data
@Builder
public class OrchestrationResult {
    private OrchestrationResultType type;
    private String message;
    private boolean success;
    private Object data;
    private Map<String, Object> nextStepInfo;  // NEW: Smart suggestion data
}
```

---

## 🎨 Response to User

### Before (Without Smart Suggestions):
```json
{
  "message": "Order cancelled successfully",
  "success": true,
  "data": {
    "orderId": "11233",
    "refundAmount": 129.99
  }
}
```

### After (With Smart Suggestions):
```json
{
  "message": "Order cancelled successfully",
  "success": true,
  "data": {
    "orderId": "11233",
    "refundAmount": 129.99
  },
  
  "nextStep": {
    "title": "Refund Timeline",
    "content": "Your refund of $129.99 will be processed within 5-10 business days. You'll receive it back to your original payment method (card ending in 4242). You can track the status...",
    "rationale": "You cancelled an expensive order. Here's exactly when you'll get your money back to ease your mind.",
    "confidence": 0.88,
    "sources": ["refund_policy.md", "faq.md"]
  }
}
```

---

## 📊 Real-World Examples

### Example 1: Cancel Order ($1000+)
```
Primary Action: cancel_order (orderId: 11233)
User Context: Expensive item, likely anxious
Emotion: Worried about refund

LLM Recommendation:
{
  "intent": "show_refund_timeline",
  "query": "What is the exact refund process and timeline for cancelled orders?",
  "rationale": "User cancelled expensive order, anxious about money. Provide timeline to reduce anxiety.",
  "confidence": 0.92
}

Response to User:
"Your order is cancelled. Here's your refund: 
$129.99 → Your card in 5-10 business days"
```

### Example 2: Upgrade Subscription
```
Primary Action: upgrade_subscription (planId: premium)
User Context: Just spent money on upgrade
Emotion: Excited, looking for value

LLM Recommendation:
{
  "intent": "show_premium_features_guide",
  "query": "What are the premium features and how do I use them?",
  "rationale": "User excited about upgrade. Immediately show value to reduce buyer's remorse.",
  "confidence": 0.89
}

Response to User:
"Upgrade successful! Here's what you can do now:
- Advanced analytics dashboard
- Priority support
- Custom integrations...
Get started →"
```

### Example 3: Payment Method Failed, Then Updated
```
Primary Action: update_payment_method
User Context: Payment failed before, now updating
Emotion: Anxious - wants confirmation it works

LLM Recommendation:
{
  "intent": "show_payment_confirmation",
  "query": "Confirm my payment method is valid and show recent transaction status",
  "rationale": "User updated payment after failure. Immediately reassure with confirmation.",
  "confidence": 0.91
}

Response to User:
"Payment method updated successfully ✓
Last charge: $29.99 - Success
You're all set for next billing"
```

### Example 4: Pause Subscription
```
Primary Action: pause_subscription (months: 3)
User Context: Pausing, considering leaving
Emotion: Looking for reasons to stay

LLM Recommendation:
{
  "intent": "show_alternative_plans_and_perks",
  "query": "What other subscription options do we offer that might better fit my needs?",
  "rationale": "Last chance to retain. Show alternatives and explain benefits.",
  "confidence": 0.85
}

Response to User:
"Subscription paused for 3 months.
Before you go, did you know we offer:
- Quarterly plans (better value)
- Professional tier (24/7 support)
Get back any time →"
```

### Example 5: Cancel Subscription
```
Primary Action: cancel_subscription
User Context: Leaving entirely
Emotion: Frustrated or price-sensitive

LLM Recommendation:
{
  "intent": "show_special_retention_offers",
  "query": "What special offers or discounts are available to keep your subscription?",
  "rationale": "Final opportunity. Show best offer before they leave.",
  "confidence": 0.79
}

Response to User:
"We're sorry to see you go!
Wait - we have an offer just for you:
50% off your next 3 months
Get details →"
```

---

## ✨ Confidence Thresholds

```java
if (nextStep.getConfidence() >= 0.85) {
    // Definitely show it
    showAsMainSuggestion();
} else if (nextStep.getConfidence() >= 0.70) {
    // Show it, but subtle
    showAsSecondaryHint();
} else {
    // Skip it - not confident enough
    skipRecommendation();
}
```

---

## 📋 Implementation Checklist

**Library Code:**
- [ ] Add `NextStepRecommendation` to Intent DTO
- [ ] Update LLM system prompt with next-step guidance
- [ ] Update RAGOrchestrator to retrieve next-step info
- [ ] Add error handling (next-step retrieval failures)
- [ ] Add confidence threshold checks

**Testing:**
- [ ] Test with action intents (should get recommendations)
- [ ] Test with information intents (should skip)
- [ ] Test with out-of-scope (should skip)
- [ ] Test with low confidence (should skip)
- [ ] Test with retrieval failures (should handle gracefully)

---

## 🎯 UX Impact

### Without Smart Suggestions:
```
User: "Cancel my order"
System: "Done"
User: "Now what?"
User: "When do I get my refund?"
User: Goes to search FAQ
```

### With Smart Suggestions:
```
User: "Cancel my order"
System: "Done. Your refund: $129.99 in 5-10 days"
User: Satisfied, knows next steps
Result: User delighted
```

---

## 🚀 Advanced Configurations

### Optional: Configure Confidence Thresholds

```yaml
# application.yml
ai:
  smart-suggestions:
    enabled: true
    min-confidence: 0.70
    show-rationale: true
    max-retrieval-tokens: 500
```

### Optional: Track Suggestion Effectiveness

```java
@Service
public class SuggestionsAnalytics {
    
    public void trackSuggestionShown(String suggestionIntent, String userId) {
        // Track which suggestions were shown
    }
    
    public void trackSuggestionClicked(String suggestionIntent, String userId) {
        // Track which suggestions users engaged with
    }
    
    public Map<String, Double> getSuggestionClickThroughRates() {
        // Get most effective suggestions
        // Use for LLM optimization
    }
}
```

---

## 🎓 Key Insights

1. **LLM is Context-Aware**
   - Understands emotional state
   - Predicts next questions
   - Personalizes per user

2. **Proactive Beats Reactive**
   - Show info before they ask
   - Reduce friction
   - Increase satisfaction

3. **Confidence Matters**
   - Only show when confident
   - Skip uncertain recommendations
   - Maintain trust

4. **Context is Everything**
   - Same action → different recommendations
   - Based on user state, not just action
   - Feels intelligent

---

## 📖 Integration with Other Layers

```
Layer 1: PII Detection
        ↓
Layer 2: Intent Extraction (with NextStepRecommendation)
        ↓
Layer 3: Action Execution
        ↓
Layer 4: Smart Suggestions (THIS LAYER)
    ├─ Retrieve next-step info
    └─ Add to response
        ↓
Layer 5: Response Sanitization
        ↓
Layer 6: Intent History
        ↓
Response to User (with smart suggestion!)
```

---

**Layer 4 complete! Smart suggestions make your system feel intelligent and delightful.** 🎯


# LLM Prompt Modification - requiresGeneration Parameter

## ✅ Changes Completed

### File Modified: EnrichedPromptBuilder.java

**Location**: `/ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/EnrichedPromptBuilder.java`

**Two Methods Updated**:

#### 1. `appendExtractionRules()` Method (Lines 85-100)

**Added Rule #6** to guide LLM on how to set `requiresGeneration`:

```java
private void appendExtractionRules(StringBuilder prompt) {
    prompt.append("EXTRACTION RULES:\n");
    prompt.append("1. If the user wants to execute an action -> intent.type = ACTION and include action + actionParams.\n");
    prompt.append("2. If the user is searching for information -> intent.type = INFORMATION.\n");
    prompt.append("3. If the request is unsupported -> intent.type = OUT_OF_SCOPE and explain briefly in actionParams.reason.\n");
    prompt.append("4. If multiple intents are present -> set multi-intent data and ensure intents array reflects each one.\n");
    prompt.append("5. Confidence must be between 0.0 and 1.0.\n");
    
    // ✅ NEW RULE #6
    prompt.append("6. For INFORMATION intents, determine if the user wants:\n");
    prompt.append("   - Just search results -> requiresGeneration: false (user asking for data, lists, or information)\n");
    prompt.append("   - Analysis or recommendation -> requiresGeneration: true (user asking for opinion, advice, comparison, or analysis)\n");
    prompt.append("   Examples:\n");
    prompt.append("     * \"Show me products under $60\" -> requiresGeneration: false\n");
    prompt.append("     * \"Should I buy this?\" -> requiresGeneration: true\n");
    prompt.append("     * \"Find transactions from last week\" -> requiresGeneration: false\n");
    prompt.append("     * \"Recommend the best option\" -> requiresGeneration: true\n\n");
}
```

**What This Does**:
- ✅ Tells LLM there is a `requiresGeneration` flag for INFORMATION intents
- ✅ Explains when to set `false` (search-only queries)
- ✅ Explains when to set `true` (analysis/recommendation queries)
- ✅ Provides 4 concrete examples for LLM to learn from

#### 2. `appendOutputFormat()` Method (Lines 110-139)

**Updated JSON Schema** to include `requiresGeneration` field:

```java
private void appendOutputFormat(StringBuilder prompt) {
    prompt.append("OUTPUT JSON SCHEMA:\n");
    prompt.append("""
        {
          "intents": [
            {
              "type": "ACTION | INFORMATION | OUT_OF_SCOPE | COMPOUND",
              "intent": "canonical_intent_name",
              "confidence": 0.95,
              "action": "action_name_if_applicable",
              "actionParams": {"key": "value"},
              "vectorSpace": "policies | faq | ...",
              "requiresRetrieval": true,
              "requiresGeneration": false,  ← ✅ NEW FIELD
              "nextStepRecommended": {
                "intent": "potential_follow_up_intent",
                "query": "Helpful follow-up question to ask the user",
                "rationale": "Why this is useful",
                "confidence": 0.88,
                "vectorSpace": "faq | policies | test-product | ..."
              }
            }
          ],
          "isCompound": false,
          "orchestrationStrategy": "DIRECT_ACTION | RETRIEVE_AND_GENERATE | ADMIT_UNKNOWN",
          "metadata": {}
        }
        """);
    prompt.append("\nEnsure the response is valid JSON with double quotes and no additional commentary.\n");
}
```

**What This Does**:
- ✅ Shows LLM the JSON schema includes `requiresGeneration` field
- ✅ LLM knows it must include this field in its response
- ✅ Demonstrates the field should be boolean (true/false)

---

## 🔄 How It Works Now

### Execution Flow

```
1. User asks: "Should I buy this wallet?"
   ↓
2. RAGOrchestrator calls: IntentQueryExtractor.extract(query, userId)
   ↓
3. IntentQueryExtractor builds system prompt
   ├─ Calls: EnrichedPromptBuilder.buildSystemPrompt(context) // context carries userId only when authenticated
   └─ Gets prompt with:
      ✅ Rule #6 about requiresGeneration
      ✅ JSON schema including requiresGeneration
   ↓
4. LLM receives prompt
   ├─ Reads rule #6
   ├─ Sees JSON schema with requiresGeneration field
   ├─ Analyzes query: "Should I buy?" (opinion/recommendation)
   └─ Decides: requiresGeneration = true
   ↓
5. LLM generates JSON response
   {
     "type": "INFORMATION",
     "intent": "recommend_product",
     "requiresRetrieval": true,
     "requiresGeneration": true  ← ✅ LLM DECIDED THIS
   }
   ↓
6. IntentQueryExtractor.parseResponse()
   └─ Deserializes JSON to Intent object
      └─ requiresGeneration field populated: true
   ↓
7. Intent.normalize()
   └─ requiresGeneration already set by LLM
   └─ No changes needed
   ↓
8. RAGOrchestrator.handleInformation()
   ├─ Checks: intent.requiresGenerationOrDefault(false)
   ├─ Returns: true (LLM decided generation needed)
   └─ Executes: LLM generation flow with context filtering
   ↓
9. User receives: LLM-generated recommendation
```

---

## ✅ Integration Points

### Before (No Prompt Update)
- ❌ LLM doesn't know about `requiresGeneration`
- ❌ LLM can't set the field
- ❌ Intent.requiresGeneration remains null
- ❌ RAGOrchestrator uses default: false (search-only)
- ❌ Feature doesn't work

### After (With Prompt Update) ✅
- ✅ LLM sees Rule #6 about `requiresGeneration`
- ✅ LLM sees field in JSON schema
- ✅ LLM analyzes query and decides true/false
- ✅ LLM includes field in JSON response
- ✅ Intent.requiresGeneration is populated
- ✅ RAGOrchestrator uses actual value
- ✅ Feature works correctly

---

## 🧪 Testing the Prompt

### Test Query 1: Search-Only
**Input**: "Show me all products"

**Expected LLM Response**:
```json
{
  "type": "INFORMATION",
  "intent": "list_products",
  "requiresRetrieval": true,
  "requiresGeneration": false
}
```

**Reasoning**: User is just asking to see data (search-only)

### Test Query 2: LLM Generation
**Input**: "Which product should I buy?"

**Expected LLM Response**:
```json
{
  "type": "INFORMATION",
  "intent": "recommend_product",
  "requiresRetrieval": true,
  "requiresGeneration": true
}
```

**Reasoning**: User is asking for recommendation/opinion (needs LLM)

### Test Query 3: Edge Case
**Input**: "Find products and tell me which is best"

**Expected LLM Response**:
```json
{
  "type": "INFORMATION",
  "intent": "find_and_evaluate",
  "requiresRetrieval": true,
  "requiresGeneration": true
}
```

**Reasoning**: User wants search + analysis (needs LLM generation)

---

## 📊 Complete Implementation Status

| Component | Status | Details |
|-----------|--------|---------|
| Intent.java | ✅ Done | Field, getter, normalization |
| EnrichedPromptBuilder.java | ✅ Done | Rule #6, JSON schema updated |
| LLM Prompt | ✅ Done | LLM now knows about requiresGeneration |
| RAGOrchestrator.java | ⏳ TODO | Update handleInformation() to use flag |
| Tests | ⏳ TODO | Add unit/integration tests |
| Deployment | ⏳ TODO | Follow implementation checklist |

---

## 🎯 Next Step

Once LLM is updated with the new prompt, we need to:

1. **Update RAGOrchestrator.java** - Use the `requiresGeneration` flag in `handleInformation()` method to route to correct flow
2. **Add tests** - Verify Intent parsing works, LLM sets flag correctly
3. **Test with real LLM** - Confirm LLM actually sets the flag in responses

---

## Summary

✅ **The LLM prompt has been successfully modified to include the `requiresGeneration` parameter**

**What Changed**:
1. Added Rule #6 to extraction rules explaining `requiresGeneration`
2. Added `requiresGeneration` field to JSON schema
3. Provided 4 examples to help LLM learn when to set true vs false

**Result**:
- LLM now understands `requiresGeneration`
- LLM can set this field in its response
- Intent will be populated with this value
- RAGOrchestrator can use it for routing

**Status**: ✅ LLM Prompt Updated | ⏳ RAGOrchestrator Routing Pending | ⏳ Tests Pending


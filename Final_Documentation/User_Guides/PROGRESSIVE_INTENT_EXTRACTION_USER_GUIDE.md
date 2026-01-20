# Progressive Intent Extraction User Guide

## Overview

Progressive Intent Extraction is a resilient, provider-agnostic system for extracting structured intents from user queries. It uses a **bounded fallback ladder** to handle real-world variations in LLM outputs across different providers (OpenAI, Anthropic, Cohere, Gemini, Azure).

This guide explains how to configure, use, and troubleshoot the progressive extraction system.

---

## What Progressive Intent Extraction Solves

### The Problem

Different LLM providers produce varying output shapes for the same logical intent:
- Malformed or truncated JSON
- Missing required fields
- Inconsistent action naming
- Incomplete action parameters
- Structural schema violations

Without progressive extraction, these variations cause extraction failures and require provider-specific workarounds.

### The Solution

Progressive Intent Extraction applies a **smart fallback ladder** that attempts multiple strategies in sequence:

1. **Compound** (fast-path) — Single request with full schema
2. **Repair** (structural fix) — Fix JSON/schema errors
3. **Completion** (contract fill) — Fill missing required fields
4. **Multi-step** (decomposed) — Break into smaller, validated prompts

Each step is:
- **Bounded** by cost/latency limits
- **Provider-agnostic** (no provider-specific code)
- **Observable** (diagnostics show which path was used)
- **LLM-driven** (semantic decisions by LLM, contract enforcement by code)

---

## How It Works

### Fallback Ladder Flow

```
User Query
    ↓
┌───────────────────────┐
│ 1. Compound Strategy  │ ← Fast-path: single request
│   (single LLM call)   │
└───────────────────────┘
    ↓ (structural failure)
┌───────────────────────┐
│ 2. Repair Strategy    │ ← Fix JSON/schema errors
│   (≤ repairMaxAttempts)│
└───────────────────────┘
    ↓ (contract-incomplete)
┌───────────────────────┐
│ 3. Completion Strategy│ ← Fill missing required fields
│ (≤ completionMaxAttempts)│
└───────────────────────┘
    ↓ (still failing)
┌───────────────────────┐
│ 4. Multi-step Strategy│ ← Decomposed prompts
│   (classify → select) │   (requires ≥3 remaining calls)
└───────────────────────┘
    ↓
 Final Result
```

### Processing Pipeline for Each Step

For each extraction attempt:

1. **Parse** — Convert LLM output to `MultiIntentResponse`
2. **Post-process** — Apply deterministic normalization (no LLM calls)
3. **Validate** — Check contract completeness and safety
4. **Assess** — Determine error category (STRUCTURAL / INCOMPLETE / UNSAFE / OTHER)
5. **Decide** — Success → return, or fall back to next strategy

### Error Categories

| Category | Meaning | Next Step |
|----------|---------|-----------|
| **STRUCTURAL** | Invalid JSON, schema mismatch, parse errors | → Repair |
| **INCOMPLETE** | Valid JSON but missing required fields | → Completion |
| **UNSAFE** | Unknown actions, invalid values | → Completion |
| **OTHER** | Post-processing or validation errors | → Next strategy |

---

## Configuration

### Basic Configuration

Configure progressive extraction under `ai.intent-extraction.progressive.*`:

```yaml
ai:
  intent-extraction:
    progressive:
      # Enable progressive fallback (default: true)
      enabled: true

      # Enable repair step for structural errors (default: true)
      repairEnabled: true

      # Max repair attempts (0-3, default: 1)
      repairMaxAttempts: 1

      # Enable completion step for incomplete contracts (default: true)
      completionEnabled: true

      # Max completion attempts (0-3, default: 1)
      completionMaxAttempts: 1

      # Enable multi-step fallback (default: true)
      multiStepEnabled: true

      # Max total LLM calls per request (1-10, default: 5)
      # Cost control: prevents unbounded retry loops
      maxTotalLlmCalls: 5

      # Force specific extraction mode for debugging
      # Values: compound, repair, completion, multi_step, auto (or blank)
      # forceMode: auto
```

### Environment Variables

You can override configuration via environment variables:

```bash
# Enable/disable progressive extraction
AI_INTENT_EXTRACTION_PROGRESSIVE_ENABLED=true

# Configure max attempts
AI_INTENT_EXTRACTION_PROGRESSIVE_REPAIR_MAX_ATTEMPTS=1
AI_INTENT_EXTRACTION_PROGRESSIVE_COMPLETION_MAX_ATTEMPTS=1

# Cost control
AI_INTENT_EXTRACTION_PROGRESSIVE_MAX_TOTAL_LLM_CALLS=5
```

### Recommended Profiles

#### Production (Optimized for Cost/Speed)
```yaml
ai:
  intent-extraction:
    progressive:
      enabled: true
      repairEnabled: true
      repairMaxAttempts: 1
      completionEnabled: true
      completionMaxAttempts: 1
      multiStepEnabled: true
      maxTotalLlmCalls: 5
```

#### Development (Optimized for Debugging)
```yaml
ai:
  intent-extraction:
    progressive:
      enabled: true
      repairEnabled: true
      repairMaxAttempts: 2
      completionEnabled: true
      completionMaxAttempts: 2
      multiStepEnabled: true
      maxTotalLlmCalls: 7
      # Force specific mode for testing
      # forceMode: completion
```

#### CI/Testing (Optimized for Stability)
```yaml
ai:
  intent-extraction:
    progressive:
      enabled: true
      repairEnabled: true
      repairMaxAttempts: 1
      completionEnabled: true
      completionMaxAttempts: 1
      multiStepEnabled: true
      maxTotalLlmCalls: 6
```

---

## Understanding Diagnostics

Progressive extraction includes detailed diagnostics to help you understand which path was used and why.

### Diagnostics Structure

The extraction output includes a `diagnostics` map with:

```json
{
  "extractionPath": "completion",
  "extractionAttempts": 3,
  "llmCalls": 3,
  "attempts": [
    {
      "strategy": "compound",
      "success": false,
      "llmCalls": 1,
      "errorCategory": "INCOMPLETE",
      "issueCodes": ["MISSING_REQUIRED_PARAM"]
    },
    {
      "strategy": "completion",
      "success": true,
      "llmCalls": 1
    }
  ]
}
```

### Diagnostics Fields

| Field | Description |
|-------|-------------|
| `extractionPath` | Final successful strategy: `compound`, `repair`, `completion`, `multi_step`, or `fallback` |
| `extractionAttempts` | Total number of attempts made |
| `llmCalls` | Total LLM API calls used |
| `attempts[]` | Detailed log of each attempt |

### Per-Attempt Fields

| Field | Description |
|-------|-------------|
| `strategy` | Strategy name: `compound`, `repair`, `completion`, or `multi_step` |
| `success` | Whether this attempt succeeded |
| `llmCalls` | LLM calls used by this attempt |
| `errorCategory` | Error type if failed: `STRUCTURAL`, `INCOMPLETE`, `UNSAFE`, `OTHER` |
| `issueCodes` | Validation issue codes (e.g., `MISSING_REQUIRED_PARAM`, `UNKNOWN_ACTION`) |
| `errors` | Human-readable error messages |
| `normalization` | Normalization rules applied (if any) |

---

## Common Scenarios

### Scenario 1: Compound Success (Ideal Path)

**User Query:** "Find all customers in California"

**Flow:**
1. Compound strategy returns valid, complete JSON
2. Post-processing applies normalization
3. Validation passes
4. **Result:** Success on first attempt

**Diagnostics:**
```json
{
  "extractionPath": "compound",
  "extractionAttempts": 1,
  "llmCalls": 1
}
```

### Scenario 2: Structural Failure → Repair

**User Query:** "Show me the dashboard"

**Flow:**
1. Compound strategy returns malformed JSON (missing closing brace)
2. Repair strategy fixes JSON structure
3. Validation passes
4. **Result:** Success after repair

**Diagnostics:**
```json
{
  "extractionPath": "repair",
  "extractionAttempts": 2,
  "llmCalls": 2,
  "attempts": [
    {
      "strategy": "compound",
      "success": false,
      "errorCategory": "STRUCTURAL"
    },
    {
      "strategy": "repair",
      "success": true
    }
  ]
}
```

### Scenario 3: Missing Required Params → Completion

**User Query:** "relationship_query: Find customer connections"

**Flow:**
1. Compound strategy returns ACTION but missing `actionParams.query`
2. Completion strategy fills missing parameter using user query
3. Validation passes
4. **Result:** Success after completion

**Diagnostics:**
```json
{
  "extractionPath": "completion",
  "extractionAttempts": 2,
  "llmCalls": 2,
  "attempts": [
    {
      "strategy": "compound",
      "success": false,
      "errorCategory": "INCOMPLETE",
      "issueCodes": ["MISSING_REQUIRED_PARAM"]
    },
    {
      "strategy": "completion",
      "success": true
    }
  ]
}
```

### Scenario 4: Complex Query → Multi-step

**User Query:** "Search for products, then summarize the results"

**Flow:**
1. Compound strategy fails (complex multi-part intent)
2. Repair fails (not a structural issue)
3. Multi-step breaks into: classify → select actions → fill params
4. **Result:** Success after multi-step decomposition

**Diagnostics:**
```json
{
  "extractionPath": "multi_step",
  "extractionAttempts": 3,
  "llmCalls": 4
}
```

---

## Debugging Guide

### Enable Debug Mode

To force a specific extraction mode for debugging:

```yaml
ai:
  intent-extraction:
    progressive:
      forceMode: completion  # or: compound, repair, multi_step
```

This bypasses the normal fallback ladder and only uses the specified strategy.

### Common Issues and Solutions

#### Issue: High `llmCalls` Count

**Symptom:** Diagnostics show 5+ LLM calls per request

**Possible Causes:**
- Multiple repair/completion attempts failing
- Complex queries requiring multi-step decomposition
- Provider consistently producing incomplete outputs

**Solutions:**
1. Review `maxTotalLlmCalls` limit (reduce if too high)
2. Check provider quality (consider switching to more reliable provider for orchestration)
3. Review prompt templates for clarity
4. Analyze `issueCodes` to identify recurring validation failures

#### Issue: Extraction Always Falls Back

**Symptom:** `extractionPath` is always `fallback`

**Possible Causes:**
- Budget exhausted before success (`llmCalls >= maxTotalLlmCalls`)
- All strategies consistently failing validation
- Provider connection issues

**Solutions:**
1. Increase `maxTotalLlmCalls` if budget allows
2. Enable debug snapshots to see actual provider outputs
3. Check provider connectivity and credentials
4. Review validation rules (may be too strict)

#### Issue: Missing Required Parameters

**Symptom:** `issueCodes` includes `MISSING_REQUIRED_PARAM`

**Possible Causes:**
- Action handlers require parameters not specified in metadata
- User query lacks necessary information
- Completion strategy not enabled

**Solutions:**
1. Ensure `completionEnabled: true`
2. Update action handler metadata to declare required parameters
3. Review user query for completeness
4. Check completion prompts for clarity

#### Issue: Unknown Action Errors

**Symptom:** `issueCodes` includes `UNKNOWN_ACTION` or `UNREGISTERED_ACTION`

**Possible Causes:**
- Provider inventing action names not in registry
- Action handler not properly registered
- Normalization rules not mapping action name correctly

**Solutions:**
1. Verify action handler is registered in `ActionHandlerRegistry`
2. Check action name mapping in post-processor
3. Review allowed actions list in prompts
4. Enable completion to guide LLM toward registered actions

---

## Best Practices

### 1. Start with Defaults

The default configuration is optimized for most use cases. Only adjust settings when you have specific requirements or observed issues.

### 2. Monitor Diagnostics

Use diagnostics to understand extraction patterns:
- If `extractionPath` is mostly `compound`: excellent (fast, cheap)
- If `extractionPath` is mostly `repair`: acceptable (structural fixes common)
- If `extractionPath` is mostly `completion`: review prompt clarity
- If `extractionPath` is mostly `multi_step`: consider query complexity or provider quality

### 3. Use Appropriate `maxTotalLlmCalls`

- **Low budget (3-4 calls):** Fast but may fall back on complex queries
- **Medium budget (5-6 calls):** Balanced (recommended for production)
- **High budget (7-10 calls):** Maximum resilience but higher cost

### 4. Tune by Provider

Different providers have different reliability profiles:
- **High-quality structured output providers** (e.g., GPT-4, Claude 3.5): Lower repair/completion needs
- **Less predictable providers**: May need higher attempt limits
- **Cost-sensitive scenarios**: Use cheaper provider for orchestration, reserve premium for generation

### 5. Separate Orchestration from Generation

Configure different LLM providers for different purposes:

```yaml
ai:
  providers:
    # Default provider
    llm-provider: openai

    # Orchestration (intent extraction, planning)
    orchestration:
      llm-provider: cohere  # fast, good at structured outputs
      temperature: 0.1      # low temp for predictability
      maxTokens: 1200

    # Generation (final narrative responses)
    generation:
      llm-provider: openai  # high quality for user-facing text
      model: gpt-4o
      temperature: 0.3
      maxTokens: 2000
```

---

## Integration with Normalization

Progressive extraction works seamlessly with the **Orchestration Result Normalization** layer:

1. **Extraction** produces a `MultiIntentResponse`
2. **Post-processing** applies deterministic normalization (no LLM calls)
   - Action name canonicalization
   - OUT_OF_SCOPE handling
   - Relationship query parameter validation
3. **Validation** checks contract completeness
4. **Orchestration** executes the normalized intent

The normalization layer ensures provider-agnostic results even when different extraction strategies are used.

**Related Guide:** `NORMALIZATION_AND_ORCHESTRATION_GUIDE.md`

---

## Advanced Configuration

### Orchestration-Specific LLM Settings

You can configure a separate LLM provider specifically for intent extraction:

```yaml
ai:
  providers:
    orchestration:
      llm-provider: cohere
      model: command-r-plus
      temperature: 0.1
      maxTokens: 1500
```

This allows you to:
- Use a cheaper/faster provider for extraction
- Reserve premium providers for generation
- Optimize temperature/tokens for structured outputs

### Action Metadata Requirements

For completion to work effectively, action handlers must declare their metadata:

```java
@Component
@AIAction(
    name = "relationship_query",
    description = "Execute a relationship query across connected entities",
    requiredParameters = {"query"}
)
public class RelationshipQueryActionHandler implements ActionHandler {
    @Override
    public AIActionMetaData getMetadata() {
        return AIActionMetaData.builder()
            .name("relationship_query")
            .description("Execute a relationship query")
            .requiredParameters(Set.of("query"))
            .parameters(Map.of(
                "query", "The relationship query string"
            ))
            .build();
    }
    // ... handler implementation
}
```

The completion strategy uses this metadata to:
- Validate required parameters are present
- Guide the LLM to fill missing parameters correctly
- Provide parameter descriptions in completion prompts

---

## Metrics and Observability

### Key Metrics to Track

1. **Extraction Path Distribution**
   - Percentage using: compound, repair, completion, multi_step, fallback
   - Goal: >80% compound for healthy system

2. **Average LLM Calls per Request**
   - Track over time to detect degradation
   - Goal: <2 calls average

3. **Fallback Rate**
   - Percentage of requests reaching fallback
   - Goal: <5%

4. **Strategy Success Rates**
   - Success rate per strategy
   - Identify which strategies need tuning

### Debug Snapshots

Enable orchestration debug snapshots for detailed failure analysis:

```yaml
ai:
  orchestration:
    result-normalization:
      enabled: true
      debugSnapshotEnabled: true  # Enable in CI/debugging only
```

Snapshots include:
- Normalized result metadata
- Extraction diagnostics
- Validation issue codes
- Applied normalization rules

**Warning:** Only enable in non-production environments (contains processing metadata).

---

## Migration Guide

### Upgrading from Non-Progressive Extraction

Progressive extraction is **backward compatible**. To enable:

1. Update configuration:
```yaml
ai:
  intent-extraction:
    progressive:
      enabled: true  # Enable progressive fallback
```

2. Monitor diagnostics in logs

3. Tune settings based on observed patterns

### Disabling Progressive Extraction

To revert to single-strategy extraction:

```yaml
ai:
  intent-extraction:
    progressive:
      enabled: false
```

Or force compound-only mode:

```yaml
ai:
  intent-extraction:
    progressive:
      enabled: true
      repairEnabled: false
      completionEnabled: false
      multiStepEnabled: false
```

---

## Troubleshooting Checklist

- [ ] Is `progressive.enabled: true`?
- [ ] Are repair/completion/multi-step strategies enabled as needed?
- [ ] Is `maxTotalLlmCalls` sufficient for your queries?
- [ ] Are action handlers properly registered with metadata?
- [ ] Are provider credentials valid and connectivity working?
- [ ] Are diagnostics showing which path is being used?
- [ ] Have you reviewed validation `issueCodes` for patterns?
- [ ] Are normalization rules being applied correctly?

---

## Related Documentation

- **System Architecture:** `PROGRESSIVE_INTENT_EXTRACTION_ARCHITECTURE_GUIDE.md`
- **Normalization:** `NORMALIZATION_AND_ORCHESTRATION_GUIDE.md`
- **Provider Configuration:** `CONFIGURATION_AND_OPTIMIZATION_GUIDE.md`
- **Real API Testing:** `REALAPI_PROVIDER_MATRIX_TESTING_GUIDE.md`

---

## Support

For issues or questions:
- Review diagnostics output for extraction path details
- Check logs for validation errors and issue codes
- Consult architecture guide for deep-dive on strategies
- File GitHub issues with diagnostics output attached

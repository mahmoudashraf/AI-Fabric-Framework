# Progressive Intent Extraction — Architecture Guide

## Overview

This guide provides a deep technical dive into the **Progressive Intent Extraction** system architecture, implementation patterns, extension points, and design decisions.

**Audience:** Developers, architects, and contributors who need to understand or modify the extraction system.

---

## Table of Contents

1. [Architecture Principles](#architecture-principles)
2. [System Components](#system-components)
3. [Fallback Ladder Design](#fallback-ladder-design)
4. [Extraction Strategies](#extraction-strategies)
5. [Validation & Error Categorization](#validation--error-categorization)
6. [Post-Processing Pipeline](#post-processing-pipeline)
7. [Diagnostics & Observability](#diagnostics--observability)
8. [Configuration & Properties](#configuration--properties)
9. [Extension Points](#extension-points)
10. [Testing Strategy](#testing-strategy)
11. [Design Decisions (ADR-style)](#design-decisions-adr-style)

---

## Architecture Principles

The progressive extraction system follows these core principles:

### 1. Provider-Agnostic Design
- **No provider-specific code** in extraction logic
- Same ladder logic for OpenAI, Anthropic, Cohere, Gemini, Azure
- Provider differences handled by adapter layer (`AICoreService`)

### 2. LLM-Driven Semantics, Code-Driven Contracts
- **LLM decides** semantic meaning (intent classification, action selection)
- **Code enforces** structural contracts (schemas, required fields, action registry)
- No string parsing heuristics for semantic interpretation

### 3. Bounded Cost & Latency
- Hard limit on total LLM calls per request (`maxTotalLlmCalls`)
- No unbounded retry loops
- Fail-fast with structured fallback when budget exhausted

### 4. Deterministic First, LLM Fallback
- **Post-processing** (deterministic normalization) runs before validation
- Avoid LLM calls when deterministic rules can fix the issue
- LLM strategies only invoked for issues that require semantic interpretation

### 5. Observable & Debuggable
- Detailed diagnostics for every extraction attempt
- Validation issue codes for pattern analysis
- Extraction path tracking for metrics/monitoring

---

## System Components

### Component Diagram

```
┌─────────────────────────────────────────────────────────┐
│           ProgressiveIntentExtractionEngine             │
│                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  Compound    │  │   Repair     │  │  Completion  │ │
│  │  Strategy    │→ │  Strategy    │→ │  Strategy    │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│                                            ↓           │
│                                    ┌──────────────┐   │
│                                    │  Multi-Step  │   │
│                                    │  Strategy    │   │
│                                    └──────────────┘   │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│        IntentExtractionPostProcessor                    │
│  (Deterministic normalization, no LLM calls)            │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│          IntentExtractionValidator                      │
│  (Contract validation, issue codes, error categories)   │
└─────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Responsibility | Type |
|-----------|---------------|------|
| `ProgressiveIntentExtractionEngine` | Orchestrates fallback ladder, budget tracking, diagnostics | Orchestrator |
| `CompoundIntentExtractionStrategy` | Fast-path single-request extraction | Strategy |
| `RepairIntentExtractionStrategy` | Structural JSON/schema repair | Strategy |
| `CompletionIntentExtractionStrategy` | Contract-incomplete field completion | Strategy |
| `MultiStepIntentExtractionStrategy` | Decomposed multi-step extraction | Strategy |
| `IntentExtractionPostProcessor` | Deterministic normalization (action names, params, etc.) | Processor |
| `IntentExtractionValidator` | Contract validation, issue code generation | Validator |
| `ActionHandlerRegistry` | Action metadata and handler lookup | Registry |
| `EnrichedPromptBuilder` | System prompt construction | Builder |
| `IntentExtractionJsonSupport` | JSON parsing, code fence stripping | Utility |

---

## Fallback Ladder Design

### Decision Flow

```
┌─────────────────┐
│  User Query     │
└────────┬────────┘
         ↓
┌────────────────────────────────────────────┐
│ 1. COMPOUND STRATEGY                       │
│    Single LLM call with full schema        │
└────────┬───────────────────────────────────┘
         │
         ├─→ Success? → Return (fast path ✓)
         │
         ↓ Structural Failure (parse/schema error)
┌────────────────────────────────────────────┐
│ 2. REPAIR STRATEGY                         │
│    Fix JSON structure (≤ repairMaxAttempts)│
└────────┬───────────────────────────────────┘
         │
         ├─→ Success? → Return
         │
         ↓ Contract-Incomplete (missing fields)
┌────────────────────────────────────────────┐
│ 3. COMPLETION STRATEGY                     │
│    Fill required fields                    │
│    (≤ completionMaxAttempts)               │
└────────┬───────────────────────────────────┘
         │
         ├─→ Success? → Return
         │
         ↓ Still Failing + Budget Allows (≥3 calls)
┌────────────────────────────────────────────┐
│ 4. MULTI-STEP STRATEGY                     │
│    Decompose: classify → select → fill     │
│    (uses up to 3 LLM calls)                │
└────────┬───────────────────────────────────┘
         │
         ├─→ Success? → Return
         │
         ↓ Exhausted
┌────────────────────────────────────────────┐
│ FALLBACK: OUT_OF_SCOPE                     │
│    Safe default with diagnostic metadata   │
└────────────────────────────────────────────┘
```

### Budget Enforcement

```java
// From ProgressiveIntentExtractionEngine.java

int totalLlmCalls = 0;
int maxCalls = properties.getMaxTotalLlmCalls(); // default: 5

// Each strategy increments totalLlmCalls
totalLlmCalls += attempt.getLlmCalls();

// Multi-step requires ≥3 remaining calls
int remainingCalls = maxCalls - totalLlmCalls;
if (multiStepEnabled && remainingCalls >= 3) {
    // Attempt multi-step
}
```

**Why 3 calls for multi-step?**
Multi-step can use up to 3 calls:
1. Intent classification (ACTION vs INFORMATION vs OUT_OF_SCOPE)
2. Action selection (pick action + basic params)
3. Parameter filling (complete action parameters using metadata)

If budget < 3, skip multi-step to avoid partial decomposition.

---

## Extraction Strategies

### 1. Compound Strategy

**Purpose:** Fast-path extraction in a single LLM call

**Implementation:** `CompoundIntentExtractionStrategy.java`

**Flow:**
1. Build enriched system prompt with schema and constraints
2. Request `MultiIntentResponse` from LLM
3. Parse JSON response
4. Return parsed result (no validation yet)

**Prompt Characteristics:**
- Full JSON schema
- Allowed actions list
- Vector spaces list
- Relationship query examples
- JSON-only response parameters

**Success Criteria:**
- Valid JSON matching schema
- At least one intent
- All required fields present

**Example Prompt Snippet:**
```
You are an intent extraction system.
Output a JSON object matching this schema:
{
  "intents": [
    {
      "type": "ACTION|INFORMATION|OUT_OF_SCOPE",
      "intent": "...",
      "action": "...",  // if type=ACTION
      "actionParams": {...},
      ...
    }
  ],
  ...
}

ALLOWED ACTIONS (you MUST NOT invent actions):
- relationship_query (required params: query)
- clear_vector_index (required params: vectorSpace)
- ...
```

---

### 2. Repair Strategy

**Purpose:** Fix structural JSON/schema errors

**Implementation:** `RepairIntentExtractionStrategy.java`

**Flow:**
1. Take previous attempt's malformed output
2. Build repair prompt with:
   - Original query
   - Malformed JSON (bounded snippet)
   - Validation errors (schema violations)
3. Request corrected JSON
4. Parse and return

**Prompt Characteristics:**
- Minimal context (just schema + errors)
- Explicit "fix these errors" instruction
- JSON-only output
- No semantic re-interpretation (preserve user meaning)

**Success Criteria:**
- Valid JSON structure
- Matches schema
- No parse errors

**When Used:**
- `errorCategory == STRUCTURAL`
- Invalid JSON (missing braces, quotes, etc.)
- Schema mismatches (wrong field types)
- Truncated outputs

**Example Repair Prompt:**
```
REPAIR MODE:
The previous output had these errors:
- Missing closing brace at line 15
- Field "action" has type string but got null

PARTIAL OUTPUT:
{"intents": [{"type": "ACTION", "action": null, "intent": "search", ...

OUTPUT CORRECTED JSON ONLY.
```

---

### 3. Completion Strategy

**Purpose:** Fill missing required fields in contract-incomplete responses

**Implementation:** `CompletionIntentExtractionStrategy.java`

**Flow:**
1. Take previous attempt's parsed response (structurally valid but incomplete)
2. Extract validation issues (MISSING_REQUIRED_PARAM, etc.)
3. Build completion prompt with:
   - User query
   - Partial JSON
   - Validation issues
   - Allowed actions metadata (required params)
4. Request completed JSON
5. Parse and return

**Prompt Characteristics:**
- Action metadata with required parameters
- Validation issue codes
- "Complete, don't reinvent" instruction
- Guidance for safe fallback (OUT_OF_SCOPE) if cannot infer

**Success Criteria:**
- All required parameters present
- Action names from allowed list
- Contract-complete

**When Used:**
- `errorCategory == INCOMPLETE` (missing required fields)
- `errorCategory == UNSAFE` (unknown actions, invalid values)

**Special Rules:**
- For `relationship_query`: derive `actionParams.query` from user request, strip hint prefix
- Do NOT guess vectorSpace or routing values
- If cannot infer safely, fallback to OUT_OF_SCOPE with helpful `nextStepRecommended`

**Example Completion Prompt:**
```
COMPLETION MODE:
Fix ONLY the missing/invalid contract fields listed below.

ALLOWED ACTIONS (do NOT invent):
- relationship_query (required params: query) - Execute relationship query

VALIDATION ISSUES:
- MISSING_REQUIRED_PARAM field=actionParams.query intentIndex=0

USER REQUEST:
relationship_query: Find customer connections to orders

PARTIAL JSON:
{
  "intents": [{
    "type": "ACTION",
    "action": "relationship_query",
    "actionParams": {}
  }]
}

OUTPUT CORRECTED JSON.
```

---

### 4. Multi-Step Strategy

**Purpose:** Decompose complex queries into small, validated prompts

**Implementation:** `MultiStepIntentExtractionStrategy.java`

**Flow:**
1. **Step 1 — Classify** (1 LLM call)
   - Determine: ACTION, INFORMATION, or OUT_OF_SCOPE
   - Determine: requires retrieval? requires generation?

2. **Step 2 — Select** (1 LLM call)
   - If ACTION: pick action from allowed list
   - If INFORMATION: specify vectorSpace, semanticQuery

3. **Step 3 — Fill Params** (1 LLM call, conditional)
   - If ACTION selected: complete `actionParams` using action metadata
   - Use action's parameter spec (names, descriptions, required flags)

**Prompt Characteristics:**
- Small, focused schema per step
- Hard constraints (must pick from list)
- Step-level validation before proceeding

**Success Criteria:**
- Each step produces valid output
- Final result is contract-complete

**When Used:**
- All other strategies failed
- Budget allows (≥3 remaining calls)
- Complex multi-part queries

**Example Multi-Step Prompts:**

**Step 1 — Classify:**
```json
{
  "primaryType": "ACTION",
  "requiresRetrieval": false,
  "requiresGeneration": false
}
```

**Step 2 — Select Action:**
```json
{
  "action": "relationship_query",
  "confidence": 0.9
}
```

**Step 3 — Fill Params:**
```json
{
  "actionParams": {
    "query": "Find customer connections to orders"
  }
}
```

---

## Validation & Error Categorization

### Validation Architecture

`IntentExtractionValidator` performs contract validation and generates structured issues.

**Validation Flow:**
```
MultiIntentResponse
    ↓
┌─────────────────────────┐
│ Structural Validation   │ ← Basic schema checks
│ - Has intents?          │
│ - Valid intent types?   │
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ Action Validation       │ ← Action-specific checks
│ - Action registered?    │
│ - Required params?      │
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ Safety Validation       │ ← Security/safety checks
│ - Unknown actions?      │
│ - Invalid values?       │
└─────────────────────────┘
    ↓
ValidationResult (valid, errorCategory, issues, errors, warnings)
```

### Validation Model

```java
public record ValidationResult(
    boolean valid,
    ErrorCategory errorCategory,
    List<String> errors,
    List<String> warnings,
    List<ValidationIssue> issues
) {}

public record ValidationIssue(
    IssueCode code,
    Severity severity,
    String field,
    int intentIndex,
    String message
) {}
```

### Error Categories

| Category | Meaning | Typical Causes | Next Strategy |
|----------|---------|----------------|---------------|
| **STRUCTURAL** | JSON/schema errors | Parse failures, type mismatches, missing braces | Repair |
| **INCOMPLETE** | Missing required fields | Absent action params, missing vectorSpace | Completion |
| **UNSAFE** | Unknown/invalid values | Unregistered actions, hallucinated vector spaces | Completion |
| **OTHER** | Misc validation errors | Post-processing failures, unexpected errors | Next strategy |

### Issue Codes

Common `IssueCode` enum values:

```java
public enum IssueCode {
    // Structural
    EMPTY_INTENTS,
    INVALID_INTENT_TYPE,
    PARSE_ERROR,

    // Incomplete
    MISSING_REQUIRED_PARAM,
    MISSING_ACTION,
    MISSING_VECTOR_SPACE,

    // Unsafe
    UNKNOWN_ACTION,
    UNREGISTERED_ACTION,
    INVALID_ACTION_PARAMS,

    // Other
    POST_PROCESSING_FAILED,
    VALIDATION_EXCEPTION
}
```

### Severity Levels

```java
public enum Severity {
    ERROR,   // Blocks success
    WARNING  // Logged but doesn't block
}
```

---

## Post-Processing Pipeline

### Purpose

Apply **deterministic normalization** (no LLM calls) to fix common provider variations.

### Processing Steps

```java
// From IntentExtractionPostProcessor.java

public MultiIntentResponse postProcess(MultiIntentResponse response, String originalQuery) {
    // 1. Canonicalize action names (e.g., "relationship-query" → "relationship_query")
    canonicalizeActionNames(response);

    // 2. Handle OUT_OF_SCOPE without explicit intent name
    normalizeOutOfScopeIntents(response);

    // 3. Validate and normalize relationship query params
    normalizeRelationshipQueryParams(response, originalQuery);

    // 4. Strip hint prefixes (e.g., "relationship_query: ..." → "...")
    stripHintPrefixes(response);

    // 5. Attach normalization metadata
    attachNormalizationMetadata(response);

    return response;
}
```

### Normalization Rules

#### 1. Action Name Canonicalization

**Problem:** Providers may use different casing/separators

**Rule:** Normalize to registry canonical name

```java
// Before
"action": "relationship-query"  // or "relationshipQuery"

// After
"action": "relationship_query"
```

**Implementation:**
```java
String canonical = actionHandlerRegistry.resolveActionName(rawActionName);
intent.setAction(canonical);
```

#### 2. OUT_OF_SCOPE Normalization

**Problem:** Providers may omit intent name for OUT_OF_SCOPE

**Rule:** Set intent name to "out_of_scope" if missing

```java
if (intent.getType() == IntentType.OUT_OF_SCOPE && !hasText(intent.getIntent())) {
    intent.setIntent("out_of_scope");
}
```

#### 3. Relationship Query Parameter Normalization

**Problem:** Users may prefix query with "relationship_query:"

**Rule:** Strip hint prefix from actionParams.query

```java
String query = actionParams.get("query");
if (query.startsWith("relationship_query:")) {
    query = query.substring("relationship_query:".length()).trim();
    actionParams.put("query", query);
}
```

#### 4. Generation Instructions Normalization

**Problem:** Providers encode post-processing inconsistently

**Rule:** If `requiresGeneration=true`, ensure `generationInstructions` present (not `nextStepRecommended`)

```java
if (intent.isRequiresGeneration() && !hasText(intent.getGenerationInstructions())) {
    // Move from nextStepRecommended if present
    if (hasText(intent.getNextStepRecommended())) {
        intent.setGenerationInstructions(intent.getNextStepRecommended());
    }
}
```

### Normalization Metadata

Post-processor attaches metadata showing which rules were applied:

```json
{
  "intents": [...],
  "metadata": {
    "normalization": {
      "appliedRules": [
        "ACTION_NAME_CANONICALIZED",
        "RELATIONSHIP_QUERY_HINT_STRIPPED"
      ],
      "ruleCount": 2
    }
  }
}
```

This metadata:
- Aids debugging (shows what was normalized)
- Included in extraction diagnostics
- Available in debug snapshots

---

## Diagnostics & Observability

### Diagnostics Structure

```java
public record ExtractionOutput(
    MultiIntentResponse response,
    Map<String, Object> diagnostics
) {}
```

### Diagnostics Schema

```json
{
  "extractionPath": "completion",        // Final successful strategy
  "extractionAttempts": 2,               // Total attempts
  "llmCalls": 2,                         // Total LLM calls
  "attempts": [
    {
      "strategy": "compound",
      "success": false,
      "llmCalls": 1,
      "errorCategory": "INCOMPLETE",
      "issueCodes": ["MISSING_REQUIRED_PARAM"],
      "errors": ["Intent 0: Missing required parameter 'query'"],
      "normalization": {
        "appliedRules": ["ACTION_NAME_CANONICALIZED"],
        "ruleCount": 1
      }
    },
    {
      "strategy": "completion",
      "success": true,
      "llmCalls": 1,
      "normalization": {
        "appliedRules": ["RELATIONSHIP_QUERY_HINT_STRIPPED"],
        "ruleCount": 1
      }
    }
  ]
}
```

### Diagnostic Fields

| Field | Type | Description |
|-------|------|-------------|
| `extractionPath` | string | Final path: `compound`, `repair`, `completion`, `multi_step`, `fallback` |
| `extractionAttempts` | int | Total attempts made |
| `llmCalls` | int | Total LLM API calls |
| `attempts` | array | Per-attempt details |
| `attempts[].strategy` | string | Strategy name |
| `attempts[].success` | boolean | Success flag |
| `attempts[].llmCalls` | int | Calls for this attempt |
| `attempts[].errorCategory` | string | Error category if failed |
| `attempts[].issueCodes` | array | Validation issue codes |
| `attempts[].errors` | array | Error messages |
| `attempts[].warnings` | array | Warning messages |
| `attempts[].normalization` | object | Normalization metadata |

### Integration with Debug Snapshots

When `ai.orchestration.result-normalization.debugSnapshotEnabled=true`:

```java
// Snapshots include extraction diagnostics
OrchestrationResultDebugSnapshot snapshot = OrchestrationResultDebugSnapshot.builder()
    .resultType(result.getType())
    .success(result.isSuccess())
    .errorCode(result.getErrorCode())
    .extractionDiagnostics(diagnostics)  // ← Diagnostics included
    .build();
```

**Use Case:** Provider matrix tests can assert on diagnostics to track extraction quality.

---

## Configuration & Properties

### Property Hierarchy

```yaml
ai:
  intent-extraction:
    progressive:
      enabled: true                    # Master toggle
      repairEnabled: true              # Enable repair step
      repairMaxAttempts: 1             # Repair attempt limit (0-3)
      completionEnabled: true          # Enable completion step
      completionMaxAttempts: 1         # Completion attempt limit (0-3)
      multiStepEnabled: true           # Enable multi-step fallback
      maxTotalLlmCalls: 5              # Global budget (1-10)
      forceMode: auto                  # Force mode for debugging
```

### Property Validation

Properties are validated at startup via `@Validated`:

```java
@Data
@Validated
@ConfigurationProperties(prefix = "ai.intent-extraction.progressive")
public class ProgressiveIntentExtractionProperties {

    @Min(0) @Max(3)
    private int repairMaxAttempts = 1;

    @Min(0) @Max(3)
    private int completionMaxAttempts = 1;

    @Min(1) @Max(10)
    private int maxTotalLlmCalls = 5;

    // ...
}
```

### Conditional Bean Loading

`ProgressiveIntentExtractionEngine` only loads when enabled:

```java
@Component
@ConditionalOnProperty(
    prefix = "ai.intent-extraction.progressive",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true  // Default: enabled
)
public class ProgressiveIntentExtractionEngine {
    // ...
}
```

### Force Mode (Debugging)

`forceMode` allows testing specific strategies:

| Value | Behavior |
|-------|----------|
| `""` or `auto` | Normal ladder |
| `compound` | Only compound, fail if unsuccessful |
| `repair` | Skip compound, try repair immediately |
| `completion` | Skip compound/repair, try completion |
| `multi_step` | Skip all, go straight to multi-step |

**Example:**
```yaml
ai:
  intent-extraction:
    progressive:
      forceMode: completion  # Test completion in isolation
```

---

## Extension Points

### Adding a New Strategy

**Steps:**

1. **Create Strategy Class:**
```java
@Component
@RequiredArgsConstructor
public class MyCustomStrategy {

    public ExtractionAttempt attemptExtract(String query, OrchestrationContext ctx) {
        // 1. Build prompt
        // 2. Call LLM
        // 3. Parse response
        // 4. Return ExtractionAttempt
    }

    public String getStrategyName() {
        return "my_custom";
    }
}
```

2. **Inject into Engine:**
```java
@Component
public class ProgressiveIntentExtractionEngine {

    private final MyCustomStrategy customStrategy;

    public ExtractionOutput extract(String query, OrchestrationContext context) {
        // ... existing ladder ...

        // Add custom strategy to ladder
        if (customEnabled && totalLlmCalls < maxCalls) {
            ExtractionAttempt attempt = customStrategy.attemptExtract(query, context);
            totalLlmCalls += attempt.getLlmCalls();
            // ... assess and return ...
        }
    }
}
```

3. **Add Configuration:**
```java
@ConfigurationProperties(prefix = "ai.intent-extraction.progressive")
public class ProgressiveIntentExtractionProperties {
    private boolean customEnabled = false;
}
```

### Adding New Validation Rules

**Steps:**

1. **Add Issue Code:**
```java
public enum IssueCode {
    // ... existing codes ...
    MY_CUSTOM_VALIDATION,
}
```

2. **Implement Validation:**
```java
@Component
public class IntentExtractionValidator {

    public ValidationResult validate(MultiIntentResponse response, String query) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Custom validation
        if (myCustomCheck(response)) {
            issues.add(new ValidationIssue(
                IssueCode.MY_CUSTOM_VALIDATION,
                Severity.ERROR,
                "fieldName",
                intentIndex,
                "Custom validation failed"
            ));
        }

        // ...
    }
}
```

3. **Map to Error Category:**
```java
ErrorCategory category = categorize(issues);
```

### Adding New Post-Processing Rules

**Steps:**

1. **Implement Rule:**
```java
@Component
public class IntentExtractionPostProcessor {

    public MultiIntentResponse postProcess(MultiIntentResponse response, String query) {
        List<String> appliedRules = new ArrayList<>();

        // Custom normalization
        if (needsCustomNormalization(response)) {
            applyCustomNormalization(response);
            appliedRules.add("MY_CUSTOM_NORMALIZATION");
        }

        // Attach metadata
        attachNormalizationMetadata(response, appliedRules);
        return response;
    }
}
```

2. **Test Determinism:**
```java
@Test
void testCustomNormalization() {
    // Ensure rule is deterministic (same input → same output)
    MultiIntentResponse input = buildTestInput();
    MultiIntentResponse output1 = postProcessor.postProcess(input, query);
    MultiIntentResponse output2 = postProcessor.postProcess(input, query);

    assertEquals(output1, output2);  // Must be deterministic
}
```

---

## Testing Strategy

### Unit Tests

**Focus:** Isolated strategy behavior

**Example:**
```java
@Test
void testCompletionStrategy_fillsMissingParams() {
    // Given: incomplete response missing actionParams.query
    ExtractionAttempt incomplete = buildIncompleteAttempt();

    // When: completion strategy invoked
    ExtractionAttempt result = completionStrategy.attemptComplete(query, context, incomplete);

    // Then: actionParams.query is filled
    assertTrue(result.isSuccess());
    assertNotNull(result.getResponse().getIntents().get(0).getActionParams().get("query"));
}
```

### Integration Tests

**Focus:** End-to-end ladder flow with mocked provider

**Example:**
```java
@Test
void testProgressiveLadder_compoundFails_repairSucceeds() {
    // Given: provider returns malformed JSON on first call, valid on second
    mockProvider
        .whenCompound().thenReturn("{malformed")  // Missing closing brace
        .whenRepair().thenReturn("{\"intents\":[...]}");  // Valid

    // When: engine extracts
    ExtractionOutput output = engine.extract(query, context);

    // Then: repair path used
    assertEquals("repair", output.diagnostics().get("extractionPath"));
    assertEquals(2, output.diagnostics().get("llmCalls"));
    assertTrue(output.response().hasIntents());
}
```

### Real API Tests

**Focus:** Provider matrix testing across real providers

**Example:**
```java
@Test
@ParameterizedTest
@ProviderMatrix  // Runs against OpenAI, Anthropic, Cohere, etc.
void testProgressiveExtraction_relationshipQuery(TestProvider provider) {
    // Given: relationship query
    String query = "relationship_query: Find customer orders";

    // When: extract with progressive engine
    ExtractionOutput output = engine.extract(query, context);

    // Then: canonical result (provider-agnostic)
    assertEquals(IntentType.ACTION, output.response().getIntents().get(0).getType());
    assertEquals("relationship_query", output.response().getIntents().get(0).getAction());
    assertNotNull(output.response().getIntents().get(0).getActionParams().get("query"));

    // And: diagnostics available
    assertNotNull(output.diagnostics().get("extractionPath"));
    assertTrue((Integer) output.diagnostics().get("llmCalls") <= 5);
}
```

### Test Invariants

**Must enforce:**
1. Total LLM calls never exceed `maxTotalLlmCalls`
2. Diagnostics always present and non-null
3. Final response matches canonical contract (even after fallback)
4. Post-processing is deterministic (same input → same output)
5. No provider-specific assertions (test contract, not LLM prose)

---

## Design Decisions (ADR-style)

### Decision 1: Post-Process Before Validate

**Context:** Should validation run before or after post-processing?

**Decision:** Post-process first, then validate

**Rationale:**
- Deterministic normalization can fix many issues without LLM calls
- Reduces unnecessary repair/completion attempts
- Faster and cheaper for common provider variations

**Consequences:**
- Post-processor must guarantee no LLM calls (deterministic only)
- Validation receives normalized input
- Diagnostics include post-processing metadata

**Implementation:**
```java
ExtractionAttempt assessAttempt(ExtractionAttempt attempt, String query) {
    // 1. Post-process (deterministic)
    MultiIntentResponse processed = postProcessor.postProcess(attempt.getResponse(), query);

    // 2. Validate (contract checks)
    ValidationResult validation = validator.validate(processed, query);

    // 3. Return assessed attempt
    return ExtractionAttempt.builder()
        .response(processed)
        .validationResult(validation)
        .success(validation.valid())
        .build();
}
```

---

### Decision 2: Separate Completion from Repair

**Context:** Should completion be part of repair or a separate strategy?

**Decision:** Separate strategy

**Rationale:**
- **Repair** fixes structural errors (JSON/schema)
- **Completion** fills missing required fields (contract-incomplete)
- Different prompts, different error categories
- Allows independent tuning (repairMaxAttempts vs completionMaxAttempts)

**Consequences:**
- Additional strategy in ladder
- More configuration options
- Clearer diagnostics (repair vs completion)

**Alternative Considered:** Combined "repair" for all fixes
- **Rejected:** Conflates structural and semantic fixes, harder to tune

---

### Decision 3: Multi-Step Requires ≥3 Remaining Calls

**Context:** How many calls should multi-step reserve?

**Decision:** Multi-step only runs if `remainingCalls >= 3`

**Rationale:**
- Multi-step uses 1-3 calls (classify, select, fill params)
- Partial decomposition worse than failing fast
- Prevents budget exhaustion mid-decomposition

**Consequences:**
- If budget tight (e.g., `maxTotalLlmCalls=3` after compound/repair), skip multi-step
- Clear budget enforcement

**Implementation:**
```java
int remainingCalls = maxCalls - totalLlmCalls;
if (multiStepEnabled && remainingCalls >= 3) {
    // Attempt multi-step
}
```

**Alternative Considered:** Dynamic call count based on remaining budget
- **Rejected:** Too complex, unclear success criteria

---

### Decision 4: LLM-Driven Semantics, No String Parsing

**Context:** Should we use regex/substring parsing to interpret user queries?

**Decision:** No substring parsing; LLM decides semantics, code enforces contracts

**Rationale:**
- String parsing is brittle (language-specific, ambiguous)
- LLM better at semantic interpretation
- Code enforces structural contracts (schemas, registries)

**Consequences:**
- All semantic decisions delegated to LLM
- Code only validates structure/safety
- No "if query contains 'then'" heuristics

**Example:**
```java
// ❌ NOT ALLOWED
if (query.contains("then")) {
    // Split on "then" and create compound intent
}

// ✅ ALLOWED
if (actionHandlerRegistry.isRegistered(actionName)) {
    // Action is safe, proceed
}
```

---

### Decision 5: Bounded Cost via maxTotalLlmCalls

**Context:** How to prevent unbounded retry loops?

**Decision:** Hard limit on total LLM calls per request

**Rationale:**
- Cost control (predictable budget)
- Latency control (bounded time)
- Prevents runaway extraction

**Consequences:**
- Complex queries may fall back if budget tight
- Tunable per environment (prod vs dev)

**Configuration:**
```yaml
ai:
  intent-extraction:
    progressive:
      maxTotalLlmCalls: 5  # Total budget per request
```

**Default Value:** 5 calls
- Compound: 1
- Repair: 1-2
- Completion: 1-2
- Multi-step: 1-3

---

### Decision 6: Diagnostics Always Included

**Context:** Should diagnostics be optional?

**Decision:** Always include diagnostics in `ExtractionOutput`

**Rationale:**
- Essential for debugging and monitoring
- Minimal overhead (small JSON object)
- Enables provider matrix scorecards

**Consequences:**
- Every extraction includes diagnostics
- Consumers can ignore if not needed
- Debug snapshots can include extraction metadata

---

### Decision 7: Validation Issues Use Typed Enums

**Context:** How to represent validation errors?

**Decision:** Typed `IssueCode` enum + `Severity` + structured `ValidationIssue`

**Rationale:**
- Enables programmatic handling (not string matching)
- Easier to analyze patterns across providers
- Clear severity levels (ERROR vs WARNING)

**Model:**
```java
public record ValidationIssue(
    IssueCode code,        // Typed enum
    Severity severity,     // ERROR or WARNING
    String field,          // Field path
    int intentIndex,       // Intent index
    String message         // Human-readable
) {}
```

**Alternative Considered:** Free-form error strings
- **Rejected:** No programmatic analysis, hard to categorize

---

## Performance Considerations

### Latency Optimization

1. **Fast-path Success:** Most requests should succeed on compound (1 call)
2. **Parallel Processing:** Strategies don't currently run in parallel (sequential ladder)
3. **Prompt Size:** Keep prompts minimal (especially repair/completion)
4. **Caching:** LLM provider caching applied at `AICoreService` level

### Cost Optimization

1. **Default Limits:** Conservative defaults (repairMaxAttempts=1, completionMaxAttempts=1)
2. **Budget Enforcement:** Hard limit prevents runaway costs
3. **Deterministic First:** Post-processing reduces LLM calls
4. **Separate Orchestration LLM:** Use cheaper model for extraction

**Example Cost Profile:**
- 80% compound success: 1 call each = 0.8 avg
- 15% repair success: 2 calls each = 0.3 avg
- 5% completion success: 3 calls each = 0.15 avg
- **Total avg:** ~1.25 calls per request

### Memory Optimization

1. **Bounded Diagnostics:** Only store per-attempt metadata (no full responses)
2. **Immutable Collections:** Use `List.copyOf()` to prevent accidental mutation
3. **Snapshot Limits:** Debug snapshots bounded to recent N entries

---

## Related Documentation

- **User Guide:** `PROGRESSIVE_INTENT_EXTRACTION_USER_GUIDE.md`
- **Normalization Guide:** `NORMALIZATION_AND_ORCHESTRATION_GUIDE.md`
- **Change Plans:** `changes/PROGRESSIVE_INTENT_EXTRACTION_RESILIENCE_ENGINE_CHANGE_PLAN.md`
- **Provider Testing:** `REALAPI_PROVIDER_MATRIX_TESTING_GUIDE.md`

---

## Summary

Progressive Intent Extraction provides a **resilient, provider-agnostic, bounded-cost** system for extracting structured intents from user queries. Key architectural principles:

1. **Ladder-based fallback** (compound → repair → completion → multi-step)
2. **LLM-driven semantics, code-driven contracts**
3. **Deterministic-first** (post-process before validate)
4. **Bounded cost** (maxTotalLlmCalls)
5. **Observable** (diagnostics + validation issues)

Extend via new strategies, validation rules, or post-processing rules while maintaining provider-agnostic design.
